package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PendingInputContinuationDispatcher {

    private final Map<String, PendingInputContinuationHandler> handlers = new LinkedHashMap<>();

    public PendingInputContinuationDispatcher(List<PendingInputContinuationHandler> handlers) {
        if (handlers != null) {
            handlers.forEach(handler -> this.handlers.put(handler.handlerCode(), handler));
        }
    }

    public RuntimeStepResult dispatch(UserAnswerVO answer, ContinuationCheckpointVO checkpoint, RuntimeExecutionContext context) {
        if (checkpoint == null || checkpoint.getHandler() == null) {
            return failed(context, "Missing continuation checkpoint.");
        }
        PendingInputContinuationHandler handler = handlers.get(checkpoint.getHandler());
        if (handler == null) {
            return failed(context, "Unknown continuation handler: " + checkpoint.getHandler());
        }
        return handler.handle(answer, checkpoint, context);
    }

    private RuntimeStepResult failed(RuntimeExecutionContext context, String message) {
        RuntimeSafeFailureVO failure = RuntimeSafeFailureVO.builder()
                .failureCode(RuntimeFailureCodeEnumVO.MISSING_ACTIVE_PENDING_INPUT)
                .phase(RuntimePhaseEnumVO.RESOLVING_USER_ANSWER)
                .userMessage("The task could not resume safely.")
                .developerMessage(message)
                .retryable(false)
                .build();
        return RuntimeStepResult.builder()
                .runId(context == null ? null : context.getRunId())
                .sessionId(context == null ? null : context.getSessionId())
                .status(RuntimeStepStatusEnumVO.FAILED)
                .nextRunStatus(RunStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .safeFailure(failure)
                .message(message)
                .build();
    }
}
