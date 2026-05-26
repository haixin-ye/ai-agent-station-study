package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ISessionTaskSummaryRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionTaskSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.memory.SessionTaskSummaryOutputVO;
import yhx.com.domain.agent.service.memory.gc.worker.SessionTaskSummaryGcWorker;
import yhx.com.domain.agent.service.node.sessiontasksummary.SessionTaskSummaryNodeService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SessionTaskSummaryGcWorkerTest {

    @Test
    public void session_task_summary_worker_saves_new_active_version() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType("SESSION_TASK_SUMMARY")
                .runId("run-1")
                .sessionId("session-1")
                .turnId("turn-3")
                .status("PENDING")
                .build());
        repositories.turnSummaries.add(AgentTurnSummaryEntity.builder()
                .summaryId("turn-summary-1")
                .turnId("turn-1")
                .sessionId("session-1")
                .userId("user-1")
                .summaryRef("payload-summary-1")
                .status("ACTIVE")
                .build());
        repositories.turnSummaries.add(AgentTurnSummaryEntity.builder()
                .summaryId("turn-summary-2")
                .turnId("turn-2")
                .sessionId("session-1")
                .userId("user-1")
                .summaryRef("payload-summary-2")
                .status("ACTIVE")
                .build());
        repositories.payloads.put("payload-summary-1", AgentPayloadEntity.builder()
                .payloadId("payload-summary-1")
                .content("User asked for a memory redesign and approved the simplified categories.")
                .build());
        repositories.payloads.put("payload-summary-2", AgentPayloadEntity.builder()
                .payloadId("payload-summary-2")
                .content("Agent implemented session task summary persistence foundation.")
                .build());

        SessionTaskSummaryGcWorker worker = new SessionTaskSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                new StubSessionTaskSummaryNodeService(),
                8);

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals(1, repositories.sessionTaskSummaries.size());
        AgentSessionTaskSummaryEntity saved = repositories.sessionTaskSummaries.get(0);
        Assert.assertEquals("session-1", saved.getSessionId());
        Assert.assertEquals("user-1", saved.getUserId());
        Assert.assertEquals(Integer.valueOf(1), saved.getVersionNo());
        Assert.assertEquals("ACTIVE", saved.getStatus());
        Assert.assertEquals(repositories.tasks.get(0).getOutputRef(), saved.getSummaryRef());
        Assert.assertTrue(repositories.payloads.get(saved.getSummaryRef()).getContent().contains("currentTask"));
    }

    @Test
    public void session_task_summary_worker_uses_dynamic_summary_window() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType("SESSION_TASK_SUMMARY")
                .runId("run-1")
                .sessionId("session-1")
                .turnId("turn-50")
                .status("PENDING")
                .build());
        for (int i = 1; i <= 50; i++) {
            repositories.turnSummaries.add(AgentTurnSummaryEntity.builder()
                    .summaryId("turn-summary-" + i)
                    .turnId("turn-" + i)
                    .sessionId("session-1")
                    .userId("user-1")
                    .summaryRef("payload-summary-" + i)
                    .status("ACTIVE")
                    .build());
            repositories.payloads.put("payload-summary-" + i, AgentPayloadEntity.builder()
                    .payloadId("payload-summary-" + i)
                    .content("summary " + i)
                    .build());
        }
        RecordingSessionTaskSummaryNodeService nodeService = new RecordingSessionTaskSummaryNodeService();
        SessionTaskSummaryGcWorker worker = new SessionTaskSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                nodeService,
                30);

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals(35, nodeService.lastInput.getSummaries().size());
    }

    private static class StubSessionTaskSummaryNodeService extends SessionTaskSummaryNodeService {
        private StubSessionTaskSummaryNodeService() {
            super(null);
        }

        @Override
        public SessionTaskSummaryOutputVO summarize(yhx.com.domain.agent.model.valobj.memory.SessionTaskSummaryInputVO input,
                                                    String agentId,
                                                    yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            return SessionTaskSummaryOutputVO.builder()
                    .shouldUpdate(true)
                    .mainTasks(List.of("Redesign AutoAgent memory system"))
                    .currentTask("Complete session task summary GC")
                    .importantDecisions(List.of("Use MySQL for session task summaries"))
                    .latestProgress(List.of("Session task summary persistence foundation exists"))
                    .openQuestions(List.of())
                    .obsoleteTasks(List.of())
                    .build();
        }
    }

    private static class RecordingSessionTaskSummaryNodeService extends StubSessionTaskSummaryNodeService {
        private yhx.com.domain.agent.model.valobj.memory.SessionTaskSummaryInputVO lastInput;

        @Override
        public SessionTaskSummaryOutputVO summarize(yhx.com.domain.agent.model.valobj.memory.SessionTaskSummaryInputVO input,
                                                    String agentId,
                                                    yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            this.lastInput = input;
            return super.summarize(input, agentId, profile);
        }
    }

    private static class FakeRepositories implements ITurnSummaryRepository, IMemoryTaskRepository, IPayloadRepository, ISessionTaskSummaryRepository {
        private final List<AgentTurnSummaryEntity> turnSummaries = new ArrayList<>();
        private final List<AgentMemoryTaskEntity> tasks = new ArrayList<>();
        private final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        private final List<AgentSessionTaskSummaryEntity> sessionTaskSummaries = new ArrayList<>();

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
            return turnSummaries.stream().filter(summary -> turnIds.contains(summary.getTurnId())).toList();
        }

        @Override
        public List<AgentTurnSummaryEntity> listRecentActiveSummaries(String sessionId, int limit) {
            return turnSummaries.stream()
                    .filter(summary -> sessionId.equals(summary.getSessionId()))
                    .filter(summary -> "ACTIVE".equals(summary.getStatus()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public int countActiveSummaries(String sessionId) {
            return (int) turnSummaries.stream()
                    .filter(summary -> sessionId.equals(summary.getSessionId()))
                    .filter(summary -> "ACTIVE".equals(summary.getStatus()))
                    .count();
        }

        @Override
        public void markSummariesRolledUp(List<String> summaryIds) {
        }

        @Override
        public String createTask(AgentMemoryTaskEntity task) {
            if (task.getTaskId() == null) {
                task.setTaskId("task-" + (tasks.size() + 1));
            }
            tasks.add(task);
            return task.getTaskId();
        }

        @Override
        public Optional<AgentMemoryTaskEntity> findByTaskId(String taskId) {
            return tasks.stream().filter(task -> taskId.equals(task.getTaskId())).findFirst();
        }

        @Override
        public boolean hasOpenTask(String taskType, String sessionId) {
            return tasks.stream()
                    .anyMatch(task -> taskType.equals(task.getTaskType())
                            && sessionId.equals(task.getSessionId())
                            && ("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())));
        }

        @Override
        public List<AgentMemoryTaskEntity> listRetryableFailedTasks(int maxAttempts, int limit) {
            return List.of();
        }

        @Override
        public List<AgentMemoryTaskEntity> listTasks(String status, int limit) {
            return List.of();
        }

        @Override
        public void markRunning(String taskId) {
            findByTaskId(taskId).ifPresent(task -> task.setStatus("RUNNING"));
        }

        @Override
        public void markSucceeded(String taskId, String outputRef) {
            findByTaskId(taskId).ifPresent(task -> {
                task.setStatus("SUCCEEDED");
                task.setOutputRef(outputRef);
            });
        }

        @Override
        public void markFailed(String taskId, String failureCode, String failureMessage) {
            findByTaskId(taskId).ifPresent(task -> {
                task.setStatus("FAILED");
                task.setFailureCode(failureCode);
                task.setFailureMessage(failureMessage);
            });
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
        public String saveSummary(AgentSessionTaskSummaryEntity summary) {
            if (summary.getSummaryId() == null) {
                summary.setSummaryId("session-task-summary-" + (sessionTaskSummaries.size() + 1));
            }
            sessionTaskSummaries.add(summary);
            return summary.getSummaryId();
        }

        @Override
        public Optional<AgentSessionTaskSummaryEntity> findActiveBySessionId(String sessionId) {
            return sessionTaskSummaries.stream()
                    .filter(summary -> sessionId.equals(summary.getSessionId()))
                    .filter(summary -> "ACTIVE".equals(summary.getStatus()))
                    .findFirst();
        }

        @Override
        public int nextVersionNo(String sessionId) {
            return sessionTaskSummaries.size() + 1;
        }

        @Override
        public void markActiveSuperseded(String sessionId) {
            sessionTaskSummaries.stream()
                    .filter(summary -> sessionId.equals(summary.getSessionId()))
                    .filter(summary -> "ACTIVE".equals(summary.getStatus()))
                    .forEach(summary -> summary.setStatus("SUPERSEDED"));
        }
    }
}
