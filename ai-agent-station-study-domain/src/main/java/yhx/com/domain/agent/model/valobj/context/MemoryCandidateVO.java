package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryCandidateVO {

    private String memoryId;
    private String memoryType;
    private String summary;
    private String content;
    private String contentRef;
    private BigDecimal score;
    private Double relevanceScore;
    private String sourceChannel;
    private Double sourceScore;
}
