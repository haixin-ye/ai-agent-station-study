package cn.bugstack.ai.domain.agent.model.entity;

import cn.bugstack.ai.domain.agent.model.valobj.enums.OverallStateEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session overall status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverallStatusVO {

    private OverallStateEnumVO state;
    @Builder.Default
    private java.util.List<String> completedSteps = new java.util.ArrayList<>();
    @Builder.Default
    private java.util.List<String> remainingSteps = new java.util.ArrayList<>();
    @Builder.Default
    private java.util.List<String> blockedReasons = new java.util.ArrayList<>();
    private String finalDecision;

    public static OverallStatusVO running() {
        return OverallStatusVO.builder()
                .state(OverallStateEnumVO.RUNNING)
                .build();
    }
}
