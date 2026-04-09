package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Node1 产出的结构化执行计划，供 Node2 严格执行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepExecutionPlanVO {

    /**
     * 计划标识，可用于回溯。
     */
    private String planId;

    /**
     * 计划轮次（对应 dynamic step）。
     */
    private Integer round;

    /**
     * 清洗后的全局任务目标（仅首轮必填，后续可复用）。
     */
    private String sanitizedUserGoal;

    /**
     * 本轮任务目标。
     */
    private String taskGoal;

    /**
     * 是否需要调用工具。
     */
    private Boolean toolRequired;

    /**
     * 本轮允许调用的唯一工具名。
     */
    private String toolName;

    /**
     * 调用工具的目的说明。
     */
    private String toolPurpose;

    /**
     * 工具参数提示（非最终实参）。
     */
    private String toolArgsHint;

    /**
     * 预期产出。
     */
    private String expectedOutput;

    /**
     * 本轮完成判据。
     */
    private String completionHint;
}

