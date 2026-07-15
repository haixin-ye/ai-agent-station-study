package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.tool.McpToolAvailabilityEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.McpTransportTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.RequiredPermissionEnumVO;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolSpecVO {

    private String mcpServerCode;
    private String toolName;
    private String description;
    private McpTransportTypeEnumVO transportType;
    private String inputSchemaRef;
    private Map<String, Object> inputSchema;
    private RequiredPermissionEnumVO requiredPermission;
    private String riskLevel;
    private Boolean destructive;
    private Boolean schemaLessAllowed;
    @Builder.Default
    private McpToolAvailabilityEnumVO availability = McpToolAvailabilityEnumVO.AVAILABLE;
    private String availabilityReason;
}
