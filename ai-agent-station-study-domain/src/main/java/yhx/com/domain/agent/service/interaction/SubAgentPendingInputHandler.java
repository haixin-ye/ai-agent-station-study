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
        String taskId = ContinuationCheckpointSupport.stringValue(payload, "taskId");
        if (context == null
                || isBlank(parentRunId)
                || !parentRunId.equals(context.getRunId())
                || checkpoint == null
                || !parentRunId.equals(checkpoint.getRelatedRunId())
                || isBlank(childRunId)
                || isBlank(taskId)) {
            return failed(context, "Subagent continuation relation is incomplete or mismatched.");
        }
        if (context != null && context.getRuntimeFacts() != null) {
            context.getRuntimeFacts().put("childAgentUserAnswer", answer);
            context.getRuntimeFacts().put("resumeChildRunId", childRunId);
            context.getRuntimeFacts().put("resumeChildTaskId", taskId);
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private RuntimeStepResult failed(RuntimeExecutionContext context, String message) {
        return RuntimeStepResult.builder()
                .runId(context == null ? null : context.getRunId())
                .sessionId(context == null ? null : context.getSessionId())
                .status(RuntimeStepStatusEnumVO.FAILED)
                .nextRunStatus(RunStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .message(message)
                .build();
    }
}
