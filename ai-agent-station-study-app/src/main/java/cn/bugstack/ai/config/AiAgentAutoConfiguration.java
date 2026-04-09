package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.model.entity.ArmoryCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientApiVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.ai.types.common.Constants;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI Agent 自动装配与向量库装配配置。
 * 说明：
 * 1. 客户端装配在 ApplicationReady 阶段执行；
 * 2. pgVectorStore 改为启动期 @Bean 创建，避免 RAG 侧拿到默认自动配置的 VectorStore。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiAgentAutoConfigProperties.class)
@ConditionalOnProperty(prefix = "spring.ai.agent.auto-config", name = "enabled", havingValue = "true")
public class AiAgentAutoConfiguration implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Resource
    private DefaultArmoryStrategyFactory defaultArmoryStrategyFactory;

    /**
     * 显式注册 pgVectorStore，并作为主 VectorStore。
     * 这样 RAG 上传与 Advisor 检索会统一使用 DB 中 ragApiId 指定的 API 凭证。
     */
    @Bean("pgVectorStore")
    @Primary
    public PgVectorStore pgVectorStore(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        String ragApiId = aiAgentAutoConfigProperties.getRagApiId();
        if (ragApiId == null || ragApiId.isBlank()) {
            throw new IllegalStateException("RAG 初始化失败：未配置 spring.ai.agent.auto-config.rag-api-id");
        }

        List<AiClientApiVO> apiList = loadAiClientApiFromArmory();
        AiClientApiVO ragApi = apiList.stream()
                .filter(api -> ragApiId.equals(api.getApiId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("RAG 初始化失败：未找到 ragApiId 对应的 API 配置，ragApiId=" + ragApiId));

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(ragApi.getBaseUrl())
                .apiKey(ragApi.getApiKey())
                .completionsPath(ragApi.getCompletionsPath())
                .embeddingsPath(ragApi.getEmbeddingsPath())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(aiAgentAutoConfigProperties.getEmbeddingModelName())
                .dimensions(aiAgentAutoConfigProperties.getDimensions())
                .build();

        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);

        PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(aiAgentAutoConfigProperties.getVector_name())
                .dimensions(aiAgentAutoConfigProperties.getDimensions())
                .initializeSchema(true)
                .build();

        try {
            store.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("PgVectorStore 初始化失败，请检查 pgvector 扩展、表权限、embedding 配置", e);
        }

        log.info("PgVectorStore 已初始化: ragApiId={}, baseUrl={}, embeddingsPath={}, model={}, dimensions={}, table={}",
                ragApiId,
                ragApi.getBaseUrl(),
                ragApi.getEmbeddingsPath(),
                aiAgentAutoConfigProperties.getEmbeddingModelName(),
                aiAgentAutoConfigProperties.getDimensions(),
                aiAgentAutoConfigProperties.getVector_name());

        return store;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            log.info("AI Agent 自动装配开始，配置: {}", aiAgentAutoConfigProperties);

            List<String> commandIdList = parseClientIds(aiAgentAutoConfigProperties.getClientIds());
            if (CollectionUtils.isEmpty(commandIdList)) {
                log.warn("AI Agent 自动装配跳过：client-ids 为空");
                return;
            }

            StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> armoryStrategyHandler =
                    defaultArmoryStrategyFactory.armoryStrategyHandler();

            String result = armoryStrategyHandler.apply(
                    ArmoryCommandEntity.builder()
                            .commandType(AiAgentEnumVO.AI_CLIENT.getCode())
                            .commandIdList(commandIdList)
                            .build(),
                    new DefaultArmoryStrategyFactory.DynamicContext());

            log.info("AI Agent 自动装配完成，结果: {}", result);

            ApplicationContext applicationContext = event.getApplicationContext();
            Map<String, VectorStore> vectorStoreMap = applicationContext.getBeansOfType(VectorStore.class);
            log.info("VectorStore Bean 列表: {}", vectorStoreMap.keySet());
        } catch (Exception e) {
            log.error("AI Agent 自动装配失败", e);
        }
    }

    private List<AiClientApiVO> loadAiClientApiFromArmory() {
        List<String> commandIdList = parseClientIds(aiAgentAutoConfigProperties.getClientIds());
        if (CollectionUtils.isEmpty(commandIdList)) {
            throw new IllegalStateException("RAG 初始化失败：client-ids 为空，无法加载 API 配置");
        }

        DefaultArmoryStrategyFactory.DynamicContext dynamicContext = new DefaultArmoryStrategyFactory.DynamicContext();
        StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> armoryStrategyHandler =
                defaultArmoryStrategyFactory.armoryStrategyHandler();

        try {
            armoryStrategyHandler.apply(
                    ArmoryCommandEntity.builder()
                            .commandType(AiAgentEnumVO.AI_CLIENT.getCode())
                            .commandIdList(commandIdList)
                            .build(),
                    dynamicContext);
        } catch (Exception e) {
            throw new IllegalStateException("RAG 初始化失败：装配链路执行异常", e);
        }

        List<AiClientApiVO> apiList = dynamicContext.getValue(AiAgentEnumVO.AI_CLIENT_API.getDataName());
        if (CollectionUtils.isEmpty(apiList)) {
            throw new IllegalStateException("RAG 初始化失败：未从 DynamicContext 加载到 ai_client_api");
        }
        return apiList;
    }

    private List<String> parseClientIds(List<String> clientIds) {
        if (CollectionUtils.isEmpty(clientIds)) {
            return List.of();
        }
        if (clientIds.size() == 1 && clientIds.get(0).contains(Constants.SPLIT)) {
            return Arrays.stream(clientIds.get(0).split(Constants.SPLIT))
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .collect(Collectors.toList());
        }
        return clientIds;
    }
}
