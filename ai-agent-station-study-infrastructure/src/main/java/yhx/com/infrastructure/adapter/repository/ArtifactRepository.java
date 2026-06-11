package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.infrastructure.dao.IAgentArtifactDao;
import yhx.com.infrastructure.dao.po.AgentArtifactPO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ArtifactRepository implements IArtifactRepository {

    @Resource
    private IAgentArtifactDao agentArtifactDao;

    @Override
    public String saveArtifact(AgentArtifactEntity artifact) {
        if (artifact.getArtifactId() == null || artifact.getArtifactId().isBlank()) {
            artifact.setArtifactId("artifact-" + UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        if (artifact.getCreatedAt() == null) {
            artifact.setCreatedAt(now);
        }
        if (artifact.getUpdatedAt() == null) {
            artifact.setUpdatedAt(now);
        }
        if (artifact.getVersion() == null) {
            artifact.setVersion(1);
        }
        agentArtifactDao.insert(toPO(artifact));
        return artifact.getArtifactId();
    }

    @Override
    public Optional<AgentArtifactEntity> findArtifact(String artifactId) {
        return Optional.ofNullable(agentArtifactDao.queryByArtifactId(artifactId)).map(this::toEntity);
    }

    @Override
    public List<AgentArtifactEntity> findArtifactCandidates(String sessionId, String userInput, int limit) {
        return agentArtifactDao.listCandidates(sessionId, userInput, limit).stream().map(this::toEntity).toList();
    }

    private AgentArtifactPO toPO(AgentArtifactEntity entity) {
        return AgentArtifactPO.builder()
                .artifactId(entity.getArtifactId())
                .sessionId(entity.getSessionId())
                .runId(entity.getRunId())
                .artifactType(entity.getArtifactType())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .contentRef(entity.getContentRef())
                .version(entity.getVersion())
                .lastMentionedAt(entity.getLastMentionedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentArtifactEntity toEntity(AgentArtifactPO po) {
        return AgentArtifactEntity.builder()
                .artifactId(po.getArtifactId())
                .sessionId(po.getSessionId())
                .runId(po.getRunId())
                .artifactType(po.getArtifactType())
                .title(po.getTitle())
                .summary(po.getSummary())
                .contentRef(po.getContentRef())
                .version(po.getVersion())
                .lastMentionedAt(po.getLastMentionedAt())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
