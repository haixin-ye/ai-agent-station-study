package yhx.com.domain.agent.model.valobj.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentObservabilitySnapshotVO {
    @Builder.Default
    private Map<String, Object> header = new LinkedHashMap<>();
    private String status;
    private String currentPhase;
    @Builder.Default
    private Map<String, Object> context = new LinkedHashMap<>();
    @Builder.Default
    private List<AgentObservabilityLoopVO> loops = List.of();
    @Builder.Default
    private List<AgentRunTraceEntity> traces = List.of();
    @Builder.Default
    private Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
    @Builder.Default
    private List<AgentEvidenceEntity> evidence = List.of();
    @Builder.Default
    private List<ToolCallEntity> toolCalls = List.of();
    private Long lastSeq;
}
