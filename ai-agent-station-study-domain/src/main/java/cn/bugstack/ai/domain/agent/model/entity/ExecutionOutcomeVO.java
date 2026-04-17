package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured execution outcome passed from Node2 to Node3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionOutcomeVO {

    public static final String SUCCESS = "SUCCESS";
    public static final String BLOCKED = "BLOCKED";
    public static final String FAILED = "FAILED";

    private String status;
    private String errorCode;
    private String errorMessage;
    private String rawResult;
}
