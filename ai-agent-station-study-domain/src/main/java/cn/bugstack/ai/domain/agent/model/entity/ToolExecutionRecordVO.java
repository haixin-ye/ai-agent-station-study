package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool execution record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionRecordVO {

    private Integer roundIndex;
    private String stepId;
    private String toolName;
    private String requestPayload;
    private String responsePayload;
    private String normalizedOutcome;
    private Boolean success;
    private String errorType;
    private String errorMessage;
    private String timestamp;
}
