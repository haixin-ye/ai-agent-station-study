package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;

import java.util.List;

public interface IEvidenceRepository {

    String saveEvidence(AgentEvidenceEntity evidence);

    List<AgentEvidenceEntity> listRunEvidence(String runId);
}
