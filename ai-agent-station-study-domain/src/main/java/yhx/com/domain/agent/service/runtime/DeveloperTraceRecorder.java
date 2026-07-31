package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class DeveloperTraceRecorder {

    private final IEventTraceRepository eventTraceRepository;
    private final IPayloadRepository payloadRepository;
    private final RunDiagnosticRecorder diagnosticRecorder;

    public DeveloperTraceRecorder(IEventTraceRepository eventTraceRepository, IPayloadRepository payloadRepository) {
        this(eventTraceRepository, payloadRepository, null);
    }

    public DeveloperTraceRecorder(IEventTraceRepository eventTraceRepository,
                                  IPayloadRepository payloadRepository,
                                  RunDiagnosticRecorder diagnosticRecorder) {
        this.eventTraceRepository = eventTraceRepository;
        this.payloadRepository = payloadRepository;
        this.diagnosticRecorder = diagnosticRecorder;
    }

    public void phaseStarted(String runId, Integer loopIndex, RuntimePhaseEnumVO phase) {
        append(runId, TraceTypeEnumVO.RUNTIME_DECISION, payload(loopIndex, "phase_started", phase == null ? null : phase.code(), null, null));
    }

    public void phaseCompleted(String runId, Integer loopIndex, RuntimePhaseEnumVO phase) {
        append(runId, TraceTypeEnumVO.RUNTIME_DECISION, payload(loopIndex, "phase_completed", phase == null ? null : phase.code(), null, null));
    }

    public void nodeInvocation(String runId, Integer loopIndex, String componentCode, String payloadRef) {
        append(runId, TraceTypeEnumVO.NODE_INPUT, payload(loopIndex, "node_invocation", componentCode, payloadRef, null));
    }

    public void nodeInput(String runId, Integer loopIndex, String componentCode, Integer attemptNo,
                          Map<String, Object> details) {
        appendDetailed(runId, TraceTypeEnumVO.NODE_INPUT, detailedPayload(loopIndex, "node_input_full",
                componentCode, attemptNo, details));
    }

    public void nodeOutput(String runId, Integer loopIndex, String componentCode, Integer attemptNo,
                           Map<String, Object> details) {
        appendDetailed(runId, TraceTypeEnumVO.NODE_OUTPUT, detailedPayload(loopIndex, "node_output_full",
                componentCode, attemptNo, details));
    }

    public void actionParsed(String runId, Integer loopIndex, MainAgentActionTypeEnumVO actionType, String payloadRef) {
        append(runId, TraceTypeEnumVO.CONTRACT_VALIDATION, payload(loopIndex, "action_parsed", actionType == null ? null : actionType.code(), payloadRef, null));
    }

    public void contractFailure(String runId, Integer loopIndex, RuntimeFailureCodeEnumVO failureCode, String payloadRef) {
        append(runId, TraceTypeEnumVO.CONTRACT_VALIDATION, payload(loopIndex, "contract_failure", failureCode == null ? null : failureCode.code(), payloadRef, null));
    }

    public void error(String runId, Integer loopIndex, RuntimeFailureCodeEnumVO failureCode, String summary, String payloadRef) {
        append(runId, TraceTypeEnumVO.RUNTIME_DECISION, payload(loopIndex, "error", failureCode == null ? null : failureCode.code(), payloadRef, summary));
    }

    private void append(String runId, TraceTypeEnumVO traceType, Map<String, Object> payload) {
        if (eventTraceRepository == null || payloadRepository == null) {
            return;
        }
        log.info("[AutoAgent][trace] runId={}, traceType={}, loopIndex={}, event={}, code={}, summary={}, payloadRef={}",
                runId, traceType == null ? null : traceType.code(), payload.get("loopIndex"),
                payload.get("event"), payload.get("code"), payload.get("summary"), payload.get("payloadRef"));
        if (diagnosticRecorder != null) {
            Map<String, Object> diagnostic = new LinkedHashMap<>(payload);
            diagnostic.put("traceType", traceType == null ? null : traceType.code());
            diagnosticRecorder.record(runId, "TRACE", String.valueOf(payload.get("event")), diagnostic);
        }
        String payloadRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.DEBUG_TRACE)
                .content(JSON.toJSONString(payload))
                .preview(String.valueOf(payload.get("event")))
                .createdAt(LocalDateTime.now())
                .build());
        eventTraceRepository.appendTrace(AgentRunTraceEntity.builder()
                .runId(runId)
                .traceType(traceType)
                .payloadRef(payloadRef)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void appendDetailed(String runId, TraceTypeEnumVO traceType, Map<String, Object> payload) {
        append(runId, traceType, payload);
    }

    private Map<String, Object> detailedPayload(Integer loopIndex, String event, String componentCode,
                                                Integer attemptNo, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loopIndex", loopIndex);
        payload.put("event", event);
        payload.put("code", componentCode);
        payload.put("attemptNo", attemptNo);
        if (details != null) {
            details.forEach(payload::put);
        }
        return payload;
    }

    private Map<String, Object> payload(Integer loopIndex, String event, String code, String payloadRef, String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loopIndex", loopIndex);
        payload.put("event", event);
        payload.put("code", code);
        payload.put("payloadRef", payloadRef);
        payload.put("summary", summary);
        return payload;
    }
}
