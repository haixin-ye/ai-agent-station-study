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
public class SummaryCandidateVO {

    private String summaryId;
    private String turnId;
    private String summary;
    private String summaryRef;
    private List<String> artifactRefs;
    private Double relevanceScore;
    private String sourceChannel;
    private Double sourceScore;
    private List<String> sourceReasons;
    private Long messageStartSeq;
    private Long messageEndSeq;
    private LocalDateTime createdAt;
}
