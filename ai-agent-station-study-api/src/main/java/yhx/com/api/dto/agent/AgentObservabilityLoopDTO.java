package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentObservabilityLoopDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer loopIndex;
    private String status;
    private String stage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @Builder.Default
    private Map<String, Object> stateView = new LinkedHashMap<>();
    @Builder.Default
    private List<Map<String, Object>> stateViewSources = List.of();
    @Builder.Default
    private List<String> promptRefs = List.of();
    @Builder.Default
    private List<Map<String, Object>> attempts = List.of();
    private String action;
    @Builder.Default
    private Map<String, Object> actionInput = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Object> actionOutput = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Object> runtimeOutcome = new LinkedHashMap<>();
    @Builder.Default
    private List<Map<String, Object>> toolResults = List.of();
    @Builder.Default
    private List<Map<String, Object>> childAgentResults = List.of();
    @Builder.Default
    private Map<String, Object> checkpoint = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Object> error = new LinkedHashMap<>();
}
