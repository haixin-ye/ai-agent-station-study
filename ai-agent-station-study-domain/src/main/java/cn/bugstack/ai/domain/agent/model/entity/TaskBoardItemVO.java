package cn.bugstack.ai.domain.agent.model.entity;

import cn.bugstack.ai.domain.agent.model.valobj.enums.StepStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Board item representing a unit of work.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskBoardItemVO {

    private String stepId;
    private String lastRoundTask;
    @Builder.Default
    private java.util.List<String> acceptedOutputs = new java.util.ArrayList<>();
    private String lastFailureReason;
    private Integer attemptCount;
    private StepStatusEnumVO status;
}
