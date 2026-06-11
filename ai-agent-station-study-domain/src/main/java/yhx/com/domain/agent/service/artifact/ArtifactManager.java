package yhx.com.domain.agent.service.artifact;

import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.artifact.ArtifactCreateCommandVO;
import yhx.com.domain.agent.model.valobj.artifact.ArtifactUpdateCommandVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class ArtifactManager {

    private final IArtifactRepository artifactRepository;
    private final IPayloadRepository payloadRepository;

    public ArtifactManager(IArtifactRepository artifactRepository, IPayloadRepository payloadRepository) {
        this.artifactRepository = artifactRepository;
        this.payloadRepository = payloadRepository;
    }

    public AgentArtifactEntity createArtifact(ArtifactCreateCommandVO command) {
        String payloadId = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.ARTIFACT_CONTENT)
                .content(command.getContent())
                .createdAt(LocalDateTime.now())
                .build());
        AgentArtifactEntity artifact = AgentArtifactEntity.builder()
                .artifactId("artifact-" + UUID.randomUUID())
                .sessionId(command.getSessionId())
                .runId(command.getRunId())
                .artifactType(command.getArtifactType())
                .title(command.getTitle())
                .summary(command.getSummary())
                .contentRef(payloadId)
                .version(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        artifactRepository.saveArtifact(artifact);
        return artifact;
    }

    public AgentArtifactEntity updateArtifact(ArtifactUpdateCommandVO command) {
        AgentArtifactEntity existing = artifactRepository.findArtifact(command.getArtifactId())
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + command.getArtifactId()));
        String payloadId = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.ARTIFACT_CONTENT)
                .content(command.getContent())
                .createdAt(LocalDateTime.now())
                .build());
        existing.setTitle(command.getTitle() == null ? existing.getTitle() : command.getTitle());
        existing.setSummary(command.getSummary() == null ? existing.getSummary() : command.getSummary());
        existing.setContentRef(payloadId);
        existing.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
        existing.setUpdatedAt(LocalDateTime.now());
        artifactRepository.saveArtifact(existing);
        return existing;
    }

    public AgentArtifactEntity findArtifact(String artifactId) {
        Optional<AgentArtifactEntity> artifact = artifactRepository.findArtifact(artifactId);
        return artifact.orElse(null);
    }
}
