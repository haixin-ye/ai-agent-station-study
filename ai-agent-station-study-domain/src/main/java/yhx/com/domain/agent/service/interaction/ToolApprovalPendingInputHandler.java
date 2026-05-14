package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;

import java.util.Map;

public class ToolApprovalPendingInputHandler implements PendingInputContinuationHandler {

    public static final String HANDLER_CODE = "TOOL_APPROVAL";

    @Override
    public String handlerCode() {
        return HANDLER_CODE;
    }

    @Override
    public RuntimeStepResult handle(UserAnswerVO answer, ContinuationCheckpointVO checkpoint, RuntimeExecutionContext context) {
        if (answer == null || answer.getStatus() == UserAnswerStatusEnumVO.CANCELLED) {
            return RuntimeStepResult.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.CANCELLED)
                    .nextRunStatus(RunStatusEnumVO.CANCELLED)
                    .nextPhase(RuntimePhaseEnumVO.CANCELLED)
                    .message("User cancelled tool approval.")
                    .build();
        }
        if (!(answer.getValue() instanceof Map<?, ?> decision)) {
            return failed(context, "Tool approval answer is malformed.");
        }
        if ("APPROVED".equals(String.valueOf(decision.get("decision")))) {
            if (context.getRuntimeFacts() != null) {
                context.getRuntimeFacts().put("toolApproval", answer);
            }
            return RuntimeStepResult.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.CONTINUE)
                    .nextRunStatus(RunStatusEnumVO.RUNNING)
                    .nextPhase(RuntimePhaseEnumVO.PREPARING_TOOL)
                    .message("Tool approval accepted.")
                    .build();
        }
        if (context.getRuntimeFacts() != null) {
            context.getRuntimeFacts().put("toolDenied", answer);
        }
        return RuntimeStepResult.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .status(RuntimeStepStatusEnumVO.CONTINUE)
                .nextRunStatus(RunStatusEnumVO.RUNNING)
                .nextPhase(RuntimePhaseEnumVO.PREPARING_CONTEXT)
                .message("Tool approval rejected.")
                .build();
    }

    private RuntimeStepResult failed(RuntimeExecutionContext context, String message) {
        return RuntimeStepResult.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .status(RuntimeStepStatusEnumVO.FAILED)
                .nextRunStatus(RunStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .message(message)
                .build();
    }
}
