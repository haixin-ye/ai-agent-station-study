package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagCandidateVO {

    private String candidateId;
    private String sourceType;
    private String documentId;
    private String chunkId;
    private String sourceName;
    private String title;
    private String summary;
    private String snippet;
    private String contentRef;
    private String retrievalTextRef;
    private String injectMode;
    private Integer chunkNo;
    private Integer chunkCount;
    private Integer tokenCount;
    private Double sourceScore;
    private String sourceChannel;
    private List<String> reasons;
    private RagCodeCandidateMetaVO codeMeta;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
