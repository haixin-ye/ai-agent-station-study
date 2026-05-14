package yhx.com.domain.agent.service.evidence;

import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.EvidenceCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;

import java.util.List;

public class EvidenceManager {

    private final IEvidenceRepository evidenceRepository;
    private final EvidenceCandidatePreselector evidenceCandidatePreselector;
    private final EvidencePackBuilder evidencePackBuilder;

    public EvidenceManager(IEvidenceRepository evidenceRepository) {
        this(evidenceRepository, new EvidenceCandidatePreselector(), new EvidencePackBuilder());
    }

    public EvidenceManager(IEvidenceRepository evidenceRepository,
                           EvidenceCandidatePreselector evidenceCandidatePreselector,
                           EvidencePackBuilder evidencePackBuilder) {
        this.evidenceRepository = evidenceRepository;
        this.evidenceCandidatePreselector = evidenceCandidatePreselector;
        this.evidencePackBuilder = evidencePackBuilder;
    }

    public String saveEvidence(AgentEvidenceEntity evidence) {
        return evidenceRepository.saveEvidence(evidence);
    }

    public List<EvidenceCandidateVO> selectEvidenceCandidates(String runId, String sessionId, String userInput, int limit) {
        return evidenceCandidatePreselector.select(userInput, evidenceRepository.listRunEvidence(runId), limit);
    }

    public List<MaterializedEvidenceVO> buildEvidencePack(List<ContextSelectionVO> selections) {
        return List.of();
    }

    public List<MaterializedEvidenceVO> buildEvidencePackFromCandidates(List<EvidenceCandidateVO> candidates) {
        return evidencePackBuilder.buildFromCandidates(candidates);
    }
}
