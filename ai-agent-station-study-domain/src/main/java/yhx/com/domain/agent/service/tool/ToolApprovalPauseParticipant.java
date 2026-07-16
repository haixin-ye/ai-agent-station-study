package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;
import yhx.com.domain.agent.service.interaction.PendingInputPauseParticipant;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

public class ToolApprovalPauseParticipant implements PendingInputPauseParticipant {

    private final IToolRepository toolRepository;

    public ToolApprovalPauseParticipant(IToolRepository toolRepository) {
        this.toolRepository = toolRepository;
    }

    @Override
    public boolean supports(String handlerCode) {
        return ToolApprovalPendingInputHandler.HANDLER_CODE.equals(handlerCode);
    }

    @Override
    public void beforePendingInputPersisted(PendingInputCreateCommand command) {
        Map<String, Object> payload = command == null || command.getContinuation() == null
                ? Map.of()
                : command.getContinuation().getPayload();
        String approvalId = value(payload, "approvalId");
        String approvalKey = value(payload, "approvalKey");
        String toolCallId = value(payload, "toolCallId");
        String argumentsHash = value(payload, "argumentsHash");
        String permissionMode = value(payload, "permissionMode");
        if (isBlank(approvalId) || isBlank(approvalKey) || isBlank(toolCallId)
                || isBlank(argumentsHash) || isBlank(permissionMode)) {
            throw new IllegalArgumentException("Tool approval pause payload is incomplete.");
        }
        ToolCallEntity toolCall = toolRepository.findToolCall(toolCallId)
                .orElseThrow(() -> new IllegalArgumentException("Tool approval ToolCall is missing."));
        if (!Objects.equals(command.getRunId(), toolCall.getRunId())) {
            throw new IllegalArgumentException("Tool approval ToolCall belongs to another Run.");
        }
        ToolApprovalEntity existing = toolRepository.findApprovalByApprovalKey(approvalKey).orElse(null);
        if (existing == null) {
            toolRepository.saveApproval(ToolApprovalEntity.builder()
                    .approvalId(approvalId)
                    .approvalKey(approvalKey)
                    .runId(command.getRunId())
                    .toolCallId(toolCallId)
                    .status(ToolApprovalStatusEnumVO.PENDING)
                    .permissionMode(permissionMode)
                    .argumentsHash(argumentsHash)
                    .createdAt(LocalDateTime.now())
                    .build());
        } else if (!Objects.equals(command.getRunId(), existing.getRunId())
                || !Objects.equals(toolCallId, existing.getToolCallId())
                || !Objects.equals(argumentsHash, existing.getArgumentsHash())
                || existing.getStatus() != ToolApprovalStatusEnumVO.PENDING) {
            throw new IllegalArgumentException("Existing tool approval does not match the pause intent.");
        }
        toolRepository.updateToolCallStatus(toolCallId, ToolCallStatusEnumVO.APPROVAL_PENDING);
    }

    private String value(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
