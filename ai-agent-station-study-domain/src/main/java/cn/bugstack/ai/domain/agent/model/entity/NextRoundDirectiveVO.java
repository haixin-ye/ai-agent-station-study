package cn.bugstack.ai.domain.agent.model.entity;

import cn.bugstack.ai.domain.agent.model.valobj.enums.NextRoundDirectiveTypeEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Directive for the next round.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextRoundDirectiveVO {

    private NextRoundDirectiveTypeEnumVO directiveType;
    private String targetStepId;
    private String reason;

    public static NextRoundDirectiveVO replanSameStep(String targetStepId, String reason) {
        return NextRoundDirectiveVO.builder()
                .directiveType(NextRoundDirectiveTypeEnumVO.REPLAN_SAME_STEP)
                .targetStepId(targetStepId)
                .reason(reason)
                .build();
    }
}
