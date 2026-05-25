package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentVectorIndexEntity;

import java.util.Optional;

public interface IVectorIndexRepository {

    String saveOrUpdate(AgentVectorIndexEntity index);

    Optional<AgentVectorIndexEntity> findBySource(String collectionType, String sourceType, String sourceId);

    void markDisabled(String collectionType, String sourceType, String sourceId);
}
