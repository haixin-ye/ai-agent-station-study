package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolApprovalDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalDecisionResultVO;
import yhx.com.domain.agent.service.tool.ToolApprovalService;

import java.util.ArrayList;
import java.util.List;
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
            appendToolDeniedClarification(context, answer, checkpointPayload);
        }
        return RuntimeStepResult.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .status(RuntimeStepStatusEnumVO.CONTINUE)
                .nextRunStatus(RunStatusEnumVO.RUNNING)
                .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                .message("Tool approval rejected by user.")
                .build();
    }

    @SuppressWarnings("unchecked")
    private void appendToolDeniedClarification(RuntimeExecutionContext context, UserAnswerVO answer, Map<String, Object> checkpointPayload) {
        Object existing = context.getRuntimeFacts().get("userClarifications");
        List<UserClarificationVO> clarifications = existing instanceof List<?> list
                ? new ArrayList<>((List<UserClarificationVO>) list)
                : new ArrayList<>();
        Object toolIntent = checkpointPayload == null ? null : checkpointPayload.get("toolIntent");
        clarifications.add(UserClarificationVO.builder()
                .sourceComponent(HANDLER_CODE)
                .pendingId(answer == null ? null : answer.getPendingId())
                .question("Tool approval request")
                .answerType("TOOL_APPROVAL_REJECTED")
                .selectedOptionId(answer == null ? null : answer.getSelectedOptionId())
                .value(Map.of("decision", "REJECTED"))
                .freeText(answer == null ? null : answer.getFreeText())
                .metadata(toolIntent == null ? Map.of() : Map.of("toolIntent", toolIntent))
                .build());
        context.getRuntimeFacts().put("userClarifications", clarifications);
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
