package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.modelruntime.AgentModelApiEntity;
import yhx.com.domain.agent.model.entity.modelruntime.AgentModelProfileEntity;
import yhx.com.domain.agent.model.entity.modelruntime.AgentNodeModelBindingEntity;

import java.util.List;
import java.util.Optional;

public interface IModelRuntimeRepository {

    Optional<AgentNodeModelBindingEntity> findActiveBindingByNodeCode(String nodeCode);

    Optional<AgentModelProfileEntity> findActiveModelProfile(String modelProfileId);

    Optional<AgentModelApiEntity> findActiveApi(String apiId);

    List<AgentNodeModelBindingEntity> listActiveBindings();
}
