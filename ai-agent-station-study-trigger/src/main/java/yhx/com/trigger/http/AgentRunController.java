package yhx.com.trigger.http;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import yhx.com.api.dto.agent.AgentFinalResponseDTO;
import yhx.com.api.dto.agent.AgentRunDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.service.api.AgentQueryFacade;
import yhx.com.trigger.http.support.AgentApiMapper;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.util.Optional;

@RestController
@CrossOrigin("*")
@RequestMapping("/agent/runs")
public class AgentRunController {

    @Resource
    private AgentQueryFacade agentQueryFacade;

    @GetMapping("/{runId}")
    public Response<AgentRunDTO> findRun(@PathVariable("runId") String runId) {
        return agentQueryFacade.findRun(runId)
                .map(AgentApiMapper::toRun)
                .map(AgentResponseSupport::success)
                .orElseGet(() -> AgentResponseSupport.failed("run not found"));
    }

    @GetMapping("/{runId}/final")
    public Response<AgentFinalResponseDTO> findFinal(@PathVariable("runId") String runId) {
        Optional<AgentRunEntity> run = agentQueryFacade.findRun(runId);
        if (run.isEmpty()) {
            return AgentResponseSupport.failed("run not found");
        }
        AgentRunEntity runEntity = run.get();
        return AgentResponseSupport.success(AgentApiMapper.toFinal(runEntity,
                agentQueryFacade.findFinalAnswer(runId),
                agentQueryFacade.listSessionArtifacts(runEntity.getSessionId(), 20)));
    }
}
