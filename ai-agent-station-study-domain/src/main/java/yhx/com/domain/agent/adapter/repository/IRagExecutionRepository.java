package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.RagHitEntity;
import yhx.com.domain.agent.model.entity.persistence.RagQueryEntity;

import java.util.List;

public interface IRagExecutionRepository {

    String saveRagQuery(RagQueryEntity query);

    void updateRagQueryStatus(String ragQueryId, String status, String failureCode, String failureMessage);

    void saveRagHits(List<RagHitEntity> hits);

    List<RagQueryEntity> listRagQueries(String runId);

    List<RagHitEntity> listRagHits(String runId);
}
