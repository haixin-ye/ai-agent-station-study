package cn.bugstack.ai.domain.agent.model.entity;

import cn.bugstack.ai.domain.agent.model.valobj.enums.StepStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single step in the plan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStepVO {

    private String stepId;
    private String title;
    private String goal;
    private String completionCriteria;
    @Builder.Default
    private java.util.List<String> dependencies = new java.util.ArrayList<>();
    private StepStatusEnumVO status;
}
