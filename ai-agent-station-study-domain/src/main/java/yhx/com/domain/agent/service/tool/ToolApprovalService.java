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
import yhx.com.domain.agent.adapter.transaction.IInteractionTransactionExecutor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Optional;

public class ToolApprovalService {

    private final IToolRepository toolRepository;
    private final IPayloadRepository payloadRepository;
    private final UserInteractionManager userInteractionManager;
    private final IInteractionTransactionExecutor transactionExecutor;

    public ToolApprovalService(IToolRepository toolRepository,
                               IPayloadRepository payloadRepository,
                               UserInteractionManager userInteractionManager) {
        this(toolRepository, payloadRepository, userInteractionManager, null);
    }

    public ToolApprovalService(IToolRepository toolRepository,
                               IPayloadRepository payloadRepository,
                               UserInteractionManager userInteractionManager,
                               IInteractionTransactionExecutor transactionExecutor) {
        this.toolRepository = toolRepository;
        this.payloadRepository = payloadRepository;
        this.userInteractionManager = userInteractionManager;
        this.transactionExecutor = transactionExecutor;
    }

    public ToolApprovalDecisionResultVO ensureApproval(ToolApprovalDecisionCommandVO command) {
        if (transactionExecutor == null) {
            return ensureApprovalInternal(command);
        }
        try {
            return transactionExecutor.execute(() -> ensureApprovalInternal(command));
        } catch (RuntimeException e) {
            return ToolApprovalDecisionResultVO.builder()
                    .status(ToolApprovalDecisionStatusEnumVO.DENIED)
                    .failureCode("TOOL_APPROVAL_PAUSE_FAILED")
                    .message("Tool approval could not be persisted atomically: " + e.getMessage())
                    .build();
        }
    }

    private ToolApprovalDecisionResultVO ensureApprovalInternal(ToolApprovalDecisionCommandVO command) {
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
                .runtimeContext(command.getRuntimeContext())
                .continuation(ContinuationCheckpointVO.builder()
                        .handler(ToolApprovalPendingInputHandler.HANDLER_CODE)
                        .resumePhase(RuntimePhaseEnumVO.PREPARING_TOOL)
                        .sourceComponent("ToolApprovalService")
                        .relatedRunId(command.getRunId())
                        .relatedLoopIndex(command.getRuntimeContext() == null ? null : command.getRuntimeContext().getLoopIndex())
                        .expectedAnswerValueType("OPTION")
                        .payload(approvalCheckpointPayload(command, approvalId))
                        .build())
                .build());
        if (pending == null || !Boolean.TRUE.equals(pending.getCreated())) {
            throw new IllegalStateException(pending == null
                    ? "PendingInput creation returned null."
                    : pending.getFailureMessage());
        }
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
        if (approval.getStatus() != ToolApprovalStatusEnumVO.PENDING) {
            return ToolApprovalDecisionResultVO.builder()
                    .status(ToolApprovalDecisionStatusEnumVO.DENIED)
                    .approval(approval)
                    .failureCode("TOOL_APPROVAL_ALREADY_RESOLVED")
                    .message("Tool approval was already resolved.")
                    .build();
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
                .question(approvalQuestion(command))
                .inputMode("SINGLE_CHOICE")
                .allowFreeText(false)
                .options(List.of(
                        Map.of("id", "approve", "label", "Approve", "value", Map.of("decision", "APPROVED", "approvalKey", command.getApprovalKey())),
                        Map.of("id", "reject", "label", "Reject", "value", Map.of("decision", "REJECTED", "approvalKey", command.getApprovalKey()))
                ))
                .build();
    }

    private String approvalQuestion(ToolApprovalDecisionCommandVO command) {
        String toolName = toolName(command);
        List<String> lines = new ArrayList<>();
        lines.add("是否允许调用工具 " + toolName + "?");
        if (command != null && command.getToolIntent() != null) {
            String goal = command.getToolIntent().getGoal();
            if (goal != null && !goal.isBlank()) {
                lines.add("目标: " + compact(goal, 180));
            }
            lines.addAll(argumentSummary(command.getToolIntent().getArguments()));
        }
        return String.join("\n", lines);
    }

    private String toolName(ToolApprovalDecisionCommandVO command) {
        if (command != null && command.getToolIntent() != null
                && command.getToolIntent().getToolName() != null
                && !command.getToolIntent().getToolName().isBlank()) {
            return command.getToolIntent().getToolName();
        }
        if (command != null && command.getCapability() != null
                && command.getCapability().getCapabilityCode() != null
                && !command.getCapability().getCapabilityCode().isBlank()) {
            return command.getCapability().getCapabilityCode();
        }
        return "unknown";
    }

    private List<String> argumentSummary(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        addKnownArgument(lines, arguments, "path", "路径");
        addKnownArgument(lines, arguments, "file", "文件");
        addKnownArgument(lines, arguments, "directory", "目录");
        addKnownArgument(lines, arguments, "pattern", "匹配模式");
        addKnownArgument(lines, arguments, "query", "查询");
        addKnownArgument(lines, arguments, "command", "命令");
        Object content = arguments.get("content");
        if (content != null) {
            String text = String.valueOf(content);
            lines.add("内容预览: " + compact(text, 80) + " (" + text.length() + " chars)");
        }

        Map<String, Object> remaining = new LinkedHashMap<>(arguments);
        remaining.keySet().removeAll(List.of("path", "file", "directory", "pattern", "query", "command", "content"));
        if (!remaining.isEmpty()) {
            StringJoiner joiner = new StringJoiner(", ");
            remaining.forEach((key, value) -> joiner.add(key + "=" + compact(String.valueOf(value), 60)));
            lines.add("其他参数: " + compact(joiner.toString(), 180));
        }
        return lines;
    }

    private void addKnownArgument(List<String> lines, Map<String, Object> arguments, String key, String label) {
        Object value = arguments.get(key);
        if (value != null) {
            lines.add(label + ": " + compact(String.valueOf(value), 180));
        }
    }

    private String compact(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        if (compacted.length() <= maxChars) {
            return compacted;
        }
        return compacted.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolIntentPayload(ToolApprovalDecisionCommandVO command) {
        Map<String, Object> payload = command == null || command.getToolIntent() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(JSON.parseObject(JSON.toJSONString(command.getToolIntent()), Map.class));
        if (command != null && command.getCapability() != null) {
            payload.put("capabilityCode", command.getCapability().getCapabilityCode());
        }
        if (command != null && command.getToolSpec() != null) {
            payload.put("mcpServerCode", command.getToolSpec().getMcpServerCode());
            payload.put("toolName", command.getToolSpec().getToolName());
        }
        return payload;
    }

    public Optional<ToolApprovalEntity> findApprovalByApprovalKey(String approvalKey) {
        if (approvalKey == null || approvalKey.isBlank()) {
            return Optional.empty();
        }
        return toolRepository.findApprovalByApprovalKey(approvalKey);
    }

    private Map<String, Object> approvalCheckpointPayload(ToolApprovalDecisionCommandVO command, String approvalId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalId", approvalId);
        payload.put("approvalKey", command.getApprovalKey());
        payload.put("toolCallId", command.getToolCallId());
        payload.put("argumentsHash", command.getArgumentsHash());
        payload.put("toolIntent", toolIntentPayload(command));
        if (command.getCapability() != null) {
            payload.put("capabilityCode", command.getCapability().getCapabilityCode());
        }
        if (command.getToolSpec() != null) {
            payload.put("mcpServerCode", command.getToolSpec().getMcpServerCode());
            payload.put("toolName", command.getToolSpec().getToolName());
        }
        return payload;
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
