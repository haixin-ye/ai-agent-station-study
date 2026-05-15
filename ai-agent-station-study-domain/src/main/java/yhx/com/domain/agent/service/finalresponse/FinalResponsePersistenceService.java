package yhx.com.domain.agent.service.finalresponse;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunAuditEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.AuditTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.MessageRoleEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class FinalResponsePersistenceService {

    private final IPayloadRepository payloadRepository;
    private final IConversationRepository conversationRepository;
    private final IRunRepository runRepository;
    private final IEventTraceRepository eventTraceRepository;

    public FinalResponsePersistenceService(IPayloadRepository payloadRepository,
                                           IConversationRepository conversationRepository,
                                           IRunRepository runRepository,
                                           IEventTraceRepository eventTraceRepository) {
        this.payloadRepository = payloadRepository;
        this.conversationRepository = conversationRepository;
        this.runRepository = runRepository;
        this.eventTraceRepository = eventTraceRepository;
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
        runRepository.updateFinalAnswerRef(command.getRunId(), contentRef);
        runRepository.updateRunStatus(command.getRunId(), RunStatusEnumVO.COMPLETED, null);
        saveAudit(command.getRunId(), "FINAL_DELIVERED", "Final response persisted after guard pass.");
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
}
