package yhx.com.domain.agent.service.finalresponse;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunAuditEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.AuditTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.MessageRoleEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;
import yhx.com.domain.agent.service.memory.NoopTurnCompletionPublisher;
import yhx.com.domain.agent.service.memory.TurnCompletionPublisher;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FinalResponsePersistenceService {

    private final IPayloadRepository payloadRepository;
    private final IConversationRepository conversationRepository;
    private final IRunRepository runRepository;
    private final IEventTraceRepository eventTraceRepository;
    private final ITurnRepository turnRepository;
    private final TurnCompletionPublisher turnCompletionPublisher;

    public FinalResponsePersistenceService(IPayloadRepository payloadRepository,
                                           IConversationRepository conversationRepository,
                                           IRunRepository runRepository,
                                           IEventTraceRepository eventTraceRepository) {
        this(payloadRepository, conversationRepository, runRepository, eventTraceRepository, null, null);
    }

    public FinalResponsePersistenceService(IPayloadRepository payloadRepository,
                                           IConversationRepository conversationRepository,
                                           IRunRepository runRepository,
                                           IEventTraceRepository eventTraceRepository,
                                           ITurnRepository turnRepository,
                                           TurnCompletionPublisher turnCompletionPublisher) {
        this.payloadRepository = payloadRepository;
        this.conversationRepository = conversationRepository;
        this.runRepository = runRepository;
        this.eventTraceRepository = eventTraceRepository;
        this.turnRepository = turnRepository;
        this.turnCompletionPublisher = turnCompletionPublisher == null ? new NoopTurnCompletionPublisher() : turnCompletionPublisher;
    }

    public String saveCandidateDebugPayload(FinalDeliveryCommandVO command) {
        return savePayload(PayloadTypeEnumVO.DEBUG_TRACE, command == null ? null : command.getFinalAnswerCandidate(), "final-candidate");
    }

    public String saveGuardDetail(String runId, FinalResponseGuardResultVO guardResult) {
        String payloadRef = savePayload(PayloadTypeEnumVO.DEBUG_TRACE, guardResult, "final-guard");
        if (eventTraceRepository != null) {
            eventTraceRepository.appendTrace(AgentRunTraceEntity.builder()
                    .runId(runId)
                    .traceType(TraceTypeEnumVO.CONTRACT_VALIDATION)
                    .payloadRef(payloadRef)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
        return payloadRef;
    }

    public FinalResponseVO persistDelivered(FinalDeliveryCommandVO command, FinalResponseVO finalResponse) {
        String contentRef = savePayload(PayloadTypeEnumVO.TEXT, finalResponse.getContent(), "final-answer");
        String metadataRef = savePayload(PayloadTypeEnumVO.JSON, metadata(finalResponse), "final-message-metadata");
        String messageId = "message-" + UUID.randomUUID();
        conversationRepository.appendMessage(AgentMessageEntity.builder()
                .messageId(messageId)
                .sessionId(command.getSessionId())
                .runId(command.getRunId())
                .role(MessageRoleEnumVO.ASSISTANT)
                .contentRef(contentRef)
                .metadataRef(metadataRef)
                .visibleToUser(true)
                .createdAt(LocalDateTime.now())
                .build());
        String turnId = saveCompletedTurn(command, messageId, contentRef);
        runRepository.updateFinalAnswerRef(command.getRunId(), contentRef);
        runRepository.updateRunStatus(command.getRunId(), RunStatusEnumVO.COMPLETED, null);
        saveAudit(command.getRunId(), "FINAL_DELIVERED", "Final response persisted after guard pass.");
        if (turnId != null) {
            turnCompletionPublisher.onTurnCompleted(turnId);
        }
        finalResponse.setMessageId(messageId);
        finalResponse.setContentRef(contentRef);
        return finalResponse;
    }

    public void persistFailure(String runId, String failureCode, String summary) {
        if (runRepository != null) {
            runRepository.updateRunStatus(runId, RunStatusEnumVO.FAILED, failureCode);
        }
        saveAudit(runId, failureCode, summary);
    }

    private void saveAudit(String runId, String code, String summary) {
        if (eventTraceRepository == null) {
            return;
        }
        String payloadRef = savePayload(PayloadTypeEnumVO.JSON, Map.of("code", code, "summary", summary), "final-audit");
        eventTraceRepository.appendAudit(AgentRunAuditEntity.builder()
                .runId(runId)
                .auditType(AuditTypeEnumVO.FINAL_DELIVERED)
                .payloadRef(payloadRef)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Map<String, Object> metadata(FinalResponseVO response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("format", response.getFormat());
        metadata.put("createdAt", response.getCreatedAt());
        return metadata;
    }

    private String saveCompletedTurn(FinalDeliveryCommandVO command, String assistantMessageId, String assistantPayloadRef) {
        if (turnRepository == null || command == null || command.getUserMessageId() == null || command.getUserMessageId().isBlank()) {
            return null;
        }
        return turnRepository.saveCompletedTurn(AgentTurnEntity.builder()
                .sessionId(command.getSessionId())
                .runId(command.getRunId())
                .userId(command.getUserId())
                .agentId(command.getAgentId())
                .userMessageId(command.getUserMessageId())
                .assistantMessageId(assistantMessageId)
                .userPayloadRef(completedTurnUserPayloadRef(command))
                .assistantPayloadRef(assistantPayloadRef)
                .status("COMPLETED")
                .completedAt(LocalDateTime.now())
                .build());
    }

    private String completedTurnUserPayloadRef(FinalDeliveryCommandVO command) {
        String originalPayloadRef = findUserPayloadRef(command.getUserMessageId());
        if (!hasUserClarifications(command)) {
            return originalPayloadRef;
        }
        String content = originalUserContent(originalPayloadRef, command.getUserInput());
        String enhanced = completedTurnUserPayload(content, command.getUserClarifications());
        String enhancedPayloadRef = savePayload(PayloadTypeEnumVO.TEXT, enhanced, "completed-turn-user-context");
        return enhancedPayloadRef == null ? originalPayloadRef : enhancedPayloadRef;
    }

    private boolean hasUserClarifications(FinalDeliveryCommandVO command) {
        return command != null
                && command.getUserClarifications() != null
                && command.getUserClarifications().stream().anyMatch(this::hasClarificationContent);
    }

    private boolean hasClarificationContent(UserClarificationVO clarification) {
        return clarification != null
                && (!isBlank(clarification.getQuestion())
                || !isBlank(clarification.getFreeText())
                || clarification.getValue() != null
                || !isBlank(clarification.getSelectedOptionId()));
    }

    private String originalUserContent(String originalPayloadRef, String fallback) {
        if (payloadRepository != null && originalPayloadRef != null && !originalPayloadRef.isBlank()) {
            return payloadRepository.findContent(originalPayloadRef).orElse(fallback);
        }
        return fallback;
    }

    private String completedTurnUserPayload(String originalUserContent, List<UserClarificationVO> clarifications) {
        StringBuilder builder = new StringBuilder();
        builder.append("Original user question:\n");
        builder.append(isBlank(originalUserContent) ? "" : originalUserContent.trim());
        builder.append("\n\nUser clarification answers in this run:");
        int index = 1;
        for (UserClarificationVO clarification : clarifications) {
            if (!hasClarificationContent(clarification)) {
                continue;
            }
            builder.append("\n").append(index++).append(". Question: ");
            builder.append(isBlank(clarification.getQuestion()) ? "" : clarification.getQuestion().trim());
            builder.append("\n   Answer: ");
            builder.append(clarificationAnswer(clarification));
        }
        return builder.toString();
    }

    private String clarificationAnswer(UserClarificationVO clarification) {
        if (!isBlank(clarification.getFreeText())) {
            return clarification.getFreeText().trim();
        }
        if (clarification.getValue() != null) {
            return clarification.getValue() instanceof String text ? text.trim() : JSON.toJSONString(clarification.getValue());
        }
        if (!isBlank(clarification.getSelectedOptionId())) {
            return clarification.getSelectedOptionId().trim();
        }
        return "";
    }

    private String findUserPayloadRef(String userMessageId) {
        if (conversationRepository == null || userMessageId == null || userMessageId.isBlank()) {
            return null;
        }
        return conversationRepository.findMessageById(userMessageId)
                .map(AgentMessageEntity::getContentRef)
                .orElse(null);
    }

    private String savePayload(PayloadTypeEnumVO payloadType, Object value, String preview) {
        if (payloadRepository == null || value == null) {
            return null;
        }
        String content = value instanceof String text ? text : JSON.toJSONString(value);
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(payloadType)
                .content(content)
                .preview(preview(content, preview))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String preview(String content, String fallback) {
        if (content == null || content.isBlank()) {
            return fallback;
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
