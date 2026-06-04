package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotebookRiskVO {

    private String id;
    private String content;
    private String status;
    private List<String> sourceEvidenceIds;
    private List<String> sourceWorkIds;
    private Integer loopIndex;
    private Long sequence;
}
