package yhx.com.domain.agent.model.valobj.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagVerifierInputVO {

    private RunMeta runMeta;
    private UserRequest userRequest;
    private FinalCandidate finalAnswerCandidate;
    private RagContext ragContext;
    private List<EvidenceItem> evidence;
    private VerificationMode verificationMode;
    private String outputContractVersion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunMeta {
        private String runId;
        private String sessionId;
        private Integer loopIndex;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRequest {
        private String messageId;
        private String content;
        private Boolean requiresKnowledgeBaseGrounding;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinalCandidate {
        private String targetId;
        private String content;
        private List<Citation> citations;
        private Boolean claimsKnowledgeBaseGrounding;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private String evidenceId;
        private String usage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagContext {
        private Boolean ragWasUsed;
        private Integer queryCount;
        private List<QueryItem> queries;
        private Boolean noHit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryItem {
        private String ragQueryId;
        private String query;
        private String status;
        private Integer hitCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceItem {
        private String evidenceId;
        private String ragQueryId;
        private String sourceTitle;
        private String chunkSummary;
        private String chunkSnippet;
        private String citationLabel;
        private String relevance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationMode {
        private String mode;
        private Boolean strictCitationCheck;
        private Boolean allowGeneralKnowledgeWhenNotClaimingRag;
    }
}
