package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured execution plan produced by Node1 for Node2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepExecutionPlanVO {

    private String planId;

    private Integer round;

    private String sanitizedUserGoal;

    private String taskGoal;

    private Boolean toolRequired;

    private String toolName;

    private String toolPurpose;

    private String toolArgsHint;

    private String expectedOutput;

    /**
     * Source payload explicitly carried from Node1 to Node2 when the task
     * depends on history or other private context that Node2 cannot fetch alone.
     */
    private String sourceContent;

    private String completionHint;
}
