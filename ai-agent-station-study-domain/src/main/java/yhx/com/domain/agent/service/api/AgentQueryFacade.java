package yhx.com.domain.agent.service.api;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgentQueryFacade {

    private final IRunRepository runRepository;
    private final IConversationRepository conversationRepository;
    private final IEventTraceRepository eventTraceRepository;
    private final IPendingInputRepository pendingInputRepository;
    private final IArtifactRepository artifactRepository;
    private final IPayloadRepository payloadRepository;

    public AgentQueryFacade(IRunRepository runRepository,
                            IConversationRepository conversationRepository,
                            IEventTraceRepository eventTraceRepository,
                            IPendingInputRepository pendingInputRepository,
                            IArtifactRepository artifactRepository,
                            IPayloadRepository payloadRepository) {
        this.runRepository = runRepository;
        this.conversationRepository = conversationRepository;
        this.eventTraceRepository = eventTraceRepository;
        this.pendingInputRepository = pendingInputRepository;
        this.artifactRepository = artifactRepository;
        this.payloadRepository = payloadRepository;
    }

    public Optional<AgentRunEntity> findRun(String runId) {
        return runRepository.findRun(runId);
    }

    public List<AgentMessageEntity> listVisibleMessages(String sessionId, int limit) {
        return conversationRepository.listRecentVisibleMessages(sessionId, normalizedLimit(limit));
    }

    public Optional<String> resolveContent(String payloadRef) {
        if (payloadRef == null || payloadRef.isBlank()) {
            return Optional.empty();
        }
        return payloadRepository.findContent(payloadRef);
    }

    public Optional<AgentPayloadEntity> findPayload(String payloadRef) {
        if (payloadRef == null || payloadRef.isBlank()) {
            return Optional.empty();
        }
        return payloadRepository.findPayload(payloadRef);
    }

    public Optional<String> findFinalAnswer(String runId) {
        return findRun(runId)
                .map(AgentRunEntity::getFinalAnswerRef)
                .flatMap(this::resolveContent);
    }

    public List<AgentRunEventEntity> listUserVisibleEvents(String runId, int limit) {
        return eventTraceRepository.listUserVisibleEvents(runId, normalizedLimit(limit));
    }

    public Optional<AgentPendingInputEntity> findActivePendingInput(String runId) {
        return pendingInputRepository.findActivePendingInput(runId);
    }

    public Optional<AgentPendingInputEntity> findPendingInput(String pendingId) {
        return pendingInputRepository.findByPendingId(pendingId);
    }

    public List<AgentArtifactEntity> listSessionArtifacts(String sessionId, int limit) {
        return artifactRepository.findArtifactCandidates(sessionId, "", normalizedLimit(limit));
    }

    public Optional<AgentArtifactEntity> findArtifact(String artifactId) {
        return artifactRepository.findArtifact(artifactId);
    }

    public List<AgentArtifactEntity> listArtifactVersions(String artifactId) {
        return findArtifact(artifactId).map(List::of).orElseGet(Collections::emptyList);
    }

    public Map<String, Object> readJsonPayloadAsMap(String payloadRef) {
        return resolveContent(payloadRef)
                .map(content -> JSON.parseObject(content, Map.class))
                .orElseGet(Collections::emptyMap);
    }

    private int normalizedLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }
}

