package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Overall plan for the session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterPlanVO {

    private String planId;
    private Integer planVersion;
    private SessionGoalVO sessionGoal;
    @Builder.Default
    private List<PlanStepVO> mainSteps = new ArrayList<>();
}
