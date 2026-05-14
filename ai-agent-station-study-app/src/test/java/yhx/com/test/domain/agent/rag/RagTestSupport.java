package yhx.com.test.domain.agent.rag;

import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRagExecutionRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.entity.persistence.RagHitEntity;
import yhx.com.domain.agent.model.entity.persistence.RagQueryEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class RagTestSupport {

    static class FullRepository implements IRunRepository, IRagExecutionRepository, IPayloadRepository, IEvidenceRepository {
        final Map<String, AgentRunEntity> runs = new LinkedHashMap<>();
        final List<RagQueryEntity> queries = new ArrayList<>();
        final List<RagHitEntity> hits = new ArrayList<>();
        final List<AgentEvidenceEntity> evidence = new ArrayList<>();
        final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        int markRagWasUsedCalls;

        @Override
        public String createRun(AgentRunEntity run) {
            runs.put(run.getRunId(), run);
            return run.getRunId();
        }

        @Override
        public void updateRunPhase(String runId, RuntimePhaseEnumVO phase) {
            runs.get(runId).setPhase(phase);
        }

        @Override
        public void updateRunStatus(String runId, RunStatusEnumVO status, String failureCode) {
            runs.get(runId).setStatus(status);
        }

        @Override
        public void updateFinalAnswerRef(String runId, String finalAnswerRef) {
            runs.get(runId).setFinalAnswerRef(finalAnswerRef);
        }

        @Override
        public void markRagWasUsed(String runId) {
            markRagWasUsedCalls++;
            runs.computeIfAbsent(runId, id -> AgentRunEntity.builder().runId(id).build()).setRagWasUsed(true);
        }

        @Override
        public Optional<AgentRunEntity> findRun(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public String saveRagQuery(RagQueryEntity query) {
            if (query.getRagQueryId() == null) {
                query.setRagQueryId("rag-query-" + (queries.size() + 1));
            }
            queries.add(query);
            return query.getRagQueryId();
        }

        @Override
        public void updateRagQueryStatus(String ragQueryId, String status, String failureCode, String failureMessage) {
            queries.stream().filter(item -> ragQueryId.equals(item.getRagQueryId())).findFirst().ifPresent(item -> {
                item.setStatus(status);
                item.setFailureCode(failureCode);
                item.setFailureMessage(failureMessage);
            });
        }

        @Override
        public void saveRagHits(List<RagHitEntity> items) {
            hits.addAll(items);
        }

        @Override
        public List<RagQueryEntity> listRagQueries(String runId) {
            return queries.stream().filter(item -> runId.equals(item.getRunId())).toList();
        }

        @Override
        public List<RagHitEntity> listRagHits(String runId) {
            return hits.stream().filter(item -> runId.equals(item.getRunId())).toList();
        }

        @Override
        public String savePayload(AgentPayloadEntity payload) {
            if (payload.getPayloadId() == null) {
                payload.setPayloadId("payload-" + (payloads.size() + 1));
            }
            payloads.put(payload.getPayloadId(), payload);
            return payload.getPayloadId();
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            return Optional.ofNullable(payloads.get(payloadId));
        }

        @Override
        public String saveEvidence(AgentEvidenceEntity item) {
            if (item.getEvidenceId() == null) {
                item.setEvidenceId("evidence-" + (evidence.size() + 1));
            }
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
    }
}
