package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session-level goal extracted from user input.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionGoalVO {

    private String rawUserInput;
    private String sanitizedGoal;
    private String successCriteria;
    private Integer maxRounds;
    private String failurePolicy;
}
