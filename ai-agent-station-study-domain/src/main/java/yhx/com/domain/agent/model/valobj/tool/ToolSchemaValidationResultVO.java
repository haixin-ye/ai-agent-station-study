package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolSchemaValidationResultVO {

    private Boolean valid;
    private String schemaHash;
    private List<ToolSchemaViolationVO> violations;
    private String safeMessage;
}
