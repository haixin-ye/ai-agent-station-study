package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.RunEventTypeEnumVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class RunEventPublisher {

    private final IEventTraceRepository eventTraceRepository;
    private final IPayloadRepository payloadRepository;
    private final RunDiagnosticRecorder diagnosticRecorder;

    public RunEventPublisher(IEventTraceRepository eventTraceRepository, IPayloadRepository payloadRepository) {
        this(eventTraceRepository, payloadRepository, null);
    }

    public RunEventPublisher(IEventTraceRepository eventTraceRepository,
                             IPayloadRepository payloadRepository,
                             RunDiagnosticRecorder diagnosticRecorder) {
        this.eventTraceRepository = eventTraceRepository;
        this.payloadRepository = payloadRepository;
        this.diagnosticRecorder = diagnosticRecorder;
    }

    public void received(String runId, String summary) {
        append(runId, RunEventTypeEnumVO.RUN_STARTED, payload("received", summary, null, null));
    }

    public void phase(String runId, String title, String summary) {
        append(runId, RunEventTypeEnumVO.STATUS_CHANGED, payload(title, summary, null, null));
    }

    public void askingUser(String runId, String pendingInputId, String question) {
        append(runId, RunEventTypeEnumVO.ASK_USER, payload("asking_user", question, pendingInputId, null));
    }

    public void askingUser(String runId, String pendingInputId, AskUserRequestVO request) {
        Map<String, Object> payload = payload("asking_user",
                request == null ? null : request.getQuestion(),
                pendingInputId,
                null);
        if (request != null) {
            payload.put("question", request.getQuestion());
            payload.put("inputMode", request.getInputMode());
            payload.put("allowFreeText", request.getAllowFreeText());
            payload.put("options", request.getOptions());
        }
        append(runId, RunEventTypeEnumVO.ASK_USER, payload);
    }

    public void completed(String runId, String finalMessageId) {
        append(runId, RunEventTypeEnumVO.FINAL_READY, payload("completed", "Final response is ready.", null, finalMessageId));
    }

    public void failed(String runId, String userSafeSummary) {
        append(runId, RunEventTypeEnumVO.RUN_FAILED, payload("failed", userSafeSummary, null, null));
    }

    public void cancelled(String runId, String summary) {
        append(runId, RunEventTypeEnumVO.RUN_FAILED, payload("cancelled", summary, null, null));
    }

    private void append(String runId, RunEventTypeEnumVO eventType, Map<String, Object> payload) {
        if (eventTraceRepository == null || payloadRepository == null) {
            return;
        }
        log.info("[AutoAgent][event] runId={}, eventType={}, title={}, summary={}, pendingInputId={}, finalMessageId={}",
                runId, eventType == null ? null : eventType.code(), payload.get("title"), payload.get("summary"),
                payload.get("pendingInputId"), payload.get("finalMessageId"));
        if (diagnosticRecorder != null) {
            Map<String, Object> diagnostic = new LinkedHashMap<>(payload);
            diagnostic.put("eventType", eventType == null ? null : eventType.code());
            diagnosticRecorder.record(runId, "USER_EVENT", eventType == null ? null : eventType.code(), diagnostic);
        }
        String payloadRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(payload))
                .preview(String.valueOf(payload.get("summary")))
                .createdAt(LocalDateTime.now())
                .build());
        eventTraceRepository.appendUserVisibleEvent(AgentRunEventEntity.builder()
                .runId(runId)
                .eventType(eventType)
                .payloadRef(payloadRef)
                .userVisible(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Map<String, Object> payload(String title, String summary, String pendingInputId, String finalMessageId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("summary", summary);
        payload.put("pendingInputId", pendingInputId);
        payload.put("finalMessageId", finalMessageId);
        return payload;
    }
}
