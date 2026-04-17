package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight execution facts produced by Node2 and consumed by Node3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundExecutionSummaryVO {

    @Builder.Default
    private Boolean toolRequired = false;

    @Builder.Default
    private Boolean toolInvoked = false;

    @Builder.Default
    private List<String> invokedTools = new ArrayList<>();

    @Builder.Default
    private Boolean toolSuccess = false;

    @Builder.Default
    private Boolean evidenceAvailable = false;

    private String evidenceSummary;

    private String blockingReason;

    private String rawExecutionResult;
}
