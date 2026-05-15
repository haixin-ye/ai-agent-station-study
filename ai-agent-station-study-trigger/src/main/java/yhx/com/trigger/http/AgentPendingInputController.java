package yhx.com.trigger.http;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import yhx.com.api.dto.agent.AgentPendingInputDTO;
import yhx.com.api.dto.agent.AgentUserInputRequestDTO;
import yhx.com.api.dto.agent.AgentUserInputResponseDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.api.AgentQueryFacade;
import yhx.com.domain.agent.service.api.AgentRuntimeFacade;
import yhx.com.trigger.http.support.AgentApiMapper;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.util.Optional;

@RestController
@CrossOrigin("*")
@RequestMapping("/agent/runs")
public class AgentPendingInputController {

    @Resource
    private AgentQueryFacade agentQueryFacade;

    @Resource
    private AgentRuntimeFacade agentRuntimeFacade;

    @GetMapping("/{runId}/pending-input")
    public Response<AgentPendingInputDTO> findPendingInput(@PathVariable String runId) {
        return agentQueryFacade.findActivePendingInput(runId)
                .map(pending -> AgentApiMapper.toPendingInput(pending, agentQueryFacade))
                .map(AgentResponseSupport::success)
                .orElseGet(() -> AgentResponseSupport.success(null));
    }

    @PostMapping("/{runId}/user-input")
    public Response<AgentUserInputResponseDTO> submitUserInput(@PathVariable String runId,
                                                               @RequestBody AgentUserInputRequestDTO request) {
        Optional<AgentPendingInputEntity> pending = agentQueryFacade.findPendingInput(request.getPendingId());
        if (pending.isEmpty() || !runId.equals(pending.get().getRunId())) {
            return AgentResponseSupport.failed("pending input not found");
        }
        if (rejectsFreeText(pending.get(), request)) {
            return AgentResponseSupport.failed("free text is not allowed for this pending input");
        }
        try {
            RuntimeStepResult result = agentRuntimeFacade.resume(runId,
                    request.getPendingId(),
                    request.getOptionId(),
                    request.getFreeText(),
                    request.getCancelled(),
                    request.getMetadata());
            return AgentResponseSupport.success(AgentUserInputResponseDTO.builder()
                    .runId(runId)
                    .pendingId(request.getPendingId())
                    .status(result.getNextRunStatus() == null ? null : result.getNextRunStatus().code())
                    .build());
        } catch (Exception e) {
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    private boolean rejectsFreeText(AgentPendingInputEntity pending, AgentUserInputRequestDTO request) {
        boolean hasFreeText = request.getFreeText() != null && !request.getFreeText().isBlank();
        if (!hasFreeText) {
            return false;
        }
        return "SINGLE_CHOICE".equals(pending.getInputMode())
                || "CONFIRM".equals(pending.getInputMode())
                || "TOOL_APPROVAL".equals(pending.getPendingType());
    }
}

