package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured decision produced by Node3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisionDecisionVO {

    public static final String PASS = "PASS";
    public static final String REPLAN = "REPLAN";
    public static final String ROUND_PASS = "ROUND_PASS";
    public static final String ROUND_RETRY = "ROUND_RETRY";
    public static final String OVERALL_PASS = "OVERALL_PASS";
    public static final String OVERALL_CONTINUE = "OVERALL_CONTINUE";

    /**
     * Legacy field for compatibility: PASS|REPLAN
     * PASS maps to overall pass; REPLAN maps to overall continue.
     */
    private String decision;

    /**
     * Round-level decision: ROUND_PASS|ROUND_RETRY
     */
    private String roundDecision;

    /**
     * Overall-goal decision: OVERALL_PASS|OVERALL_CONTINUE
     */
    private String overallDecision;

    /**
     * Suggested next action: RETRY_SAME_ROUND|NEXT_ROUND_REPLAN|FINISH
     */
    private String nextAction;

    private String assessment;
    private String issues;
    private String suggestions;
    private Integer score;
    private String raw;
}
