package yhx.com.domain.agent.service.api;

import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.service.debug.DebugAccessPolicy;
import yhx.com.domain.agent.service.debug.DebugPayloadPreviewPolicy;

import java.util.List;
import java.util.Optional;

public class AgentDebugFacade {

    private final IEventTraceRepository eventTraceRepository;
    private final IEvidenceRepository evidenceRepository;
    private final IToolRepository toolRepository;
    private final IPayloadRepository payloadRepository;
    private final DebugAccessPolicy debugAccessPolicy;
    private final DebugPayloadPreviewPolicy debugPayloadPreviewPolicy;

    public AgentDebugFacade(IEventTraceRepository eventTraceRepository,
                            IEvidenceRepository evidenceRepository,
                            IToolRepository toolRepository,
                            IPayloadRepository payloadRepository,
                            DebugAccessPolicy debugAccessPolicy,
                            DebugPayloadPreviewPolicy debugPayloadPreviewPolicy) {
        this.eventTraceRepository = eventTraceRepository;
        this.evidenceRepository = evidenceRepository;
        this.toolRepository = toolRepository;
        this.payloadRepository = payloadRepository;
        this.debugAccessPolicy = debugAccessPolicy;
        this.debugPayloadPreviewPolicy = debugPayloadPreviewPolicy;
    }

    public List<AgentRunTraceEntity> listTraces(String runId, int limit) {
        debugAccessPolicy.requireDebugApiEnabled();
        return eventTraceRepository.listDebugTrace(runId, normalizedLimit(limit));
    }

    public List<AgentEvidenceEntity> listEvidence(String runId) {
        debugAccessPolicy.requireDebugApiEnabled();
        return evidenceRepository.listRunEvidence(runId);
    }

    public List<ToolCallEntity> listToolCalls(String runId) {
        debugAccessPolicy.requireDebugApiEnabled();
        return toolRepository.listRunToolCalls(runId, 100);
    }

    public Optional<AgentPayloadEntity> findPayload(String payloadId) {
        debugAccessPolicy.requirePayloadPreviewEnabled();
        return payloadRepository.findPayload(payloadId)
                .map(debugPayloadPreviewPolicy::applyPreviewPolicy);
    }

    private int normalizedLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, 500);
    }
}

