package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Archive for a completed round.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundArchiveVO {

    private Integer round;
    private String node1PlanSnapshot;
    private String node2ExecutionSnapshot;
    private String node3VerificationSnapshot;
    @Builder.Default
    private List<AcceptedResultVO> acceptedResults = new ArrayList<>();
}
