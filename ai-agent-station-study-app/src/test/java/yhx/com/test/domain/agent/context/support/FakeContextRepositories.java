package yhx.com.test.domain.agent.context.support;

import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FakeContextRepositories implements IConversationRepository, IArtifactRepository, IEvidenceRepository, IMemoryRepository, IPayloadRepository, ITurnRepository, ITurnSummaryRepository {

    public final Map<String, AgentPayloadEntity> payloads = new HashMap<>();
    public final Map<String, AgentArtifactEntity> artifacts = new HashMap<>();
    public final List<AgentMessageEntity> messages = new ArrayList<>();
    public final List<AgentEvidenceEntity> evidence = new ArrayList<>();
    public final List<AgentMemoryEntity> memories = new ArrayList<>();
    public final List<AgentTurnEntity> turns = new ArrayList<>();
    public final List<AgentTurnSummaryEntity> turnSummaries = new ArrayList<>();

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
    public Optional<AgentMessageEntity> findMessageById(String messageId) {
        return messages.stream().filter(message -> messageId.equals(message.getMessageId())).findFirst();
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
    public Optional<AgentMemoryEntity> findMemory(String memoryId) {
        return memories.stream().filter(memory -> memoryId.equals(memory.getMemoryId())).findFirst();
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

    @Override
    public String saveCompletedTurn(AgentTurnEntity turn) {
        turns.add(turn);
        return turn.getTurnId();
    }

    @Override
    public Optional<AgentTurnEntity> findByTurnId(String turnId) {
        return turns.stream().filter(turn -> turnId.equals(turn.getTurnId())).findFirst();
    }

    @Override
    public Optional<AgentTurnEntity> findByRunId(String runId) {
        return turns.stream().filter(turn -> runId.equals(turn.getRunId())).findFirst();
    }

    @Override
    public List<AgentTurnEntity> listRecentCompletedTurns(String sessionId, int limit) {
        return turns.stream()
                .filter(turn -> sessionId == null || sessionId.equals(turn.getSessionId()))
                .filter(turn -> "COMPLETED".equals(turn.getStatus()))
                .sorted((left, right) -> Long.compare(nullToZero(right.getTurnNo()), nullToZero(left.getTurnNo())))
                .limit(limit)
                .toList();
    }

    @Override
    public List<AgentTurnEntity> listCompletedTurnsBefore(String sessionId, Long beforeTurnNo, int limit) {
        return turns.stream()
                .filter(turn -> sessionId == null || sessionId.equals(turn.getSessionId()))
                .filter(turn -> "COMPLETED".equals(turn.getStatus()))
                .filter(turn -> beforeTurnNo == null || (turn.getTurnNo() != null && turn.getTurnNo() < beforeTurnNo))
                .sorted((left, right) -> Long.compare(nullToZero(right.getTurnNo()), nullToZero(left.getTurnNo())))
                .limit(limit)
                .toList();
    }

    @Override
    public String saveSummary(AgentTurnSummaryEntity summary) {
        turnSummaries.add(summary);
        return summary.getSummaryId();
    }

    @Override
    public Optional<AgentTurnSummaryEntity> findSummaryById(String summaryId) {
        return turnSummaries.stream().filter(summary -> summaryId.equals(summary.getSummaryId())).findFirst();
    }

    @Override
    public Optional<AgentTurnSummaryEntity> findSummaryByTurnId(String turnId) {
        return turnSummaries.stream().filter(summary -> turnId.equals(summary.getTurnId())).findFirst();
    }

    @Override
    public List<AgentTurnSummaryEntity> listByTurnIds(List<String> turnIds) {
        return turnSummaries.stream()
                .filter(summary -> turnIds.contains(summary.getTurnId()))
                .toList();
    }

    @Override
    public List<AgentTurnSummaryEntity> listRecentActiveSummaries(String sessionId, int limit) {
        return turnSummaries.stream()
                .filter(summary -> sessionId == null || sessionId.equals(summary.getSessionId()))
                .filter(summary -> "ACTIVE".equals(summary.getStatus()))
                .sorted((left, right) -> nullSafeCreatedAt(right).compareTo(nullSafeCreatedAt(left)))
                .limit(limit)
                .toList();
    }

    @Override
    public void markSummariesRolledUp(List<String> summaryIds) {
        turnSummaries.stream()
                .filter(summary -> summaryIds.contains(summary.getSummaryId()))
                .forEach(summary -> summary.setStatus("ROLLED_UP"));
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private java.time.LocalDateTime nullSafeCreatedAt(AgentTurnSummaryEntity summary) {
        return summary.getCreatedAt() == null ? java.time.LocalDateTime.MIN : summary.getCreatedAt();
    }
}
