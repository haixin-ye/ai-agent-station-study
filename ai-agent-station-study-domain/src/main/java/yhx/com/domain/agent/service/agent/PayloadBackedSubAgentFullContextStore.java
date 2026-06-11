package yhx.com.domain.agent.service.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.agent.SubAgentFullContextVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;

import java.time.LocalDateTime;
import java.util.Optional;

public class PayloadBackedSubAgentFullContextStore implements SubAgentFullContextStore {

    private final IPayloadRepository payloadRepository;

    public PayloadBackedSubAgentFullContextStore(IPayloadRepository payloadRepository) {
        this.payloadRepository = payloadRepository;
    }

    @Override
    public String save(SubAgentFullContextVO context) {
        if (payloadRepository == null || context == null) {
            return null;
        }
        String payloadRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(context, SerializerFeature.DisableCircularReferenceDetect))
                .preview(preview(context))
                .createdAt(LocalDateTime.now())
                .build());
        context.setSnapshotRef(payloadRef);
        return payloadRef;
    }

    @Override
    public Optional<SubAgentFullContextVO> load(String snapshotRef) {
        if (payloadRepository == null || snapshotRef == null || snapshotRef.isBlank()) {
            return Optional.empty();
        }
        return payloadRepository.findContent(snapshotRef)
                .map(content -> JSON.parseObject(content, SubAgentFullContextVO.class));
    }

    private String preview(SubAgentFullContextVO context) {
        String childRunId = context.getChildRunId() == null ? "unknown-child" : context.getChildRunId();
        return "sub-agent-full-context:" + childRunId + ":entries=" + context.getEntries().size();
    }
}
