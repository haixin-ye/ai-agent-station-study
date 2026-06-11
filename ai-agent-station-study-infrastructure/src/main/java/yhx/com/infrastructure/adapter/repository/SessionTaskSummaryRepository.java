package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.ISessionTaskSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionTaskSummaryEntity;
import yhx.com.infrastructure.dao.IAgentSessionTaskSummaryDao;
import yhx.com.infrastructure.dao.po.AgentSessionTaskSummaryPO;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SessionTaskSummaryRepository implements ISessionTaskSummaryRepository {

    @Resource
    private IAgentSessionTaskSummaryDao agentSessionTaskSummaryDao;

    @Override
    public String saveSummary(AgentSessionTaskSummaryEntity summary) {
        if (summary.getSummaryId() == null || summary.getSummaryId().isBlank()) {
            summary.setSummaryId("session-task-summary-" + UUID.randomUUID());
        }
        if (summary.getStatus() == null || summary.getStatus().isBlank()) {
            summary.setStatus("ACTIVE");
        }
        if (summary.getVersionNo() == null || summary.getVersionNo() <= 0) {
            summary.setVersionNo(nextVersionNo(summary.getSessionId()));
        }
        LocalDateTime now = LocalDateTime.now();
        if (summary.getCreatedAt() == null) {
            summary.setCreatedAt(now);
        }
        if (summary.getUpdatedAt() == null) {
            summary.setUpdatedAt(now);
        }
        agentSessionTaskSummaryDao.insert(toPO(summary));
        return summary.getSummaryId();
    }

    @Override
    public Optional<AgentSessionTaskSummaryEntity> findActiveBySessionId(String sessionId) {
        return Optional.ofNullable(agentSessionTaskSummaryDao.queryActiveBySessionId(sessionId)).map(this::toEntity);
    }

    @Override
    public int nextVersionNo(String sessionId) {
        Integer current = agentSessionTaskSummaryDao.queryMaxVersionNo(sessionId);
        return (current == null ? 0 : current) + 1;
    }

    @Override
    public void markActiveSuperseded(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        agentSessionTaskSummaryDao.updateActiveSuperseded(sessionId);
    }

    private AgentSessionTaskSummaryPO toPO(AgentSessionTaskSummaryEntity entity) {
        return AgentSessionTaskSummaryPO.builder()
                .summaryId(entity.getSummaryId())
                .sessionId(entity.getSessionId())
                .userId(entity.getUserId())
                .summaryRef(entity.getSummaryRef())
                .versionNo(entity.getVersionNo())
                .sourceTurnCount(entity.getSourceTurnCount())
                .sourceLatestTurnId(entity.getSourceLatestTurnId())
                .sourceLatestTurnNo(entity.getSourceLatestTurnNo())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentSessionTaskSummaryEntity toEntity(AgentSessionTaskSummaryPO po) {
        return AgentSessionTaskSummaryEntity.builder()
                .summaryId(po.getSummaryId())
                .sessionId(po.getSessionId())
                .userId(po.getUserId())
                .summaryRef(po.getSummaryRef())
                .versionNo(po.getVersionNo())
                .sourceTurnCount(po.getSourceTurnCount())
                .sourceLatestTurnId(po.getSourceLatestTurnId())
                .sourceLatestTurnNo(po.getSourceLatestTurnNo())
                .status(po.getStatus())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
