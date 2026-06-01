package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviousLoopOutcomeVO {

    private String action;
    private String status;
    private String query;
    private String message;
    private List<String> createdEvidenceIds;
    private Integer loopIndex;
}
