package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.PlanStateVO;
import yhx.com.domain.agent.service.runtime.port.PlanStatePort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PayloadBackedPlanStatePort implements PlanStatePort {

    private static final String EVENT = "plan_state_saved";

    private final IPayloadRepository payloadRepository;
    private final IEventTraceRepository eventTraceRepository;

    public PayloadBackedPlanStatePort(IPayloadRepository payloadRepository, IEventTraceRepository eventTraceRepository) {
        this.payloadRepository = payloadRepository;
        this.eventTraceRepository = eventTraceRepository;
    }

    @Override
    public String savePlan(String runId, PlanStateVO plan) {
        String planRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(plan))
                .preview(plan == null ? null : plan.getGoal())
                .createdAt(LocalDateTime.now())
                .build());
        String traceRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.DEBUG_TRACE)
                .content(JSON.toJSONString(Map.of("event", EVENT, "planRef", planRef)))
                .preview(EVENT)
                .createdAt(LocalDateTime.now())
                .build());
        eventTraceRepository.appendTrace(AgentRunTraceEntity.builder()
                .runId(runId)
                .traceType(TraceTypeEnumVO.RUNTIME_DECISION)
                .payloadRef(traceRef)
                .createdAt(LocalDateTime.now())
                .build());
        return planRef;
    }

    @Override
    public PlanStateVO findPlan(String runId) {
        List<AgentRunTraceEntity> traces = eventTraceRepository.listDebugTrace(runId, 100);
        for (int index = traces.size() - 1; index >= 0; index--) {
            AgentRunTraceEntity trace = traces.get(index);
            String payloadRef = trace.getPayloadRef();
            if (payloadRef == null || payloadRef.isBlank()) {
                continue;
            }
            String traceContent = payloadRepository.findContent(payloadRef).orElse(null);
            if (traceContent == null || !traceContent.contains(EVENT)) {
                continue;
            }
            JSONObject traceJson = JSON.parseObject(traceContent);
            String planRef = traceJson.getString("planRef");
            if (planRef == null || planRef.isBlank()) {
                continue;
            }
            return payloadRepository.findContent(planRef)
                    .map(content -> JSON.parseObject(content, PlanStateVO.class))
                    .orElse(null);
        }
        return null;
    }
}
