package yhx.com.domain.agent.model.valobj.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagVerificationRouteCommandVO {

    private String runId;
    private String sessionId;
    private Integer loopIndex;
    private String agentId;
    private String userMessageId;
    private String userInput;
    private Boolean ragWasUsed;
    private Boolean requiresKnowledgeBaseGrounding;
    private Boolean claimsKnowledgeBaseGrounding;
    private List<String> citations;
    private FinalAnswerCandidateVO finalAnswerCandidate;
    private Integer maxEvidenceSnippetChars;
}
