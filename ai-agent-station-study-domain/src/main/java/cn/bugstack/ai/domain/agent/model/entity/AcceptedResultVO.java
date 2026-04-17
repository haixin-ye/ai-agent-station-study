package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Accepted result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcceptedResultVO {

    private String stepId;
    private String resultType;
    private String content;
    private java.util.List<String> evidenceRefs;
    private Integer acceptedByRound;
    private String acceptedReason;
}
