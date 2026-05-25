package yhx.com.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import yhx.com.domain.agent.adapter.repository.IModelRuntimeRepository;
import yhx.com.domain.agent.model.entity.modelruntime.AgentModelApiEntity;

@Slf4j
@Configuration
@EnableConfigurationProperties(AutoAgentRagProperties.class)
@ConditionalOnProperty(prefix = "auto-agent.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AutoAgentRagVectorConfig {

    @Bean("pgVectorStore")
    @Primary
    public PgVectorStore pgVectorStore(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
                                       AutoAgentRagProperties properties,
                                       @Qualifier("autoAgentEmbeddingModel") EmbeddingModel embeddingModel) {
        PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(properties.getVectorName())
                .dimensions(properties.getDimensions())
                .initializeSchema(true)
                .build();
        try {
            store.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("PgVectorStore initialization failed. Check pgvector extension, table permissions, and embedding configuration.", e);
        }
        log.info("PgVectorStore initialized: model={}, dimensions={}, table={}",
                properties.getEmbeddingModelName(),
                properties.getDimensions(),
                properties.getVectorName());
        return store;
    }

    @Bean("autoAgentEmbeddingModel")
    public EmbeddingModel autoAgentEmbeddingModel(AutoAgentRagProperties properties,
                                                  IModelRuntimeRepository modelRuntimeRepository) {
        if (!StringUtils.hasText(properties.getEmbeddingApiId())) {
            throw new IllegalStateException("RAG vector store initialization failed: auto-agent.rag.embedding-api-id is required.");
        }
        AgentModelApiEntity api = modelRuntimeRepository.findActiveApi(properties.getEmbeddingApiId())
                .orElseThrow(() -> new IllegalStateException("RAG vector store initialization failed: active api not found for embedding-api-id="
                        + properties.getEmbeddingApiId()));
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(api.getBaseUrl())
                .apiKey(api.getApiKey())
                .completionsPath(api.getCompletionsPath())
                .embeddingsPath(api.getEmbeddingsPath())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.getEmbeddingModelName())
                .dimensions(properties.getDimensions())
                .build();
        log.info("AutoAgent embedding model initialized: embeddingApiId={}, baseUrl={}, embeddingsPath={}, model={}, dimensions={}",
                properties.getEmbeddingApiId(),
                api.getBaseUrl(),
                api.getEmbeddingsPath(),
                properties.getEmbeddingModelName(),
                properties.getDimensions());
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}
