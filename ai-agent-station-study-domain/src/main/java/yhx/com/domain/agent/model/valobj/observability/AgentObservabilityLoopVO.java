package yhx.com.domain.agent.model.valobj.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentObservabilityLoopVO {
    private Integer loopIndex;
    private String status;
    private String stage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @Builder.Default private Map<String, Object> stateView = new LinkedHashMap<>();
    @Builder.Default private Map<String, Object> selectedContext = new LinkedHashMap<>();
    @Builder.Default private List<Map<String, Object>> stateViewSources = List.of();
    @Builder.Default private Map<String, Object> taskLedger = new LinkedHashMap<>();
    @Builder.Default private Map<String, Object> taskUpdate = new LinkedHashMap<>();
    @Builder.Default private Map<String, Object> roundDelta = new LinkedHashMap<>();
    @Builder.Default private List<Map<String, Object>> roundHistory = List.of();
    @Builder.Default private List<String> promptRefs = List.of();
    @Builder.Default private List<Map<String, Object>> attempts = List.of();
    private String action;
    @Builder.Default private Map<String, Object> actionInput = new LinkedHashMap<>();
    @Builder.Default private Map<String, Object> actionOutput = new LinkedHashMap<>();
    @Builder.Default private Map<String, Object> runtimeOutcome = new LinkedHashMap<>();
    @Builder.Default private List<Map<String, Object>> toolResults = List.of();
    @Builder.Default private List<Map<String, Object>> childAgentResults = List.of();
    @Builder.Default private Map<String, Object> checkpoint = new LinkedHashMap<>();
    @Builder.Default private Map<String, Object> error = new LinkedHashMap<>();
    @Builder.Default private Map<String, Object> contextCandidates = new LinkedHashMap<>();
    @Builder.Default private Map<String, Object> contextPlanner = new LinkedHashMap<>();
    @Builder.Default private Map<String, Object> finalDelivery = new LinkedHashMap<>();
    @Builder.Default private List<Map<String, Object>> timeline = List.of();
}
