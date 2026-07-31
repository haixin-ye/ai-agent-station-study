package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentObservabilityStudioDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Builder.Default
    private Map<String, Object> header = new LinkedHashMap<>();
    private String status;
    private String currentPhase;
    @Builder.Default
    private Map<String, Object> context = new LinkedHashMap<>();
    @Builder.Default
    private List<AgentObservabilityLoopDTO> loops = List.of();
    @Builder.Default
    private List<AgentDebugTraceDTO> traces = List.of();
    @Builder.Default
    private Map<String, AgentDebugPayloadDTO> payloads = new LinkedHashMap<>();
    @Builder.Default
    private List<Map<String, Object>> evidence = List.of();
    @Builder.Default
    private List<Map<String, Object>> toolCalls = List.of();
    private AgentPendingInputDTO pendingInput;
    private String finalAnswer;
    private Long lastSeq;
}
