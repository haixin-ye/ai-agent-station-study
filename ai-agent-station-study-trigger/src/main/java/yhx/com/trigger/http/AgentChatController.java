package yhx.com.trigger.http;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import yhx.com.api.dto.agent.AgentChatRequestDTO;
import yhx.com.api.dto.agent.AgentChatResponseDTO;
import yhx.com.api.dto.agent.AgentMessageDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.api.AgentQueryFacade;
import yhx.com.domain.agent.service.api.AgentRuntimeFacade;
import yhx.com.trigger.http.support.AgentApiMapper;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@CrossOrigin("*")
@RequestMapping("/agent")
@Slf4j
public class AgentChatController {

    @Resource
    private AgentRuntimeFacade agentRuntimeFacade;

    @Resource
    private AgentQueryFacade agentQueryFacade;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @PostMapping("/chat")
    public Response<AgentChatResponseDTO> chat(@RequestBody AgentChatRequestDTO request) {
        try {
            String runId = "run-" + UUID.randomUUID();
            String sessionId = firstNonBlank(request == null ? null : request.getSessionId(), "sess-" + UUID.randomUUID());
            threadPoolExecutor.execute(() -> {
                try {
                    RuntimeStepResult result = agentRuntimeFacade.startWithRunId(runId,
                            sessionId,
                            request == null ? null : request.getAgentId(),
                            request == null ? null : request.getUserId(),
                            request == null ? null : request.getContent(),
                            request == null ? null : request.getInputType(),
                            request == null ? null : request.getMetadata());
                    log.info("[AutoAgent][chat-async-completed] runId={}, sessionId={}, status={}",
                            runId, sessionId, result == null ? null : result.getStatus());
                } catch (Exception e) {
                    log.error("[AutoAgent][chat-async-error] runId={}, sessionId={}", runId, sessionId, e);
                }
            });
            return AgentResponseSupport.success(AgentChatResponseDTO.builder()
                    .runId(runId)
                    .sessionId(sessionId)
                    .userMessageId(null)
                    .status("RUNNING")
                    .build());
        } catch (Exception e) {
            log.error("[AutoAgent][chat-error] sessionId={}, agentId={}, userId={}, message={}",
                    request == null ? null : request.getSessionId(),
                    request == null ? null : request.getAgentId(),
                    request == null ? null : request.getUserId(),
                    request == null ? null : request.getContent(),
                    e);
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Response<List<AgentMessageDTO>> listMessages(@PathVariable String sessionId,
                                                        @RequestParam(defaultValue = "50") int limit) {
        return AgentResponseSupport.success(agentQueryFacade.listVisibleMessages(sessionId, limit).stream()
                .map(message -> AgentApiMapper.toMessage(message, agentQueryFacade))
                .toList());
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
