package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolApprovalDecisionStatusEnumVO;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalDecisionResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolIntentVO;
import yhx.com.domain.agent.service.tool.ToolApprovalService;
import com.alibaba.fastjson.JSON;

import java.util.Map;
import java.util.function.Supplier;

public class ToolApprovalPendingInputHandler implements PendingInputContinuationHandler {

    public static final String HANDLER_CODE = "TOOL_APPROVAL";

    private final Supplier<ToolApprovalService> toolApprovalServiceSupplier;
    private final RuntimeUserClarificationRecorder clarificationRecorder = new RuntimeUserClarificationRecorder();

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
        String payloadFailure = validateSourcePayload(checkpoint, context, checkpointPayload, approvalKey);
        if (payloadFailure != null) {
            return failed(context, payloadFailure);
        }
        ToolApprovalService toolApprovalService = toolApprovalServiceSupplier.get();
        ToolApprovalEntity approval = toolApprovalService == null ? null
                : toolApprovalService.findApprovalByApprovalKey(approvalKey).orElse(null);
        ToolCallEntity toolCall = toolApprovalService == null ? null
                : toolApprovalService.findToolCall(ContinuationCheckpointSupport.stringValue(
                        checkpointPayload, "toolCallId")).orElse(null);
        String approvalFailure = validateApprovalIdentity(
                context, checkpointPayload, approval, toolCall, toolApprovalService != null);
        if (approvalFailure != null) {
            return failed(context, approvalFailure);
        }
        ToolApprovalDecisionResultVO persistedDecision = recordDecision(toolApprovalService, answer, approvalKey);
        if (answer == null || answer.getStatus() == UserAnswerStatusEnumVO.CANCELLED) {
            if (childApproval(checkpointPayload)) {
                return resumeChild(context, answer, checkpointPayload, "User cancelled delegated tool approval.");
            }
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
            if (childApproval(checkpointPayload)) {
                return resumeChild(context, answer, checkpointPayload, "Delegated tool approval accepted.");
            }
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
        if (childApproval(checkpointPayload)) {
            return resumeChild(context, answer, checkpointPayload, "Delegated tool approval rejected by user.");
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
        Object toolIntent = checkpointPayload == null ? null : checkpointPayload.get("toolIntent");
        clarificationRecorder.append(context, UserClarificationVO.builder()
                .sourceComponent(HANDLER_CODE)
                .pendingId(answer == null ? null : answer.getPendingId())
                .question("Tool approval request")
                .answerType("TOOL_APPROVAL_REJECTED")
                .selectedOptionId(answer == null ? null : answer.getSelectedOptionId())
                .value(Map.of("decision", "REJECTED"))
                .freeText(answer == null ? null : answer.getFreeText())
                .metadata(toolIntent == null ? Map.of() : Map.of("toolIntent", toolIntent))
                .build());
    }

    private ToolApprovalDecisionResultVO recordDecision(ToolApprovalService toolApprovalService,
                                                        UserAnswerVO answer,
                                                        String approvalKey) {
        if (toolApprovalService == null || approvalKey == null || approvalKey.isBlank()) {
            return null;
        }
        return toolApprovalService.handleUserDecisionByApprovalKey(answer, approvalKey);
    }

    private String validateSourcePayload(ContinuationCheckpointVO checkpoint,
                                         RuntimeExecutionContext context,
                                         Map<String, Object> payload,
                                         String approvalKey) {
        if (checkpoint == null || context == null || checkpoint.getRelatedRunId() == null
                || !checkpoint.getRelatedRunId().equals(context.getRunId())) {
            return "Tool approval checkpoint Run identity is invalid.";
        }
        if (approvalKey == null || approvalKey.isBlank()) {
            return "Tool approval checkpoint is missing approvalKey.";
        }
        if (ContinuationCheckpointSupport.stringValue(payload, "toolCallId") == null
                || ContinuationCheckpointSupport.stringValue(payload, "argumentsHash") == null
                || ContinuationCheckpointSupport.stringValue(payload, "capabilityCode") == null
                || ContinuationCheckpointSupport.stringValue(payload, "mcpServerCode") == null
                || ContinuationCheckpointSupport.stringValue(payload, "toolName") == null
                || payload.get("toolIntent") == null) {
            return "Tool approval checkpoint is missing tool identity or arguments hash.";
        }
        ToolIntentVO toolIntent = JSON.parseObject(JSON.toJSONString(payload.get("toolIntent")), ToolIntentVO.class);
        if (toolIntent == null
                || !same(ContinuationCheckpointSupport.stringValue(payload, "capabilityCode"), toolIntent.getCapabilityCode())
                || !same(ContinuationCheckpointSupport.stringValue(payload, "mcpServerCode"), toolIntent.getMcpServerCode())
                || !same(ContinuationCheckpointSupport.stringValue(payload, "toolName"), toolIntent.getToolName())) {
            return "Tool approval checkpoint tool identity does not match toolIntent.";
        }
        return null;
    }

    private String validateApprovalIdentity(RuntimeExecutionContext context,
                                            Map<String, Object> payload,
                                            ToolApprovalEntity approval,
                                            ToolCallEntity toolCall,
                                            boolean persistenceAvailable) {
        if (!persistenceAvailable) {
            return null;
        }
        if (approval == null) {
            return "Tool approval record is missing.";
        }
        if (approval.getStatus() != ToolApprovalStatusEnumVO.PENDING) {
            return "Tool approval record is already resolved.";
        }
        String approvalRunId = firstNonBlank(ContinuationCheckpointSupport.stringValue(payload, "approvalRunId"),
                context.getRunId());
        if (!approvalRunId.equals(approval.getRunId())
                || !ContinuationCheckpointSupport.stringValue(payload, "toolCallId").equals(approval.getToolCallId())
                || !ContinuationCheckpointSupport.stringValue(payload, "argumentsHash").equals(approval.getArgumentsHash())) {
            return "Tool approval record does not match checkpoint identity.";
        }
        if (toolCall == null) {
            return "Tool approval persisted tool call is missing.";
        }
        if (toolCall.getStatus() != ToolCallStatusEnumVO.APPROVAL_PENDING) {
            return "Tool approval persisted tool call is not approval-pending.";
        }
        if (!approvalRunId.equals(toolCall.getRunId())
                || !ContinuationCheckpointSupport.stringValue(payload, "toolCallId").equals(toolCall.getToolCallId())
                || !ContinuationCheckpointSupport.stringValue(payload, "mcpServerCode").equals(toolCall.getMcpServerName())
                || !ContinuationCheckpointSupport.stringValue(payload, "toolName").equals(toolCall.getToolName())) {
            return "Tool approval checkpoint does not match the persisted tool call identity.";
        }
        return null;
    }

    private boolean childApproval(Map<String, Object> payload) {
        return !isBlank(ContinuationCheckpointSupport.stringValue(payload, "parentRunId"))
                && !isBlank(ContinuationCheckpointSupport.stringValue(payload, "childRunId"))
                && !isBlank(ContinuationCheckpointSupport.stringValue(payload, "taskId"));
    }

    private RuntimeStepResult resumeChild(RuntimeExecutionContext context,
                                          UserAnswerVO answer,
                                          Map<String, Object> payload,
                                          String message) {
        String parentRunId = ContinuationCheckpointSupport.stringValue(payload, "parentRunId");
        if (context == null || isBlank(parentRunId) || !parentRunId.equals(context.getRunId())) {
            return failed(context, "Delegated tool approval parent Run identity is invalid.");
        }
        if (context.getRuntimeFacts() != null) {
            context.getRuntimeFacts().put("childAgentUserAnswer", answer);
            context.getRuntimeFacts().put("resumeChildRunId",
                    ContinuationCheckpointSupport.stringValue(payload, "childRunId"));
            context.getRuntimeFacts().put("resumeChildTaskId",
                    ContinuationCheckpointSupport.stringValue(payload, "taskId"));
            context.getRuntimeFacts().put("resumeParentRunId", parentRunId);
        }
        return RuntimeStepResult.builder()
                .runId(parentRunId)
                .sessionId(context.getSessionId())
                .status(RuntimeStepStatusEnumVO.WAITING_CHILDREN)
                .nextRunStatus(RunStatusEnumVO.WAITING_CHILDREN)
                .nextPhase(RuntimePhaseEnumVO.WAITING_CHILDREN)
                .message(message)
                .build();
    }

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean same(String left, String right) {
        return left != null && left.equals(right);
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
