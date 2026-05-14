package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolVerificationEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IToolRepository {

    String createToolCall(ToolCallEntity toolCall);

    void updateToolCallStatus(String toolCallId, ToolCallStatusEnumVO status);

    void saveToolReceipt(String toolCallId, String argumentsRef, String receiptRef);

    String saveApproval(ToolApprovalEntity approval);

    Optional<ToolApprovalEntity> findPendingApproval(String runId);

    Optional<ToolApprovalEntity> findApprovalByApprovalKey(String approvalKey);

    void markApprovalApproved(String approvalId, String userAnswerRef, LocalDateTime decidedAt);

    void markApprovalRejected(String approvalId, String userAnswerRef, LocalDateTime decidedAt);

    void markApprovalCancelled(String approvalId, String userAnswerRef, LocalDateTime decidedAt);

    void markApprovalExpired(String approvalId, LocalDateTime decidedAt);

    String saveToolVerification(ToolVerificationEntity verification);
}
