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

@RestController
@CrossOrigin("*")
@RequestMapping("/agent")
@Slf4j
public class AgentChatController {

    @Resource
    private AgentRuntimeFacade agentRuntimeFacade;

    @Resource
    private AgentQueryFacade agentQueryFacade;

    @PostMapping("/chat")
    public Response<AgentChatResponseDTO> chat(@RequestBody AgentChatRequestDTO request) {
        try {
            RuntimeStepResult result = agentRuntimeFacade.start(request.getSessionId(),
                    request.getAgentId(),
                    request.getUserId(),
                    request.getContent(),
                    request.getInputType(),
                    request.getMetadata());
            return AgentResponseSupport.success(AgentChatResponseDTO.builder()
                    .runId(result.getRunId())
                    .sessionId(result.getSessionId())
                    .userMessageId(null)
                    .status(result.getNextRunStatus() == null ? null : result.getNextRunStatus().code())
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
}
