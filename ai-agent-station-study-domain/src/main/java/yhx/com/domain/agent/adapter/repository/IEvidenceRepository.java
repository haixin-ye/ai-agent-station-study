package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;

import java.util.List;
import java.util.Optional;

public interface IEvidenceRepository {

    String saveEvidence(AgentEvidenceEntity evidence);

    Optional<AgentEvidenceEntity> findEvidence(String evidenceId);

    List<AgentEvidenceEntity> listRunEvidence(String runId);
}
