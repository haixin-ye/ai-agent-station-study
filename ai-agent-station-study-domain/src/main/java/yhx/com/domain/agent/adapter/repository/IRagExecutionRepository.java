package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.RagHitEntity;
import yhx.com.domain.agent.model.entity.persistence.RagQueryEntity;

import java.util.List;

public interface IRagExecutionRepository {

    String saveRagQuery(RagQueryEntity query);

    void saveRagHits(List<RagHitEntity> hits);

    List<RagHitEntity> listRagHits(String runId);
}
