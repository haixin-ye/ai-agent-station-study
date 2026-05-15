package yhx.com.domain.agent.service.api;

import yhx.com.domain.agent.model.valobj.runtime.RuntimeResumeCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;

import java.util.Map;

public class AgentRuntimeFacade {

    private final AutoAgentRuntimeService autoAgentRuntimeService;

    public AgentRuntimeFacade(AutoAgentRuntimeService autoAgentRuntimeService) {
        this.autoAgentRuntimeService = autoAgentRuntimeService;
    }

    public RuntimeStepResult start(String sessionId, String agentId, String userId, String content, String inputType, Map<String, Object> metadata) {
        if (autoAgentRuntimeService == null) {
            throw new IllegalStateException("AutoAgent runtime service is not configured.");
        }
        return autoAgentRuntimeService.start(RuntimeStartCommand.builder()
                .sessionId(sessionId)
                .agentId(agentId)
                .userId(userId)
                .userInput(content)
                .inputType(inputType)
                .requestMetadata(metadata)
                .build());
    }

    public RuntimeStepResult resume(String runId, String pendingId, String optionId, String freeText, Boolean cancelled, Map<String, Object> metadata) {
        if (autoAgentRuntimeService == null) {
            throw new IllegalStateException("AutoAgent runtime service is not configured.");
        }
        return autoAgentRuntimeService.resume(RuntimeResumeCommand.builder()
                .runId(runId)
                .pendingId(pendingId)
                .selectedOptionId(optionId)
                .freeText(freeText)
                .cancelled(cancelled)
                .requestMetadata(metadata)
                .build());
    }
}
