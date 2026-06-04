package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextPreparationCommand {

    private String runId;
    private String sessionId;
    private String userId;
    private String agentId;
    private String userMessageId;
    private String userInput;
    private Integer loopIndex;
    private Integer recentMessageLimit;
    private Integer artifactCandidateLimit;
    private Integer memoryCandidateLimit;
    private Integer evidenceCandidateLimit;
    private Boolean vectorRecallEnabled;
    private Boolean ragRecallEnabled;
    private List<AgentArtifactEntity> artifactSeeds;
    private List<CapabilityCandidateVO> availableCapabilities;
    private TokenBudgetVO tokenBudget;
    private Map<String, Object> runtimeFacts;
}
