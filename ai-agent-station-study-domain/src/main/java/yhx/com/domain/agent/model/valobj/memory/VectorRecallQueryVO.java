package yhx.com.domain.agent.model.valobj.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorRecallQueryVO {

    private String queryText;
    private VectorRecallFilterVO filter;
    private Integer topK;
    private Double minScore;
}
