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
import yhx.com.domain.agent.model.entity.modelruntime.AgentModelProfileEntity;
import yhx.com.domain.agent.model.entity.modelruntime.AgentNodeModelBindingEntity;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;

@Slf4j
@Configuration
@EnableConfigurationProperties(AutoAgentRagProperties.class)
@ConditionalOnProperty(prefix = "auto-agent.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AutoAgentRagVectorConfig {

    @Bean("pgVectorStore")
    @Primary
    public PgVectorStore pgVectorStore(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
                                       AutoAgentRagProperties properties,
                                       EmbeddingRuntimeSettings embeddingRuntimeSettings,
                                       @Qualifier("autoAgentEmbeddingModel") EmbeddingModel embeddingModel) {
        PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(properties.getVectorName())
                .dimensions(embeddingRuntimeSettings.dimensions())
                .initializeSchema(true)
                .build();
        try {
            store.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("PgVectorStore initialization failed. Check pgvector extension, table permissions, and embedding configuration.", e);
        }
        log.info("PgVectorStore initialized: model={}, dimensions={}, table={}",
                embeddingRuntimeSettings.modelName(),
                embeddingRuntimeSettings.dimensions(),
                properties.getVectorName());
        return store;
    }

    @Bean("autoAgentEmbeddingRuntimeSettings")
    public EmbeddingRuntimeSettings autoAgentEmbeddingRuntimeSettings(IModelRuntimeRepository modelRuntimeRepository) {
        AgentNodeModelBindingEntity binding = modelRuntimeRepository.findActiveBindingByNodeCode(AgentComponentCodeEnumVO.VECTOR_EMBEDDING.name())
                .orElseThrow(() -> new IllegalStateException("RAG vector store initialization failed: missing active node model binding: "
                        + AgentComponentCodeEnumVO.VECTOR_EMBEDDING.name()));
        AgentModelProfileEntity profile = modelRuntimeRepository.findActiveModelProfile(binding.getModelProfileId())
                .orElseThrow(() -> new IllegalStateException("RAG vector store initialization failed: active model profile not found: "
                        + binding.getModelProfileId()));
        if (!"EMBEDDING".equalsIgnoreCase(profile.getModelType())) {
            throw new IllegalStateException("RAG vector store initialization failed: model profile "
                    + profile.getModelProfileId() + " must use model_type=EMBEDDING.");
        }
        if (!StringUtils.hasText(profile.getModelName())) {
            throw new IllegalStateException("RAG vector store initialization failed: embedding model_name is required.");
        }
        if (profile.getEmbeddingDimensions() == null || profile.getEmbeddingDimensions() <= 0) {
            throw new IllegalStateException("RAG vector store initialization failed: embedding_dimensions is required for profile "
                    + profile.getModelProfileId());
        }
        AgentModelApiEntity api = modelRuntimeRepository.findActiveApi(profile.getApiId())
                .orElseThrow(() -> new IllegalStateException("RAG vector store initialization failed: active api not found for api_id="
                        + profile.getApiId()));
        return new EmbeddingRuntimeSettings(profile, api);
    }

    @Bean("autoAgentEmbeddingModel")
    public EmbeddingModel autoAgentEmbeddingModel(EmbeddingRuntimeSettings embeddingRuntimeSettings) {
        AgentModelApiEntity api = embeddingRuntimeSettings.api();
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(api.getBaseUrl())
                .apiKey(api.getApiKey())
                .completionsPath(api.getCompletionsPath())
                .embeddingsPath(api.getEmbeddingsPath())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingRuntimeSettings.modelName())
                .dimensions(embeddingRuntimeSettings.dimensions())
                .build();
        log.info("AutoAgent embedding model initialized: profileId={}, baseUrl={}, embeddingsPath={}, model={}, dimensions={}",
                embeddingRuntimeSettings.profile().getModelProfileId(),
                api.getBaseUrl(),
                api.getEmbeddingsPath(),
                embeddingRuntimeSettings.modelName(),
                embeddingRuntimeSettings.dimensions());
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }

    public record EmbeddingRuntimeSettings(AgentModelProfileEntity profile, AgentModelApiEntity api) {

        public String modelName() {
            return profile.getModelName();
        }

        public int dimensions() {
            return profile.getEmbeddingDimensions();
        }
    }
}
