package cn.bugstack.ai.domain.agent.model.entity;

import cn.bugstack.ai.domain.agent.model.valobj.enums.StepStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Task contract for the current round.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentRoundTaskVO {

    private Integer roundIndex;
    private String currentStepId;
    private String roundTask;
    @Builder.Default
    private java.util.List<String> suggestedTools = new java.util.ArrayList<>();
    private String plannerNotes;
    private String expectedEvidence;
    private String sourceContent;
    private Boolean toolRequired;
    private StepStatusEnumVO status;
}
