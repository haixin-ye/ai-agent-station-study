package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.infrastructure.dao.IAgentEvidenceDao;
import yhx.com.infrastructure.dao.po.AgentEvidencePO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class EvidenceRepository implements IEvidenceRepository {

    @Resource
    private IAgentEvidenceDao agentEvidenceDao;

    @Override
    public String saveEvidence(AgentEvidenceEntity evidence) {
        if (evidence.getEvidenceId() == null || evidence.getEvidenceId().isBlank()) {
            evidence.setEvidenceId("evidence-" + UUID.randomUUID());
        }
        if (evidence.getCreatedAt() == null) {
            evidence.setCreatedAt(LocalDateTime.now());
        }
        agentEvidenceDao.insert(toPO(evidence));
        return evidence.getEvidenceId();
    }

    @Override
    public Optional<AgentEvidenceEntity> findEvidence(String evidenceId) {
        return Optional.ofNullable(agentEvidenceDao.queryByEvidenceId(evidenceId)).map(this::toEntity);
    }

    @Override
    public List<AgentEvidenceEntity> listRunEvidence(String runId) {
        return agentEvidenceDao.listByRunId(runId).stream().map(this::toEntity).collect(Collectors.toList());
    }

    private AgentEvidencePO toPO(AgentEvidenceEntity entity) {
        return AgentEvidencePO.builder()
                .evidenceId(entity.getEvidenceId())
                .runId(entity.getRunId())
                .evidenceType(entity.getEvidenceType())
                .sourceRef(entity.getSourceRef())
                .summary(entity.getSummary())
                .confidence(entity.getConfidence())
                .usedByFinal(Boolean.TRUE.equals(entity.getUsedByFinal()) ? 1 : 0)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AgentEvidenceEntity toEntity(AgentEvidencePO po) {
        return AgentEvidenceEntity.builder()
                .evidenceId(po.getEvidenceId())
                .runId(po.getRunId())
                .evidenceType(po.getEvidenceType())
                .sourceRef(po.getSourceRef())
                .summary(po.getSummary())
                .confidence(po.getConfidence())
                .usedByFinal(po.getUsedByFinal() != null && po.getUsedByFinal() == 1)
                .createdAt(po.getCreatedAt())
                .build();
    }
}
