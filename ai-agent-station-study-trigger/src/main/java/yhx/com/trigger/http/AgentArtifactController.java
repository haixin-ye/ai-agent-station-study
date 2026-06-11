package yhx.com.trigger.http;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import yhx.com.api.dto.agent.AgentArtifactDetailDTO;
import yhx.com.api.dto.agent.AgentArtifactSummaryDTO;
import yhx.com.api.dto.agent.AgentArtifactVersionDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.service.api.AgentQueryFacade;
import yhx.com.trigger.http.support.AgentApiMapper;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/agent")
public class AgentArtifactController {

    @Resource
    private AgentQueryFacade agentQueryFacade;

    @GetMapping("/sessions/{sessionId}/artifacts")
    public Response<List<AgentArtifactSummaryDTO>> listArtifacts(@PathVariable("sessionId") String sessionId,
                                                                 @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return AgentResponseSupport.success(agentQueryFacade.listSessionArtifacts(sessionId, limit).stream()
                .map(AgentApiMapper::toArtifactSummary)
                .toList());
    }

    @GetMapping("/artifacts/{artifactId}")
    public Response<AgentArtifactDetailDTO> findArtifact(@PathVariable("artifactId") String artifactId) {
        return agentQueryFacade.findArtifact(artifactId)
                .map(artifact -> AgentApiMapper.toArtifactDetail(artifact, agentQueryFacade))
                .map(AgentResponseSupport::success)
                .orElseGet(() -> AgentResponseSupport.failed("artifact not found"));
    }

    @GetMapping("/artifacts/{artifactId}/versions")
    public Response<List<AgentArtifactVersionDTO>> listVersions(@PathVariable("artifactId") String artifactId) {
        return AgentResponseSupport.success(agentQueryFacade.listArtifactVersions(artifactId).stream()
                .map(AgentApiMapper::toArtifactVersion)
                .toList());
    }
}
