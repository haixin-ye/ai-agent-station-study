package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryCandidateVO {

    private String summaryId;
    private String summary;
    private String summaryRef;
    private Long messageStartSeq;
    private Long messageEndSeq;
}
