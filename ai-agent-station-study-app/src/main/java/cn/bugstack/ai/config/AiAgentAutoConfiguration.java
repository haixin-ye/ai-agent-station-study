package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.model.entity.ArmoryCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientApiVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.ai.types.common.Constants;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI Agent 自动装配配置类
 * 在Spring Boot应用启动完成后，根据配置自动装配AI客户端和VectorStore
 *
 * @author yhx
 * 2025/1/15 10:00
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


    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        DefaultArmoryStrategyFactory.DynamicContext dynamicContext =
                new DefaultArmoryStrategyFactory.DynamicContext();
        try {
            log.info("AI Agent 自动装配开始，配置: {}", aiAgentAutoConfigProperties);

            if (!aiAgentAutoConfigProperties.isEnabled()) {
                log.info("AI Agent 自动装配未启用");
                return;
            }

            List<String> clientIds = aiAgentAutoConfigProperties.getClientIds();
            if (CollectionUtils.isEmpty(clientIds)) {
                log.warn("AI Agent 自动装配配置的客户端ID列表为空");
                return;
            }

            // 解析客户端ID列表（支持逗号分隔的字符串）
            List<String> commandIdList;
            if (clientIds.size() == 1 && clientIds.get(0).contains(Constants.SPLIT)) {
                commandIdList = Arrays.stream(clientIds.get(0).split(Constants.SPLIT))
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .collect(Collectors.toList());
            } else {
                commandIdList = clientIds;
            }

            log.info("开始自动装配AI客户端，客户端ID列表: {}", commandIdList);

            StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> armoryStrategyHandler =
                    defaultArmoryStrategyFactory.armoryStrategyHandler();

            String result = armoryStrategyHandler.apply(
                    ArmoryCommandEntity.builder()
                            .commandType(AiAgentEnumVO.AI_CLIENT.getCode())
                            .commandIdList(commandIdList)
                            .build(),
                    dynamicContext);

            log.info("AI Agent 自动装配完成，结果: {}", result);

        } catch (Exception e) {
            log.error("AI Agent 自动装配失败", e);
        }

        // VectorStore 装配：从 DynamicContext 中取已加载的 API 配置，按 ragApiId 匹配
        log.info("开始进行VectorStore的装配");
        try {
            String ragApiId = aiAgentAutoConfigProperties.getRagApiId();
            if (ragApiId == null || ragApiId.isEmpty()) {
                log.info("未配置 ragApiId，跳过VectorStore动态装配");
                return;
            }

            List<AiClientApiVO> apiList = dynamicContext.getValue(
                    AiAgentEnumVO.AI_CLIENT_API.getDataName());
            if (CollectionUtils.isEmpty(apiList)) {
                log.warn("DynamicContext 中无API配置数据，跳过VectorStore动态装配");
                return;
            }

            apiList.stream()
                    .filter(api -> ragApiId.equals(api.getApiId()))
                    .findFirst()
                    .ifPresent(api -> {
                        // 1. 构造 API 客户端
                        OpenAiApi openAiApi = OpenAiApi.builder()
                                .baseUrl(api.getBaseUrl())
                                .apiKey(api.getApiKey())
                                .build();

                        // 2. 从 aiAgentAutoConfigProperties 中获取模型名称并构造配置项
                        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                                .model(aiAgentAutoConfigProperties.getEmbeddingModelName()) // 从 YML 获取，如 text-embedding-3-small
                                .dimensions(aiAgentAutoConfigProperties.getDimensions())
                                .build();

                        // 3. 构造 EmbeddingModel（传入 options）
                        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);

                        JdbcTemplate jdbcTemplate = event.getApplicationContext()
                                .getBean("pgVectorJdbcTemplate", JdbcTemplate.class);

                        // 4. 构造 PgVectorStore，显式指定从 YML 获取的维度
                        PgVectorStore pgVectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                                .vectorTableName(aiAgentAutoConfigProperties.getVector_name())
                                .dimensions(aiAgentAutoConfigProperties.getDimensions()) // 从 YML 获取，如 1536
                                .initializeSchema(true) // 允许根据维度自动创建/校验表
                                .build();
                        //手动创建表，这里的 .initializeSchema(true) // 允许根据维度自动创建/校验表 由于是手动注册的bean，并不会运行
                        try {
                            pgVectorStore.afterPropertiesSet();
                        } catch (Exception e) {
                            log.error("❌ PgVectorStore 初始化失败（建表失败），请检查数据库权限或是否安装了 vector 扩展", e);
                        }

                        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) event.getApplicationContext();
                        ctx.getBeanFactory().registerSingleton("pgVectorStore", pgVectorStore);
                        log.info("VectorStore 装配完成，模型: {}, 维度: {}",
                                aiAgentAutoConfigProperties.getEmbeddingModelName(),
                                aiAgentAutoConfigProperties.getDimensions());
                    });

            log.info("注：如果没有打印——\"VectorStore装配完成\"，说明Client_API未匹配上");

        } catch (Exception e) {
            log.error("VectorStore动态装配失败", e);
        }
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

}
