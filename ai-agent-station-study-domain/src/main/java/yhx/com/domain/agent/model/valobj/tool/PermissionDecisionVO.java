package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionDecisionStatusEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDecisionVO {

    private PermissionDecisionStatusEnumVO status;
    private String failureCode;
    private String reason;
}
