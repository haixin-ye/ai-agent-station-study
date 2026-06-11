package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;

import java.util.Map;

public class SubAgentPendingInputHandler implements PendingInputContinuationHandler {

    public static final String HANDLER_CODE = "GENERIC_SUB_AGENT";

    @Override
    public String handlerCode() {
        return HANDLER_CODE;
    }

    @Override
    public RuntimeStepResult handle(UserAnswerVO answer, ContinuationCheckpointVO checkpoint, RuntimeExecutionContext context) {
        Map<String, Object> payload = ContinuationCheckpointSupport.payload(checkpoint);
        String parentRunId = firstNonBlank(ContinuationCheckpointSupport.stringValue(payload, "parentRunId"),
                context == null ? null : context.getRunId());
        String childRunId = ContinuationCheckpointSupport.stringValue(payload, "childRunId");
        if (context != null && context.getRuntimeFacts() != null) {
            context.getRuntimeFacts().put("childAgentUserAnswer", answer);
            context.getRuntimeFacts().put("resumeChildRunId", childRunId);
            context.getRuntimeFacts().put("resumeChildTaskId", ContinuationCheckpointSupport.stringValue(payload, "taskId"));
            context.getRuntimeFacts().put("resumeParentRunId", parentRunId);
        }
        return RuntimeStepResult.builder()
                .runId(parentRunId)
                .sessionId(context == null ? null : context.getSessionId())
                .status(RuntimeStepStatusEnumVO.WAITING_CHILDREN)
                .nextRunStatus(RunStatusEnumVO.WAITING_CHILDREN)
                .nextPhase(ContinuationCheckpointSupport.resumePhase(checkpoint, RuntimePhaseEnumVO.WAITING_CHILDREN))
                .message("Generic subagent pending input resolved; parent remains waiting for child completion.")
                .build();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
