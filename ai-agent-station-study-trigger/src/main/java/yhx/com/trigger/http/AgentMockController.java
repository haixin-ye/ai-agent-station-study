package yhx.com.trigger.http;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import yhx.com.api.dto.agent.AgentMockScenarioDTO;
import yhx.com.api.dto.agent.AgentUserVisibleEventDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.service.api.AgentMockScenarioService;
import yhx.com.trigger.http.sse.SseEmitterRegistry;
import yhx.com.trigger.http.support.AgentApiMapper;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@CrossOrigin("*")
@RequestMapping("/mock/agent")
public class AgentMockController {

    @Resource
    private AgentMockScenarioService agentMockScenarioService;

    @Resource
    private SseEmitterRegistry sseEmitterRegistry;

    @GetMapping("/scenarios")
    public Response<List<AgentMockScenarioDTO>> listScenarios() {
        return AgentResponseSupport.success(agentMockScenarioService.listScenarios().stream()
                .map(AgentApiMapper::toMockScenario)
                .toList());
    }

    @PostMapping("/runs/{scenario}")
    public Response<Map<String, String>> createMockRun(@PathVariable String scenario) {
        return AgentResponseSupport.success(Map.of(
                "scenario", scenario,
                "runId", agentMockScenarioService.createMockRunId(scenario)
        ));
    }

    @GetMapping("/runs/{scenario}/events")
    public Response<List<AgentUserVisibleEventDTO>> listMockEvents(@PathVariable String scenario,
                                                                   @RequestParam(required = false) String runId) {
        String actualRunId = runId == null || runId.isBlank() ? "mock-" + scenario : runId;
        return AgentResponseSupport.success(agentMockScenarioService.buildEvents(scenario, actualRunId).stream()
                .map(AgentApiMapper::toMockEvent)
                .toList());
    }

    @GetMapping("/runs/{scenario}/events/stream")
    public SseEmitter streamMockEvents(@PathVariable String scenario,
                                       @RequestParam(required = false) String runId) {
        String actualRunId = runId == null || runId.isBlank() ? "mock-" + scenario : runId;
        String streamKey = "mock:" + actualRunId;
        SseEmitter emitter = sseEmitterRegistry.open(streamKey, 60_000L);
        CompletableFuture.runAsync(() -> {
            agentMockScenarioService.buildEvents(scenario, actualRunId).stream()
                    .map(AgentApiMapper::toMockEvent)
                    .forEach(event -> sseEmitterRegistry.send(streamKey, "agent-event", event.getEventId(), event));
            sseEmitterRegistry.complete(streamKey);
        });
        return emitter;
    }
}

