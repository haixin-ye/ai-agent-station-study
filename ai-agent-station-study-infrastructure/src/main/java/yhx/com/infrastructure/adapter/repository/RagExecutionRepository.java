package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IRagExecutionRepository;
import yhx.com.domain.agent.model.entity.persistence.RagHitEntity;
import yhx.com.domain.agent.model.entity.persistence.RagQueryEntity;
import yhx.com.infrastructure.dao.IAgentRagHitDao;
import yhx.com.infrastructure.dao.IAgentRagQueryDao;
import yhx.com.infrastructure.dao.po.AgentRagHitPO;
import yhx.com.infrastructure.dao.po.AgentRagQueryPO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class RagExecutionRepository implements IRagExecutionRepository {

    private static final int FAILURE_CODE_LIMIT = 64;
    private static final int FAILURE_MESSAGE_LIMIT = 4000;

    @Resource
    private IAgentRagQueryDao agentRagQueryDao;

    @Resource
    private IAgentRagHitDao agentRagHitDao;

    @Override
    public String saveRagQuery(RagQueryEntity query) {
        if (query.getRagQueryId() == null || query.getRagQueryId().isBlank()) {
            query.setRagQueryId("rag-query-" + UUID.randomUUID());
        }
        if (query.getCreatedAt() == null) {
            query.setCreatedAt(LocalDateTime.now());
        }
        agentRagQueryDao.insert(toPO(query));
        return query.getRagQueryId();
    }

    @Override
    public void updateRagQueryStatus(String ragQueryId, String status, String failureCode, String failureMessage) {
        agentRagQueryDao.updateStatus(ragQueryId, status, truncate(failureCode, FAILURE_CODE_LIMIT),
                truncate(failureMessage, FAILURE_MESSAGE_LIMIT));
    }

    @Override
    public void saveRagHits(List<RagHitEntity> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (RagHitEntity hit : hits) {
            if (hit.getRagHitId() == null || hit.getRagHitId().isBlank()) {
                hit.setRagHitId("rag-hit-" + UUID.randomUUID());
            }
            if (hit.getCreatedAt() == null) {
                hit.setCreatedAt(now);
            }
            agentRagHitDao.insert(toPO(hit));
        }
    }

    @Override
    public List<RagHitEntity> listRagHits(String runId) {
        return agentRagHitDao.listByRunId(runId).stream().map(this::toEntity).toList();
    }

    @Override
    public List<RagQueryEntity> listRagQueries(String runId) {
        return agentRagQueryDao.listByRunId(runId).stream().map(this::toQueryEntity).toList();
    }

    private AgentRagQueryPO toPO(RagQueryEntity entity) {
        return AgentRagQueryPO.builder()
                .ragQueryId(entity.getRagQueryId())
                .runId(entity.getRunId())
                .queryText(entity.getQueryText())
                .knowledgeTag(entity.getKnowledgeTag())
                .filtersRef(entity.getFiltersRef())
                .topK(entity.getTopK())
                .status(entity.getStatus())
                .failureCode(entity.getFailureCode())
                .failureMessage(entity.getFailureMessage())
                .createdAt(entity.getCreatedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }

    private RagQueryEntity toQueryEntity(AgentRagQueryPO po) {
        return RagQueryEntity.builder()
                .ragQueryId(po.getRagQueryId())
                .runId(po.getRunId())
                .queryText(po.getQueryText())
                .knowledgeTag(po.getKnowledgeTag())
                .filtersRef(po.getFiltersRef())
                .topK(po.getTopK())
                .status(po.getStatus())
                .failureCode(po.getFailureCode())
                .failureMessage(po.getFailureMessage())
                .createdAt(po.getCreatedAt())
                .completedAt(po.getCompletedAt())
                .build();
    }

    private AgentRagHitPO toPO(RagHitEntity entity) {
        return AgentRagHitPO.builder()
                .ragHitId(entity.getRagHitId())
                .ragQueryId(entity.getRagQueryId())
                .runId(entity.getRunId())
                .chunkRef(entity.getChunkRef())
                .score(entity.getScore())
                .sourceTitle(entity.getSourceTitle())
                .sourceUri(entity.getSourceUri())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private RagHitEntity toEntity(AgentRagHitPO po) {
        return RagHitEntity.builder()
                .ragHitId(po.getRagHitId())
                .ragQueryId(po.getRagQueryId())
                .runId(po.getRunId())
                .chunkRef(po.getChunkRef())
                .score(po.getScore())
                .sourceTitle(po.getSourceTitle())
                .sourceUri(po.getSourceUri())
                .createdAt(po.getCreatedAt())
                .build();
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        if (limit <= 3) {
            return value.substring(0, limit);
        }
        return value.substring(0, limit - 3) + "...";
    }
}
