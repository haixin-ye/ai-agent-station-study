package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolVerificationEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.VerificationStatusEnumVO;
import yhx.com.infrastructure.dao.IAgentToolApprovalDao;
import yhx.com.infrastructure.dao.IAgentToolCallDao;
import yhx.com.infrastructure.dao.IAgentToolVerificationDao;
import yhx.com.infrastructure.dao.po.AgentToolApprovalPO;
import yhx.com.infrastructure.dao.po.AgentToolCallPO;
import yhx.com.infrastructure.dao.po.AgentToolVerificationPO;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ToolRepository implements IToolRepository {

    @Resource
    private IAgentToolCallDao agentToolCallDao;

    @Resource
    private IAgentToolApprovalDao agentToolApprovalDao;

    @Resource
    private IAgentToolVerificationDao agentToolVerificationDao;

    @Override
    public String createToolCall(ToolCallEntity toolCall) {
        if (toolCall.getToolCallId() == null || toolCall.getToolCallId().isBlank()) {
            toolCall.setToolCallId("tool-call-" + UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        if (toolCall.getCreatedAt() == null) {
            toolCall.setCreatedAt(now);
        }
        if (toolCall.getUpdatedAt() == null) {
            toolCall.setUpdatedAt(now);
        }
        if (toolCall.getStatus() == null) {
            toolCall.setStatus(ToolCallStatusEnumVO.CREATED);
        }
        agentToolCallDao.insert(toPO(toolCall));
        return toolCall.getToolCallId();
    }

    @Override
    public void updateToolCallStatus(String toolCallId, ToolCallStatusEnumVO status) {
        agentToolCallDao.updateStatus(toolCallId, status.code());
    }

    @Override
    public void saveToolReceipt(String toolCallId, String argumentsRef, String receiptRef) {
        agentToolCallDao.saveReceipt(toolCallId, argumentsRef, receiptRef);
    }

    @Override
    public String saveApproval(ToolApprovalEntity approval) {
        if (approval.getApprovalId() == null || approval.getApprovalId().isBlank()) {
            approval.setApprovalId("approval-" + UUID.randomUUID());
        }
        if (approval.getApprovalKey() == null || approval.getApprovalKey().isBlank()) {
            approval.setApprovalKey("approval-key-" + UUID.randomUUID());
        }
        if (approval.getCreatedAt() == null) {
            approval.setCreatedAt(LocalDateTime.now());
        }
        if (approval.getStatus() == null) {
            approval.setStatus(ToolApprovalStatusEnumVO.PENDING);
        }
        agentToolApprovalDao.insert(toPO(approval));
        return approval.getApprovalId();
    }

    @Override
    public Optional<ToolApprovalEntity> findPendingApproval(String runId) {
        return Optional.ofNullable(agentToolApprovalDao.queryPendingByRunId(runId)).map(this::toEntity);
    }

    @Override
    public Optional<ToolApprovalEntity> findApprovalByApprovalKey(String approvalKey) {
        return Optional.ofNullable(agentToolApprovalDao.queryByApprovalKey(approvalKey)).map(this::toEntity);
    }

    @Override
    public void markApprovalApproved(String approvalId, String userAnswerRef, LocalDateTime decidedAt) {
        agentToolApprovalDao.markDecision(approvalId, ToolApprovalStatusEnumVO.APPROVED.code(), userAnswerRef, decidedAt);
    }

    @Override
    public void markApprovalRejected(String approvalId, String userAnswerRef, LocalDateTime decidedAt) {
        agentToolApprovalDao.markDecision(approvalId, ToolApprovalStatusEnumVO.REJECTED.code(), userAnswerRef, decidedAt);
    }

    @Override
    public void markApprovalCancelled(String approvalId, String userAnswerRef, LocalDateTime decidedAt) {
        agentToolApprovalDao.markDecision(approvalId, ToolApprovalStatusEnumVO.CANCELLED.code(), userAnswerRef, decidedAt);
    }

    @Override
    public void markApprovalExpired(String approvalId, LocalDateTime decidedAt) {
        agentToolApprovalDao.markDecision(approvalId, ToolApprovalStatusEnumVO.EXPIRED.code(), null, decidedAt);
    }

    @Override
    public String saveToolVerification(ToolVerificationEntity verification) {
        if (verification.getVerificationId() == null || verification.getVerificationId().isBlank()) {
            verification.setVerificationId("tool-verification-" + UUID.randomUUID());
        }
        if (verification.getCreatedAt() == null) {
            verification.setCreatedAt(LocalDateTime.now());
        }
        if (verification.getStatus() == null) {
            verification.setStatus(VerificationStatusEnumVO.SKIPPED);
        }
        agentToolVerificationDao.insert(toPO(verification));
        return verification.getVerificationId();
    }

    private AgentToolCallPO toPO(ToolCallEntity entity) {
        return AgentToolCallPO.builder()
                .toolCallId(entity.getToolCallId())
                .toolInvocationId(entity.getToolInvocationId())
                .runId(entity.getRunId())
                .toolName(entity.getToolName())
                .mcpServerName(entity.getMcpServerName())
                .mcpTransportType(entity.getMcpTransportType())
                .status(entity.getStatus().code())
                .inputSchemaRef(entity.getInputSchemaRef())
                .intentRef(entity.getIntentRef())
                .argumentsRef(entity.getArgumentsRef())
                .receiptRef(entity.getReceiptRef())
                .failureCode(entity.getFailureCode())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentToolApprovalPO toPO(ToolApprovalEntity entity) {
        return AgentToolApprovalPO.builder()
                .approvalId(entity.getApprovalId())
                .approvalKey(entity.getApprovalKey())
                .runId(entity.getRunId())
                .toolCallId(entity.getToolCallId())
                .status(entity.getStatus().code())
                .permissionMode(entity.getPermissionMode())
                .argumentsHash(entity.getArgumentsHash())
                .optionsRef(entity.getOptionsRef())
                .userAnswerRef(entity.getUserAnswerRef())
                .createdAt(entity.getCreatedAt())
                .decidedAt(entity.getDecidedAt())
                .build();
    }

    private AgentToolVerificationPO toPO(ToolVerificationEntity entity) {
        return AgentToolVerificationPO.builder()
                .verificationId(entity.getVerificationId())
                .runId(entity.getRunId())
                .toolCallId(entity.getToolCallId())
                .status(entity.getStatus().code())
                .failureCode(entity.getFailureCode())
                .detailRef(entity.getDetailRef())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private ToolApprovalEntity toEntity(AgentToolApprovalPO po) {
        return ToolApprovalEntity.builder()
                .approvalId(po.getApprovalId())
                .approvalKey(po.getApprovalKey())
                .runId(po.getRunId())
                .toolCallId(po.getToolCallId())
                .status(ToolApprovalStatusEnumVO.ofCode(po.getStatus()).orElse(ToolApprovalStatusEnumVO.PENDING))
                .permissionMode(po.getPermissionMode())
                .argumentsHash(po.getArgumentsHash())
                .optionsRef(po.getOptionsRef())
                .userAnswerRef(po.getUserAnswerRef())
                .createdAt(po.getCreatedAt())
                .decidedAt(po.getDecidedAt())
                .build();
    }
}
