package yhx.com.test.domain.agent.finalresponse;

import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunAuditEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class FinalResponseTestSupport {

    static class Repository implements IPayloadRepository, IConversationRepository, IRunRepository, IEventTraceRepository {
        final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        final Map<String, AgentRunEntity> runs = new LinkedHashMap<>();
        final List<AgentMessageEntity> messages = new ArrayList<>();
        final List<AgentRunTraceEntity> traces = new ArrayList<>();
        final List<AgentRunAuditEntity> audits = new ArrayList<>();
        final List<AgentRunEventEntity> events = new ArrayList<>();

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
            return messages.stream()
                    .filter(message -> sessionId.equals(message.getSessionId()))
                    .filter(message -> Boolean.TRUE.equals(message.getVisibleToUser()))
                    .limit(limit)
                    .toList();
        }

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
            AgentRunEntity run = runs.computeIfAbsent(runId, id -> AgentRunEntity.builder().runId(id).build());
            run.setStatus(status);
            run.setFailureCode(failureCode);
        }

        @Override
        public void updateFinalAnswerRef(String runId, String finalAnswerRef) {
            runs.computeIfAbsent(runId, id -> AgentRunEntity.builder().runId(id).build()).setFinalAnswerRef(finalAnswerRef);
        }

        @Override
        public void markRagWasUsed(String runId) {
            runs.computeIfAbsent(runId, id -> AgentRunEntity.builder().runId(id).build()).setRagWasUsed(true);
        }

        @Override
        public Optional<AgentRunEntity> findRun(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public void appendUserVisibleEvent(AgentRunEventEntity event) {
            events.add(event);
        }

        @Override
        public void appendTrace(AgentRunTraceEntity trace) {
            traces.add(trace);
        }

        @Override
        public void appendAudit(AgentRunAuditEntity audit) {
            audits.add(audit);
        }

        @Override
        public List<AgentRunEventEntity> listUserVisibleEvents(String runId, int limit) {
            return events.stream().filter(event -> runId.equals(event.getRunId())).limit(limit).toList();
        }

        @Override
        public List<AgentRunTraceEntity> listDebugTrace(String runId, int limit) {
            return traces.stream().filter(trace -> runId.equals(trace.getRunId())).limit(limit).toList();
        }
    }
}
