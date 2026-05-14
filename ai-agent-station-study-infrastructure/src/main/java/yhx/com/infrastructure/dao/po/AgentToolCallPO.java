package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentToolCallPO {
    private Long id;
    private String toolCallId;
    private String toolInvocationId;
    private String runId;
    private String toolName;
    private String mcpServerName;
    private String mcpTransportType;
    private String status;
    private String inputSchemaRef;
    private String intentRef;
    private String argumentsRef;
    private String receiptRef;
    private String failureCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
