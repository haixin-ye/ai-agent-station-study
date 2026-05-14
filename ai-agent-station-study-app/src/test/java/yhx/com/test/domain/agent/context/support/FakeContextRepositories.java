package yhx.com.test.domain.agent.context.support;

import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FakeContextRepositories implements IConversationRepository, IArtifactRepository, IEvidenceRepository, IMemoryRepository, IPayloadRepository {

    public final Map<String, AgentPayloadEntity> payloads = new HashMap<>();
    public final Map<String, AgentArtifactEntity> artifacts = new HashMap<>();
    public final List<AgentMessageEntity> messages = new ArrayList<>();
    public final List<AgentEvidenceEntity> evidence = new ArrayList<>();
    public final List<AgentMemoryEntity> memories = new ArrayList<>();

    @Override
    public String createSession(AgentSessionEntity session) {
        return session.getSessionId();
    }

    @Override
    public Optional<AgentSessionEntity> findSession(String sessionId) {
        return Optional.empty();
    }

    @Override
    public void appendMessage(AgentMessageEntity message) {
        messages.add(message);
    }

    @Override
    public List<AgentMessageEntity> listRecentVisibleMessages(String sessionId, int limit) {
        return messages.stream().filter(message -> Boolean.TRUE.equals(message.getVisibleToUser())).limit(limit).toList();
    }

    @Override
    public String saveArtifact(AgentArtifactEntity artifact) {
        artifacts.put(artifact.getArtifactId(), artifact);
        return artifact.getArtifactId();
    }

    @Override
    public Optional<AgentArtifactEntity> findArtifact(String artifactId) {
        return Optional.ofNullable(artifacts.get(artifactId));
    }

    @Override
    public List<AgentArtifactEntity> findArtifactCandidates(String sessionId, String userInput, int limit) {
        return artifacts.values().stream()
                .filter(artifact -> sessionId == null || sessionId.equals(artifact.getSessionId()))
                .limit(limit)
                .toList();
    }

    @Override
    public String saveEvidence(AgentEvidenceEntity item) {
        evidence.add(item);
        return item.getEvidenceId();
    }

    @Override
    public Optional<AgentEvidenceEntity> findEvidence(String evidenceId) {
        return evidence.stream().filter(item -> evidenceId.equals(item.getEvidenceId())).findFirst();
    }

    @Override
    public List<AgentEvidenceEntity> listRunEvidence(String runId) {
        return evidence.stream().filter(item -> runId.equals(item.getRunId())).toList();
    }

    @Override
    public List<AgentMemoryEntity> findMemoryCandidates(String userId, String sessionId, String query, int limit) {
        return memories.stream().limit(limit).toList();
    }

    @Override
    public String saveConversationSummary(AgentConversationSummaryEntity summary) {
        return summary.getSummaryId();
    }

    @Override
    public String saveLongTermMemory(AgentMemoryEntity memory) {
        memories.add(memory);
        return memory.getMemoryId();
    }

    @Override
    public String recordMemoryEvent(AgentMemoryEventEntity event) {
        return event.getEventId();
    }

    @Override
    public String savePayload(AgentPayloadEntity payload) {
        payloads.put(payload.getPayloadId(), payload);
        return payload.getPayloadId();
    }

    @Override
    public Optional<AgentPayloadEntity> findPayload(String payloadId) {
        return Optional.ofNullable(payloads.get(payloadId));
    }
}
