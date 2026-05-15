package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentFinalResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String runId;
    private String sessionId;
    private String status;
    private String messageId;
    private String finalAnswer;
    private List<AgentArtifactSummaryDTO> artifacts;
    private List<String> citations;
    private List<String> followUpOptions;
    private Boolean completed;
}
