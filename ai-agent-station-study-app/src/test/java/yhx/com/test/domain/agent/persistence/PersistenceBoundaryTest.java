package yhx.com.test.domain.agent.persistence;

import org.junit.Assert;
import org.junit.Test;
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
import yhx.com.domain.agent.model.valobj.enums.persistence.MessageRoleEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.RunEventTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class PersistenceBoundaryTest {

    @Test
    public void normalConversationReadDoesNotExposeDebugTrace() {
        InMemoryPersistenceRepository repository = new InMemoryPersistenceRepository();
        repository.createSession(AgentSessionEntity.builder()
                .sessionId("session-001")
                .userId("user-001")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build());

        String messageRef = repository.savePayload(AgentPayloadEntity.builder()
                .payloadId("payload-message")
                .payloadType(PayloadTypeEnumVO.TEXT)
                .content("hello user")
                .build());
        String traceRef = repository.savePayload(AgentPayloadEntity.builder()
                .payloadId("payload-trace")
                .payloadType(PayloadTypeEnumVO.DEBUG_TRACE)
                .content("{\"rawPrompt\":\"internal prompt\"}")
                .build());

        repository.appendMessage(AgentMessageEntity.builder()
                .messageId("message-001")
                .sessionId("session-001")
                .runId("run-001")
                .role(MessageRoleEnumVO.ASSISTANT)
                .contentRef(messageRef)
                .visibleToUser(true)
                .createdAt(LocalDateTime.now())
                .build());
        repository.appendTrace(AgentRunTraceEntity.builder()
                .traceId("trace-001")
                .runId("run-001")
                .seq(1L)
                .traceType(TraceTypeEnumVO.NODE_INPUT)
                .payloadRef(traceRef)
                .createdAt(LocalDateTime.now())
                .build());

        List<AgentMessageEntity> visibleMessages = repository.listRecentVisibleMessages("session-001", 10);

        Assert.assertEquals(1, visibleMessages.size());
        Assert.assertEquals(messageRef, visibleMessages.get(0).getContentRef());
        Assert.assertNotEquals(traceRef, visibleMessages.get(0).getContentRef());
        Assert.assertEquals("hello user", repository.findContent(visibleMessages.get(0).getContentRef()).orElse(null));
        Assert.assertEquals(1, repository.listDebugTrace("run-001", 10).size());
    }

    @Test
    public void invisibleMessagesAreNotReturnedByNormalConversationRead() {
        InMemoryPersistenceRepository repository = new InMemoryPersistenceRepository();
        repository.createSession(AgentSessionEntity.builder()
                .sessionId("session-002")
                .userId("user-001")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build());

        repository.appendMessage(AgentMessageEntity.builder()
                .messageId("message-visible")
                .sessionId("session-002")
                .role(MessageRoleEnumVO.ASSISTANT)
                .contentRef("payload-visible")
                .visibleToUser(true)
                .createdAt(LocalDateTime.now())
                .build());
        repository.appendMessage(AgentMessageEntity.builder()
                .messageId("message-hidden")
                .sessionId("session-002")
                .role(MessageRoleEnumVO.SYSTEM)
                .contentRef("payload-hidden")
                .visibleToUser(false)
                .createdAt(LocalDateTime.now())
                .build());

        List<AgentMessageEntity> messages = repository.listRecentVisibleMessages("session-002", 10);

        Assert.assertEquals(1, messages.size());
        Assert.assertEquals("message-visible", messages.get(0).getMessageId());
    }

    @Test
    public void runRepositoryPersistsLifecycleFieldsWithoutPayloadInlining() {
        InMemoryPersistenceRepository repository = new InMemoryPersistenceRepository();
        repository.createRun(AgentRunEntity.builder()
                .runId("run-003")
                .sessionId("session-003")
                .userId("user-001")
                .agentId("agent-001")
                .status(RunStatusEnumVO.CREATED)
                .phase(RuntimePhaseEnumVO.CREATED)
                .ragWasUsed(false)
                .createdAt(LocalDateTime.now())
                .build());

        repository.updateRunPhase("run-003", RuntimePhaseEnumVO.VERIFYING_FINAL);
        repository.updateFinalAnswerRef("run-003", "payload-final-answer");
        repository.updateRunStatus("run-003", RunStatusEnumVO.COMPLETED, null);

        AgentRunEntity run = repository.findRun("run-003").orElseThrow();

        Assert.assertEquals(RuntimePhaseEnumVO.VERIFYING_FINAL, run.getPhase());
        Assert.assertEquals(RunStatusEnumVO.COMPLETED, run.getStatus());
        Assert.assertEquals("payload-final-answer", run.getFinalAnswerRef());
        Assert.assertNull(run.getFailureCode());
    }

    private static class InMemoryPersistenceRepository implements IPayloadRepository,
            IConversationRepository,
            IRunRepository,
            IEventTraceRepository {

        private final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        private final Map<String, AgentSessionEntity> sessions = new LinkedHashMap<>();
        private final Map<String, AgentRunEntity> runs = new LinkedHashMap<>();
        private final List<AgentMessageEntity> messages = new ArrayList<>();
        private final List<AgentRunEventEntity> events = new ArrayList<>();
        private final List<AgentRunTraceEntity> traces = new ArrayList<>();
        private final List<AgentRunAuditEntity> audits = new ArrayList<>();

        @Override
        public String savePayload(AgentPayloadEntity payload) {
            String payloadId = payload.getPayloadId() == null ? "payload-" + UUID.randomUUID() : payload.getPayloadId();
            payload.setPayloadId(payloadId);
            payloads.put(payloadId, payload);
            return payloadId;
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            return Optional.ofNullable(payloads.get(payloadId));
        }

        @Override
        public String createSession(AgentSessionEntity session) {
            sessions.put(session.getSessionId(), session);
            return session.getSessionId();
        }

        @Override
        public Optional<AgentSessionEntity> findSession(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
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
                    .sorted(Comparator.comparing(AgentMessageEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
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
            AgentRunEntity run = runs.get(runId);
            run.setStatus(status);
            run.setFailureCode(failureCode);
        }

        @Override
        public void updateFinalAnswerRef(String runId, String finalAnswerRef) {
            runs.get(runId).setFinalAnswerRef(finalAnswerRef);
        }

        @Override
        public void markRagWasUsed(String runId) {
            runs.get(runId).setRagWasUsed(true);
        }

        @Override
        public Optional<AgentRunEntity> findRun(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public void appendUserVisibleEvent(AgentRunEventEntity event) {
            if (event.getEventType() == null) {
                event.setEventType(RunEventTypeEnumVO.STATUS_CHANGED);
            }
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
            return events.stream()
                    .filter(event -> runId.equals(event.getRunId()))
                    .filter(event -> Boolean.TRUE.equals(event.getUserVisible()))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        @Override
        public List<AgentRunTraceEntity> listDebugTrace(String runId, int limit) {
            return traces.stream()
                    .filter(trace -> runId.equals(trace.getRunId()))
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }
}
