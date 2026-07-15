package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolSchemaViolationVO {

    private String path;
    private String keyword;
    private String expectedConstraint;
    private String actualType;
    private String message;
}
