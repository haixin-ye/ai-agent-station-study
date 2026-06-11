package yhx.com.domain.agent.service.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentContinuationVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRegistrySnapshotVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class PayloadBackedParentChildRunRegistryStore implements ParentChildRunRegistryStore {

    private static final String PAYLOAD_ID_PREFIX = "sub-agent-registry-";

    private final IPayloadRepository payloadRepository;

    public PayloadBackedParentChildRunRegistryStore(IPayloadRepository payloadRepository) {
        if (payloadRepository == null) {
            throw new IllegalArgumentException("Payload repository is required.");
        }
        this.payloadRepository = payloadRepository;
    }

    @Override
    public void saveParent(String parentRunId,
                           List<ParentChildRunRelationVO> relations,
                           List<GenericSubAgentContinuationVO> continuations) {
        if (parentRunId == null || parentRunId.isBlank()) {
            return;
        }
        ParentChildRunRegistrySnapshotVO snapshot = ParentChildRunRegistrySnapshotVO.builder()
                .parentRunId(parentRunId)
                .relations(relations == null ? List.of() : relations)
                .continuations(continuations == null ? List.of() : continuations)
                .build();
        payloadRepository.saveOrUpdatePayload(AgentPayloadEntity.builder()
                .payloadId(payloadId(parentRunId))
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(snapshot, SerializerFeature.DisableCircularReferenceDetect))
                .preview("sub-agent-registry:" + parentRunId
                        + ":children=" + snapshot.getRelations().size()
                        + ":continuations=" + snapshot.getContinuations().size())
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    public Optional<ParentChildRunRegistrySnapshotVO> loadParent(String parentRunId) {
        if (parentRunId == null || parentRunId.isBlank()) {
            return Optional.empty();
        }
        return payloadRepository.findContent(payloadId(parentRunId))
                .map(content -> JSON.parseObject(content, ParentChildRunRegistrySnapshotVO.class));
    }

    private String payloadId(String parentRunId) {
        return PAYLOAD_ID_PREFIX + parentRunId;
    }
}
