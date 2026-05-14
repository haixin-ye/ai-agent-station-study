package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;

import java.util.Optional;

public interface IPayloadRepository {

    String savePayload(AgentPayloadEntity payload);

    Optional<AgentPayloadEntity> findPayload(String payloadId);

    default Optional<String> findContent(String payloadId) {
        return findPayload(payloadId).map(AgentPayloadEntity::getContent);
    }
}
