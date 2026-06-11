package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentVectorIndexEntity;
import yhx.com.infrastructure.dao.IAgentVectorIndexDao;
import yhx.com.infrastructure.dao.po.AgentVectorIndexPO;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class VectorIndexRepository implements IVectorIndexRepository {

    @Resource
    private IAgentVectorIndexDao agentVectorIndexDao;

    @Override
    public String saveOrUpdate(AgentVectorIndexEntity index) {
        if (index.getIndexId() == null || index.getIndexId().isBlank()) {
            index.setIndexId("vector-index-" + UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        if (index.getStatus() == null || index.getStatus().isBlank()) {
            index.setStatus("ACTIVE");
        }
        if (index.getCreatedAt() == null) {
            index.setCreatedAt(now);
        }
        if (index.getUpdatedAt() == null) {
            index.setUpdatedAt(now);
        }
        agentVectorIndexDao.insertOrUpdate(toPO(index));
        return index.getIndexId();
    }

    @Override
    public Optional<AgentVectorIndexEntity> findBySource(String collectionType, String sourceType, String sourceId) {
        return Optional.ofNullable(agentVectorIndexDao.queryBySource(collectionType, sourceType, sourceId))
                .map(this::toEntity);
    }

    @Override
    public void markDisabled(String collectionType, String sourceType, String sourceId) {
        agentVectorIndexDao.markDisabled(collectionType, sourceType, sourceId);
    }

    private AgentVectorIndexPO toPO(AgentVectorIndexEntity entity) {
        return AgentVectorIndexPO.builder()
                .indexId(entity.getIndexId())
                .collectionType(entity.getCollectionType())
                .sourceType(entity.getSourceType())
                .sourceId(entity.getSourceId())
                .vectorId(entity.getVectorId())
                .userId(entity.getUserId())
                .sessionId(entity.getSessionId())
                .contentHash(entity.getContentHash())
                .status(entity.getStatus())
                .failureMessage(entity.getFailureMessage())
                .indexedAt(entity.getIndexedAt())
                .disabledAt(entity.getDisabledAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentVectorIndexEntity toEntity(AgentVectorIndexPO po) {
        return AgentVectorIndexEntity.builder()
                .indexId(po.getIndexId())
                .collectionType(po.getCollectionType())
                .sourceType(po.getSourceType())
                .sourceId(po.getSourceId())
                .vectorId(po.getVectorId())
                .userId(po.getUserId())
                .sessionId(po.getSessionId())
                .contentHash(po.getContentHash())
                .status(po.getStatus())
                .failureMessage(po.getFailureMessage())
                .indexedAt(po.getIndexedAt())
                .disabledAt(po.getDisabledAt())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
