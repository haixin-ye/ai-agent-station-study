package yhx.com.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified trace snapshot for one node execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoAgentNodeTraceVO {

    private String nodeId;

    private String contractVersion;

    private String parseMode;

    private String recoveryLevel;

    @Builder.Default
    private Boolean lowConfidence = false;

    private String blockingReason;

    @Builder.Default
    private List<String> sourceOfTruthUsed = new ArrayList<>();
}
