package yhx.com.config;

import yhx.com.domain.agent.model.entity.armory.ArmoryCommandEntity;
import yhx.com.domain.agent.model.valobj.armory.AiClientApiVO;
import yhx.com.domain.agent.model.valobj.enums.armory.AiAgentEnumVO;
import yhx.com.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import yhx.com.types.common.Constants;
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
 * AI Agent 鑷姩瑁呴厤涓庡悜閲忓簱瑁呴厤閰嶇疆銆?
 * 璇存槑锛?
 * 1. 瀹㈡埛绔閰嶅湪 ApplicationReady 闃舵鎵ц锛?
 * 2. pgVectorStore 鏀逛负鍚姩鏈?@Bean 鍒涘缓锛岄伩鍏?RAG 渚ф嬁鍒伴粯璁よ嚜鍔ㄩ厤缃殑 VectorStore銆?
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
     * 鏄惧紡娉ㄥ唽 pgVectorStore锛屽苟浣滀负涓?VectorStore銆?
     * 杩欐牱 RAG 涓婁紶涓?Advisor 妫€绱細缁熶竴浣跨敤 DB 涓?ragApiId 鎸囧畾鐨?API 鍑瘉銆?
     */
    @Bean("pgVectorStore")
    @Primary
    public PgVectorStore pgVectorStore(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        String ragApiId = aiAgentAutoConfigProperties.getRagApiId();
        if (ragApiId == null || ragApiId.isBlank()) {
            throw new IllegalStateException("RAG 鍒濆鍖栧け璐ワ細鏈厤缃?spring.ai.agent.auto-config.rag-api-id");
        }

        List<AiClientApiVO> apiList = loadAiClientApiFromArmory();
        AiClientApiVO ragApi = apiList.stream()
                .filter(api -> ragApiId.equals(api.getApiId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("RAG 鍒濆鍖栧け璐ワ細鏈壘鍒?ragApiId 瀵瑰簲鐨?API 閰嶇疆锛宺agApiId=" + ragApiId));

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
            throw new IllegalStateException("PgVectorStore 鍒濆鍖栧け璐ワ紝璇锋鏌?pgvector 鎵╁睍銆佽〃鏉冮檺銆乪mbedding 閰嶇疆", e);
        }

        log.info("PgVectorStore 宸插垵濮嬪寲: ragApiId={}, baseUrl={}, embeddingsPath={}, model={}, dimensions={}, table={}",
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
            log.info("AI Agent 鑷姩瑁呴厤寮€濮嬶紝閰嶇疆: {}", aiAgentAutoConfigProperties);

            List<String> commandIdList = parseClientIds(aiAgentAutoConfigProperties.getClientIds());
            if (CollectionUtils.isEmpty(commandIdList)) {
                log.warn("AI Agent 鑷姩瑁呴厤璺宠繃锛歝lient-ids 涓虹┖");
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

            log.info("AI Agent 鑷姩瑁呴厤瀹屾垚锛岀粨鏋? {}", result);

            ApplicationContext applicationContext = event.getApplicationContext();
            Map<String, VectorStore> vectorStoreMap = applicationContext.getBeansOfType(VectorStore.class);
            log.info("VectorStore Bean 鍒楄〃: {}", vectorStoreMap.keySet());
        } catch (Exception e) {
            log.error("AI Agent 鑷姩瑁呴厤澶辫触", e);
        }
    }

    private List<AiClientApiVO> loadAiClientApiFromArmory() {
        List<String> commandIdList = parseClientIds(aiAgentAutoConfigProperties.getClientIds());
        if (CollectionUtils.isEmpty(commandIdList)) {
            throw new IllegalStateException("RAG 鍒濆鍖栧け璐ワ細client-ids 涓虹┖锛屾棤娉曞姞杞?API 閰嶇疆");
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
            throw new IllegalStateException("RAG 鍒濆鍖栧け璐ワ細瑁呴厤閾捐矾鎵ц寮傚父", e);
        }

        List<AiClientApiVO> apiList = dynamicContext.getValue(AiAgentEnumVO.AI_CLIENT_API.getDataName());
        if (CollectionUtils.isEmpty(apiList)) {
            throw new IllegalStateException("RAG 鍒濆鍖栧け璐ワ細鏈粠 DynamicContext 鍔犺浇鍒?ai_client_api");
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

