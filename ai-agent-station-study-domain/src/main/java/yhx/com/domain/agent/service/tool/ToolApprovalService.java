package yhx.com.domain.agent.service.tool;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolApprovalDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateResult;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalDecisionCommandVO;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalDecisionResultVO;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ToolApprovalService {

    private final IToolRepository toolRepository;
    private final IPayloadRepository payloadRepository;
    private final UserInteractionManager userInteractionManager;

    public ToolApprovalService(IToolRepository toolRepository,
                               IPayloadRepository payloadRepository,
                               UserInteractionManager userInteractionManager) {
        this.toolRepository = toolRepository;
        this.payloadRepository = payloadRepository;
        this.userInteractionManager = userInteractionManager;
    }

    public ToolApprovalDecisionResultVO ensureApproval(ToolApprovalDecisionCommandVO command) {
        if (command.getPermissionDecision() == null
                || command.getPermissionDecision().getStatus() == PermissionDecisionStatusEnumVO.ALLOW) {
            return ToolApprovalDecisionResultVO.builder().status(ToolApprovalDecisionStatusEnumVO.APPROVED).build();
        }
        if (command.getPermissionDecision().getStatus() == PermissionDecisionStatusEnumVO.DENY) {
            return ToolApprovalDecisionResultVO.builder()
                    .status(ToolApprovalDecisionStatusEnumVO.DENIED)
                    .failureCode(command.getPermissionDecision().getFailureCode())
                    .message(command.getPermissionDecision().getReason())
                    .build();
        }
        ToolApprovalEntity existing = toolRepository.findApprovalByApprovalKey(command.getApprovalKey()).orElse(null);
        if (existing != null) {
            return fromExisting(existing);
        }
        AskUserRequestVO request = approvalRequest(command);
        ToolApprovalEntity approval = ToolApprovalEntity.builder()
                .approvalKey(command.getApprovalKey())
                .runId(command.getRunId())
                .toolCallId(command.getToolCallId())
                .status(ToolApprovalStatusEnumVO.PENDING)
                .permissionMode(command.getCapability().getPermissionMode().code())
                .argumentsHash(command.getArgumentsHash())
                .optionsRef(savePayload(request.getOptions()))
                .createdAt(LocalDateTime.now())
                .build();
        String approvalId = toolRepository.saveApproval(approval);
        approval.setApprovalId(approvalId);
        PendingInputCreateResult pending = userInteractionManager.createPendingInput(PendingInputCreateCommand.builder()
                .runId(command.getRunId())
                .sessionId(command.getSessionId())
                .sourceComponent("ToolApprovalService")
                .pendingType(PendingInputTypeEnumVO.TOOL_APPROVAL.code())
                .askUserRequest(request)
                .continuation(ContinuationCheckpointVO.builder()
                        .handler(ToolApprovalPendingInputHandler.HANDLER_CODE)
                        .resumePhase(RuntimePhaseEnumVO.PREPARING_TOOL)
                        .sourceComponent("ToolApprovalService")
                        .relatedRunId(command.getRunId())
                        .payload(Map.of(
                                "approvalId", approvalId,
                                "approvalKey", command.getApprovalKey(),
                                "toolCallId", command.getToolCallId(),
                                "toolIntent", toolIntentPayload(command)))
                        .build())
                .build());
        return ToolApprovalDecisionResultVO.builder()
                .status(ToolApprovalDecisionStatusEnumVO.PENDING)
                .approval(approval)
                .pendingInputId(pending.getPendingInputId())
                .askUserRequest(request)
                .message("Tool approval is waiting for user decision.")
                .build();
    }

    public ToolApprovalDecisionResultVO handleUserDecision(UserAnswerVO answer, ToolApprovalEntity approval) {
        if (approval == null) {
            return ToolApprovalDecisionResultVO.builder().status(ToolApprovalDecisionStatusEnumVO.DENIED).failureCode("TOOL_APPROVAL_REQUIRED").message("Approval record is missing.").build();
        }
        if (answer == null || answer.getAnswerType() == UserAnswerTypeEnumVO.CANCEL) {
            toolRepository.markApprovalCancelled(approval.getApprovalId(), null, LocalDateTime.now());
            return ToolApprovalDecisionResultVO.builder().status(ToolApprovalDecisionStatusEnumVO.CANCELLED).approval(approval).message("Tool approval cancelled.").build();
        }
        String answerRef = savePayload(answer);
        if (answer.getAnswerType() == UserAnswerTypeEnumVO.FREE_TEXT) {
            toolRepository.markApprovalRejected(approval.getApprovalId(), answerRef, LocalDateTime.now());
            return ToolApprovalDecisionResultVO.builder().status(ToolApprovalDecisionStatusEnumVO.REJECTED).approval(approval).failureCode("TOOL_PERMISSION_DENIED").message("Free text cannot approve tool execution.").build();
        }
        if (answer.getValue() instanceof Map<?, ?> decision && "APPROVED".equals(String.valueOf(decision.get("decision")))) {
            toolRepository.markApprovalApproved(approval.getApprovalId(), answerRef, LocalDateTime.now());
            approval.setStatus(ToolApprovalStatusEnumVO.APPROVED);
            return ToolApprovalDecisionResultVO.builder().status(ToolApprovalDecisionStatusEnumVO.APPROVED).approval(approval).message("Tool approved.").build();
        }
        toolRepository.markApprovalRejected(approval.getApprovalId(), answerRef, LocalDateTime.now());
        approval.setStatus(ToolApprovalStatusEnumVO.REJECTED);
        return ToolApprovalDecisionResultVO.builder().status(ToolApprovalDecisionStatusEnumVO.REJECTED).approval(approval).failureCode("TOOL_PERMISSION_DENIED").message("Tool rejected.").build();
    }

    public ToolApprovalDecisionResultVO handleUserDecisionByApprovalKey(UserAnswerVO answer, String approvalKey) {
        if (approvalKey == null || approvalKey.isBlank()) {
            return ToolApprovalDecisionResultVO.builder()
                    .status(ToolApprovalDecisionStatusEnumVO.DENIED)
                    .failureCode("TOOL_APPROVAL_REQUIRED")
                    .message("Approval key is missing.")
                    .build();
        }
        ToolApprovalEntity approval = toolRepository.findApprovalByApprovalKey(approvalKey).orElse(null);
        return handleUserDecision(answer, approval);
    }

    private ToolApprovalDecisionResultVO fromExisting(ToolApprovalEntity approval) {
        if (approval.getStatus() == ToolApprovalStatusEnumVO.APPROVED) {
            return ToolApprovalDecisionResultVO.builder().status(ToolApprovalDecisionStatusEnumVO.APPROVED).approval(approval).build();
        }
        if (approval.getStatus() == ToolApprovalStatusEnumVO.PENDING) {
            return ToolApprovalDecisionResultVO.builder().status(ToolApprovalDecisionStatusEnumVO.PENDING).approval(approval).message("Existing approval is pending.").build();
        }
        if (approval.getStatus() == ToolApprovalStatusEnumVO.CANCELLED) {
            return ToolApprovalDecisionResultVO.builder().status(ToolApprovalDecisionStatusEnumVO.CANCELLED).approval(approval).build();
        }
        return ToolApprovalDecisionResultVO.builder().status(ToolApprovalDecisionStatusEnumVO.REJECTED).approval(approval).failureCode("TOOL_PERMISSION_DENIED").build();
    }

    private AskUserRequestVO approvalRequest(ToolApprovalDecisionCommandVO command) {
        return AskUserRequestVO.builder()
                .question("Approve tool execution: " + command.getCapability().getCapabilityCode() + "?")
                .inputMode("SINGLE_CHOICE")
                .allowFreeText(false)
                .options(List.of(
                        Map.of("id", "approve", "label", "Approve", "value", Map.of("decision", "APPROVED", "approvalKey", command.getApprovalKey())),
                        Map.of("id", "reject", "label", "Reject", "value", Map.of("decision", "REJECTED", "approvalKey", command.getApprovalKey()))
                ))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolIntentPayload(ToolApprovalDecisionCommandVO command) {
        if (command == null || command.getToolIntent() == null) {
            return Map.of();
        }
        return JSON.parseObject(JSON.toJSONString(command.getToolIntent()), Map.class);
    }

    private String savePayload(Object value) {
        if (payloadRepository == null || value == null) {
            return null;
        }
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(value))
                .preview("tool-approval")
                .createdAt(LocalDateTime.now())
                .build());
    }
}
