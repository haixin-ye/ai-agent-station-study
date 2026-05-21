package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.infrastructure.dao.IAgentTurnSummaryDao;
import yhx.com.infrastructure.dao.po.AgentTurnSummaryPO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TurnSummaryRepository implements ITurnSummaryRepository {

    @Resource
    private IAgentTurnSummaryDao agentTurnSummaryDao;

    @Override
    public String saveSummary(AgentTurnSummaryEntity summary) {
        if (summary.getSummaryId() == null || summary.getSummaryId().isBlank()) {
            summary.setSummaryId("turn-summary-" + UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        if (summary.getStatus() == null || summary.getStatus().isBlank()) {
            summary.setStatus("ACTIVE");
        }
        if (summary.getCreatedAt() == null) {
            summary.setCreatedAt(now);
        }
        if (summary.getUpdatedAt() == null) {
            summary.setUpdatedAt(now);
        }
        agentTurnSummaryDao.insert(toPO(summary));
        return summary.getSummaryId();
    }

    @Override
    public Optional<AgentTurnSummaryEntity> findSummaryByTurnId(String turnId) {
        return Optional.ofNullable(agentTurnSummaryDao.queryByTurnId(turnId)).map(this::toEntity);
    }

    @Override
    public List<AgentTurnSummaryEntity> listByTurnIds(List<String> turnIds) {
        if (turnIds == null || turnIds.isEmpty()) {
            return List.of();
        }
        return agentTurnSummaryDao.listByTurnIds(turnIds).stream().map(this::toEntity).toList();
    }

    private AgentTurnSummaryPO toPO(AgentTurnSummaryEntity entity) {
        return AgentTurnSummaryPO.builder()
                .summaryId(entity.getSummaryId())
                .turnId(entity.getTurnId())
                .sessionId(entity.getSessionId())
                .runId(entity.getRunId())
                .userId(entity.getUserId())
                .summaryRef(entity.getSummaryRef())
                .intent(entity.getIntent())
                .topicsJson(entity.getTopicsJson())
                .entitiesJson(entity.getEntitiesJson())
                .artifactRefsJson(entity.getArtifactRefsJson())
                .importanceScore(entity.getImportanceScore())
                .requiresLongTermExtraction(Boolean.TRUE.equals(entity.getRequiresLongTermExtraction()) ? 1 : 0)
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentTurnSummaryEntity toEntity(AgentTurnSummaryPO po) {
        return AgentTurnSummaryEntity.builder()
                .summaryId(po.getSummaryId())
                .turnId(po.getTurnId())
                .sessionId(po.getSessionId())
                .runId(po.getRunId())
                .userId(po.getUserId())
                .summaryRef(po.getSummaryRef())
                .intent(po.getIntent())
                .topicsJson(po.getTopicsJson())
                .entitiesJson(po.getEntitiesJson())
                .artifactRefsJson(po.getArtifactRefsJson())
                .importanceScore(po.getImportanceScore())
                .requiresLongTermExtraction(po.getRequiresLongTermExtraction() != null && po.getRequiresLongTermExtraction() == 1)
                .status(po.getStatus())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
