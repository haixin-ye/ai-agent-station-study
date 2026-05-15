package yhx.com.domain.agent.service.debug;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;

import java.time.LocalDateTime;
import java.util.Map;

public class DebugDataPipeline {

    private final IEventTraceRepository eventTraceRepository;
    private final IPayloadRepository payloadRepository;

    public DebugDataPipeline(IEventTraceRepository eventTraceRepository, IPayloadRepository payloadRepository) {
        this.eventTraceRepository = eventTraceRepository;
        this.payloadRepository = payloadRepository;
    }

    public String appendTrace(String runId, Long seq, TraceTypeEnumVO traceType, Map<String, Object> compactPayload) {
        String payloadRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(compactPayload == null ? Map.of() : compactPayload))
                .preview(compactPayload == null ? "" : String.valueOf(compactPayload.getOrDefault("summary", "")))
                .createdAt(LocalDateTime.now())
                .build());
        eventTraceRepository.appendTrace(AgentRunTraceEntity.builder()
                .runId(runId)
                .seq(seq)
                .traceType(traceType == null ? TraceTypeEnumVO.RUNTIME_DECISION : traceType)
                .payloadRef(payloadRef)
                .createdAt(LocalDateTime.now())
                .build());
        return payloadRef;
    }
}

