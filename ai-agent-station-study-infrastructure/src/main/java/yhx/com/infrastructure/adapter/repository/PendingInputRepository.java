package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IPendingInputRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.infrastructure.dao.IAgentPendingInputDao;
import yhx.com.infrastructure.dao.po.AgentPendingInputPO;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PendingInputRepository implements IPendingInputRepository {

    @Resource
    private IAgentPendingInputDao agentPendingInputDao;

    @Override
    public String createPendingInput(AgentPendingInputEntity pendingInput) {
        if (pendingInput.getPendingId() == null || pendingInput.getPendingId().isBlank()) {
            pendingInput.setPendingId("pending-" + UUID.randomUUID());
        }
        if (pendingInput.getCreatedAt() == null) {
            pendingInput.setCreatedAt(LocalDateTime.now());
        }
        agentPendingInputDao.insert(toPO(pendingInput));
        return pendingInput.getPendingId();
    }

    @Override
    public void markAnswered(String pendingId, String userAnswerRef) {
        agentPendingInputDao.markAnswered(pendingId, userAnswerRef);
    }

    @Override
    public Optional<AgentPendingInputEntity> findActivePendingInput(String runId) {
        return Optional.ofNullable(agentPendingInputDao.queryActiveByRunId(runId)).map(this::toEntity);
    }

    @Override
    public Optional<AgentPendingInputEntity> findByPendingId(String pendingId) {
        return Optional.ofNullable(agentPendingInputDao.queryByPendingId(pendingId)).map(this::toEntity);
    }

    @Override
    public void markCancelled(String pendingId) {
        agentPendingInputDao.markCancelled(pendingId);
    }

    @Override
    public void markExpired(String pendingId) {
        agentPendingInputDao.markExpired(pendingId);
    }

    private AgentPendingInputPO toPO(AgentPendingInputEntity entity) {
        return AgentPendingInputPO.builder()
                .pendingId(entity.getPendingId())
                .runId(entity.getRunId())
                .sourceComponent(entity.getSourceComponent())
                .pendingType(entity.getPendingType())
                .inputMode(entity.getInputMode())
                .status(entity.getStatus())
                .question(entity.getQuestion())
                .optionsRef(entity.getOptionsRef())
                .answerSchemaRef(entity.getAnswerSchemaRef())
                .continuationRef(entity.getContinuationRef())
                .userAnswerRef(entity.getUserAnswerRef())
                .createdAt(entity.getCreatedAt())
                .answeredAt(entity.getAnsweredAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    private AgentPendingInputEntity toEntity(AgentPendingInputPO po) {
        return AgentPendingInputEntity.builder()
                .pendingId(po.getPendingId())
                .runId(po.getRunId())
                .sourceComponent(po.getSourceComponent())
                .pendingType(po.getPendingType())
                .inputMode(po.getInputMode())
                .status(po.getStatus())
                .question(po.getQuestion())
                .optionsRef(po.getOptionsRef())
                .answerSchemaRef(po.getAnswerSchemaRef())
                .continuationRef(po.getContinuationRef())
                .userAnswerRef(po.getUserAnswerRef())
                .createdAt(po.getCreatedAt())
                .answeredAt(po.getAnsweredAt())
                .expiresAt(po.getExpiresAt())
                .build();
    }
}
