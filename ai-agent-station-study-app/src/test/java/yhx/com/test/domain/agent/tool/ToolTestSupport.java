package yhx.com.test.domain.agent.tool;

import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunTranscriptRepository;
import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTranscriptEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolVerificationEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateResult;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class ToolTestSupport {

    static class Repository implements IToolRepository, IPayloadRepository, IArtifactRepository, IEvidenceRepository, IRunTranscriptRepository {
        final Map<String, ToolCallEntity> toolCalls = new LinkedHashMap<>();
        final Map<String, ToolApprovalEntity> approvals = new LinkedHashMap<>();
        final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        final Map<String, AgentArtifactEntity> artifacts = new LinkedHashMap<>();
        final List<AgentEvidenceEntity> evidence = new ArrayList<>();
        final List<ToolVerificationEntity> verifications = new ArrayList<>();
        final List<AgentRunTranscriptEntity> transcripts = new ArrayList<>();

        @Override
        public String createToolCall(ToolCallEntity toolCall) {
            if (toolCall.getToolCallId() == null) {
                toolCall.setToolCallId("tool-call-" + (toolCalls.size() + 1));
            }
            if (toolCall.getStatus() == null) {
                toolCall.setStatus(ToolCallStatusEnumVO.CREATED);
            }
            toolCalls.put(toolCall.getToolCallId(), toolCall);
            return toolCall.getToolCallId();
        }

        @Override
        public Optional<ToolCallEntity> findToolCall(String toolCallId) {
            return Optional.ofNullable(toolCalls.get(toolCallId));
        }

        @Override
        public void updateToolCallStatus(String toolCallId, ToolCallStatusEnumVO status) {
            toolCalls.get(toolCallId).setStatus(status);
        }

        @Override
        public void saveToolReceipt(String toolCallId, String argumentsRef, String receiptRef) {
            saveToolReceipt(toolCallId, argumentsRef, receiptRef, ToolCallStatusEnumVO.SUCCEEDED, null);
        }

        @Override
        public void saveToolReceipt(String toolCallId, String argumentsRef, String receiptRef, ToolCallStatusEnumVO status, String failureCode) {
            ToolCallEntity call = toolCalls.get(toolCallId);
            call.setArgumentsRef(argumentsRef);
            call.setReceiptRef(receiptRef);
            call.setStatus(status);
            call.setFailureCode(failureCode);
        }

        @Override
        public String saveApproval(ToolApprovalEntity approval) {
            if (approval.getApprovalId() == null) {
                approval.setApprovalId("approval-" + (approvals.size() + 1));
            }
            approvals.put(approval.getApprovalId(), approval);
            return approval.getApprovalId();
        }

        @Override
        public Optional<ToolApprovalEntity> findPendingApproval(String runId) {
            return approvals.values().stream()
                    .filter(item -> runId.equals(item.getRunId()) && item.getStatus() == ToolApprovalStatusEnumVO.PENDING)
                    .findFirst();
        }

        @Override
        public Optional<ToolApprovalEntity> findApprovalByApprovalKey(String approvalKey) {
            return approvals.values().stream().filter(item -> approvalKey.equals(item.getApprovalKey())).findFirst();
        }

        @Override
        public Optional<ToolApprovalEntity> findApprovalByToolCallId(String toolCallId) {
            return approvals.values().stream().filter(item -> toolCallId.equals(item.getToolCallId())).findFirst();
        }

        @Override
        public void markApprovalApproved(String approvalId, String userAnswerRef, LocalDateTime decidedAt) {
            markApproval(approvalId, ToolApprovalStatusEnumVO.APPROVED, userAnswerRef, decidedAt);
        }

        @Override
        public void markApprovalRejected(String approvalId, String userAnswerRef, LocalDateTime decidedAt) {
            markApproval(approvalId, ToolApprovalStatusEnumVO.REJECTED, userAnswerRef, decidedAt);
        }

        @Override
        public void markApprovalCancelled(String approvalId, String userAnswerRef, LocalDateTime decidedAt) {
            markApproval(approvalId, ToolApprovalStatusEnumVO.CANCELLED, userAnswerRef, decidedAt);
        }

        @Override
        public void markApprovalExpired(String approvalId, LocalDateTime decidedAt) {
            markApproval(approvalId, ToolApprovalStatusEnumVO.EXPIRED, null, decidedAt);
        }

        private void markApproval(String approvalId, ToolApprovalStatusEnumVO status, String userAnswerRef, LocalDateTime decidedAt) {
            ToolApprovalEntity approval = approvals.get(approvalId);
            approval.setStatus(status);
            approval.setUserAnswerRef(userAnswerRef);
            approval.setDecidedAt(decidedAt);
        }

        @Override
        public String saveToolVerification(ToolVerificationEntity verification) {
            if (verification.getVerificationId() == null) {
                verification.setVerificationId("verification-" + (verifications.size() + 1));
            }
            verifications.add(verification);
            return verification.getVerificationId();
        }

        @Override
        public String savePayload(AgentPayloadEntity payload) {
            if (payload.getPayloadId() == null) {
                payload.setPayloadId("payload-" + (payloads.size() + 1));
            }
            payloads.put(payload.getPayloadId(), payload);
            return payload.getPayloadId();
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            return Optional.ofNullable(payloads.get(payloadId));
        }

        @Override
        public String saveArtifact(AgentArtifactEntity artifact) {
            artifacts.put(artifact.getArtifactId(), artifact);
            return artifact.getArtifactId();
        }

        @Override
        public Optional<AgentArtifactEntity> findArtifact(String artifactId) {
            return Optional.ofNullable(artifacts.get(artifactId));
        }

        @Override
        public List<AgentArtifactEntity> findArtifactCandidates(String sessionId, String userInput, int limit) {
            return artifacts.values().stream().limit(limit).toList();
        }

        @Override
        public String saveEvidence(AgentEvidenceEntity item) {
            if (item.getEvidenceId() == null) {
                item.setEvidenceId("evidence-" + (evidence.size() + 1));
            }
            evidence.add(item);
            return item.getEvidenceId();
        }

        @Override
        public Optional<AgentEvidenceEntity> findEvidence(String evidenceId) {
            return evidence.stream().filter(item -> evidenceId.equals(item.getEvidenceId())).findFirst();
        }

        @Override
        public List<AgentEvidenceEntity> listRunEvidence(String runId) {
            return evidence.stream().filter(item -> runId.equals(item.getRunId())).toList();
        }

        @Override
        public String appendBlock(AgentRunTranscriptEntity block) {
            transcripts.add(block);
            return "block-" + transcripts.size();
        }

        @Override
        public List<AgentRunTranscriptEntity> listRunBlocks(String runId) {
            return transcripts.stream().filter(item -> runId.equals(item.getRunId())).toList();
        }

        @Override
        public List<AgentRunTranscriptEntity> listBlocksForCompaction(String runId, Long beforeSeq) {
            return listRunBlocks(runId);
        }

        @Override
        public String appendCompactionSummary(AgentRunTranscriptEntity block) {
            return appendBlock(block);
        }
    }

    static class FakeUserInteractionManager extends UserInteractionManager {
        PendingInputCreateCommand lastCommand;

        FakeUserInteractionManager() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public PendingInputCreateResult createPendingInput(PendingInputCreateCommand command) {
            this.lastCommand = command;
            return PendingInputCreateResult.builder()
                    .pendingInputId("pending-tool-1")
                    .runId(command.getRunId())
                    .created(true)
                    .build();
        }

        AskUserRequestVO askUserRequest() {
            return lastCommand == null ? null : lastCommand.getAskUserRequest();
        }
    }
}
