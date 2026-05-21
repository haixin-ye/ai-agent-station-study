package yhx.com.domain.agent.model.valobj.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnSummaryOutputVO {

    private String summary;
    private String intent;
    private List<String> topics;
    private List<Map<String, Object>> entities;
    private List<String> artifactRefs;
    private BigDecimal importanceScore;
    private Boolean requiresLongTermExtraction;
}
