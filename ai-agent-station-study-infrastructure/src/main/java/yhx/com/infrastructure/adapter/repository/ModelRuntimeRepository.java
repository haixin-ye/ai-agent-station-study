package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IModelRuntimeRepository;
import yhx.com.domain.agent.model.entity.modelruntime.AgentModelApiEntity;
import yhx.com.domain.agent.model.entity.modelruntime.AgentModelProfileEntity;
import yhx.com.domain.agent.model.entity.modelruntime.AgentNodeModelBindingEntity;
import yhx.com.infrastructure.dao.IAgentModelApiDao;
import yhx.com.infrastructure.dao.IAgentModelProfileDao;
import yhx.com.infrastructure.dao.IAgentNodeModelBindingDao;
import yhx.com.infrastructure.dao.po.AgentModelApiPO;
import yhx.com.infrastructure.dao.po.AgentModelProfilePO;
import yhx.com.infrastructure.dao.po.AgentNodeModelBindingPO;

import java.util.List;
import java.util.Optional;

@Repository
public class ModelRuntimeRepository implements IModelRuntimeRepository {

    @Resource
    private IAgentModelApiDao modelApiDao;

    @Resource
    private IAgentModelProfileDao modelProfileDao;

    @Resource
    private IAgentNodeModelBindingDao nodeModelBindingDao;

    @Override
    public Optional<AgentNodeModelBindingEntity> findActiveBindingByNodeCode(String nodeCode) {
        return Optional.ofNullable(nodeModelBindingDao.queryActiveByNodeCode(nodeCode)).map(this::toBindingEntity);
    }

    @Override
    public Optional<AgentModelProfileEntity> findActiveModelProfile(String modelProfileId) {
        return Optional.ofNullable(modelProfileDao.queryActiveByProfileId(modelProfileId)).map(this::toProfileEntity);
    }

    @Override
    public Optional<AgentModelApiEntity> findActiveApi(String apiId) {
        return Optional.ofNullable(modelApiDao.queryActiveByApiId(apiId)).map(this::toApiEntity);
    }

    @Override
    public List<AgentNodeModelBindingEntity> listActiveBindings() {
        return nodeModelBindingDao.listActive().stream().map(this::toBindingEntity).toList();
    }

    private AgentModelApiEntity toApiEntity(AgentModelApiPO po) {
        return AgentModelApiEntity.builder()
                .apiId(po.getApiId())
                .provider(po.getProvider())
                .baseUrl(po.getBaseUrl())
                .apiKey(po.getApiKey())
                .completionsPath(po.getCompletionsPath())
                .embeddingsPath(po.getEmbeddingsPath())
                .enabled(po.getEnabled() != null && po.getEnabled() == 1)
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private AgentModelProfileEntity toProfileEntity(AgentModelProfilePO po) {
        return AgentModelProfileEntity.builder()
                .modelProfileId(po.getModelProfileId())
                .apiId(po.getApiId())
                .modelName(po.getModelName())
                .modelType(po.getModelType())
                .defaultTemperature(po.getDefaultTemperature())
                .defaultMaxOutputTokens(po.getDefaultMaxOutputTokens())
                .embeddingDimensions(po.getEmbeddingDimensions())
                .timeoutMs(po.getTimeoutMs())
                .enabled(po.getEnabled() != null && po.getEnabled() == 1)
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private AgentNodeModelBindingEntity toBindingEntity(AgentNodeModelBindingPO po) {
        return AgentNodeModelBindingEntity.builder()
                .bindingId(po.getBindingId())
                .nodeCode(po.getNodeCode())
                .modelProfileId(po.getModelProfileId())
                .promptVersion(po.getPromptVersion())
                .contractVersion(po.getContractVersion())
                .temperature(po.getTemperature())
                .maxOutputTokens(po.getMaxOutputTokens())
                .maxRepairAttempts(po.getMaxRepairAttempts())
                .enabled(po.getEnabled() != null && po.getEnabled() == 1)
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
