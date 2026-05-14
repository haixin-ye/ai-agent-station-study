package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterializedEvidenceVO {

    private String evidenceId;
    private String evidenceType;
    private String sourceRef;
    private String summary;
    private String boundedSnippet;
}
