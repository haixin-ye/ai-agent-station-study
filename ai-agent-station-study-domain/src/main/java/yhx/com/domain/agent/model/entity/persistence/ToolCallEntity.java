package yhx.com.domain.agent.model.entity.persistence;

import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallEntity {

    private String toolCallId;
    private String toolInvocationId;
    private String runId;
    private String toolName;
    private String mcpServerName;
    private String mcpTransportType;
    private ToolCallStatusEnumVO status;
    private String inputSchemaRef;
    private String intentRef;
    private String argumentsRef;
    private String receiptRef;
    private String failureCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
