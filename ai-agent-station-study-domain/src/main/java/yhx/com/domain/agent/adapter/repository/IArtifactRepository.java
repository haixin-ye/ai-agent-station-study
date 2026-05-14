package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;

import java.util.Optional;

public interface IArtifactRepository {

    String saveArtifact(AgentArtifactEntity artifact);

    Optional<AgentArtifactEntity> findArtifact(String artifactId);
}
