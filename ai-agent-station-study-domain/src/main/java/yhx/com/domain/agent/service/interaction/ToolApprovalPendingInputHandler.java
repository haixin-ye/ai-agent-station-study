package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolApprovalDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalDecisionResultVO;
import yhx.com.domain.agent.service.tool.ToolApprovalService;

import java.util.Map;
import java.util.function.Supplier;

public class ToolApprovalPendingInputHandler implements PendingInputContinuationHandler {

    public static final String HANDLER_CODE = "TOOL_APPROVAL";

    private final Supplier<ToolApprovalService> toolApprovalServiceSupplier;

    public ToolApprovalPendingInputHandler() {
        this(() -> null);
    }

    public ToolApprovalPendingInputHandler(Supplier<ToolApprovalService> toolApprovalServiceSupplier) {
        this.toolApprovalServiceSupplier = toolApprovalServiceSupplier == null ? () -> null : toolApprovalServiceSupplier;
    }

    @Override
    public String handlerCode() {
        return HANDLER_CODE;
    }

    @Override
    public RuntimeStepResult handle(UserAnswerVO answer, ContinuationCheckpointVO checkpoint, RuntimeExecutionContext context) {
        Map<String, Object> checkpointPayload = ContinuationCheckpointSupport.payload(checkpoint);
        String approvalKey = ContinuationCheckpointSupport.stringValue(checkpointPayload, "approvalKey");
        ToolApprovalDecisionResultVO persistedDecision = recordDecision(answer, approvalKey);
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
        if ("APPROVED".equals(String.valueOf(decision.get("decision")))
                && (persistedDecision == null || persistedDecision.getStatus() == ToolApprovalDecisionStatusEnumVO.APPROVED)) {
            if (context.getRuntimeFacts() != null) {
                context.getRuntimeFacts().put("toolApproval", answer);
                Object toolIntent = checkpointPayload.get("toolIntent");
                if (toolIntent != null) {
                    context.getRuntimeFacts().put("resumeToolIntent", toolIntent);
                }
            }
            return RuntimeStepResult.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.CONTINUE)
                    .nextRunStatus(RunStatusEnumVO.RUNNING)
                    .nextPhase(ContinuationCheckpointSupport.resumePhase(checkpoint, RuntimePhaseEnumVO.PREPARING_TOOL))
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
                .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                .message("Tool approval rejected.")
                .build();
    }

    private ToolApprovalDecisionResultVO recordDecision(UserAnswerVO answer, String approvalKey) {
        ToolApprovalService toolApprovalService = toolApprovalServiceSupplier.get();
        if (toolApprovalService == null || approvalKey == null || approvalKey.isBlank()) {
            return null;
        }
        return toolApprovalService.handleUserDecisionByApprovalKey(answer, approvalKey);
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
