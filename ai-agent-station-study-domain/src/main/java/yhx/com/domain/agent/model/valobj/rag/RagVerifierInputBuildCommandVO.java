package yhx.com.domain.agent.model.valobj.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.RagHitEntity;
import yhx.com.domain.agent.model.entity.persistence.RagQueryEntity;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagVerifierInputBuildCommandVO {

    private String runId;
    private String sessionId;
    private Integer loopIndex;
    private String userMessageId;
    private String userInput;
    private FinalAnswerCandidateVO finalAnswerCandidate;
    private Boolean ragWasUsed;
    private Boolean requiresKnowledgeBaseGrounding;
    private Boolean claimsKnowledgeBaseGrounding;
    private List<String> citations;
    private List<RagQueryEntity> ragQueries;
    private List<RagHitEntity> ragHits;
    private List<AgentEvidenceEntity> ragEvidence;
    private Integer maxEvidenceSnippetChars;
}
