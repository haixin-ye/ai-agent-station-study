package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceActionVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceOutputVO;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGovernanceGcWorker;
import yhx.com.domain.agent.service.node.memorygovernance.MemoryGovernanceNodeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MemoryGovernanceGcWorkerTest {

    @Test
    public void governance_worker_ignores_unknown_memory_ids_without_failing_task() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType("MEMORY_GOVERNANCE")
                .sessionId("session-1")
                .runId("run-1")
                .status("PENDING")
                .build());
        repositories.memories.add(AgentMemoryEntity.builder()
                .memoryId("memory-1")
                .sessionId("session-1")
                .memoryType("LONG_TERM_MEMORY")
                .summary("User is building AutoAgent memory.")
                .status("ACTIVE")
                .build());
        MemoryGovernanceGcWorker worker = new MemoryGovernanceGcWorker(repositories,
                repositories,
                null,
                null,
                new StubMemoryGovernanceNodeService(),
                20);

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals("ACTIVE", repositories.memories.get(0).getStatus());
        Assert.assertTrue(repositories.events.isEmpty());
    }

    private static class StubMemoryGovernanceNodeService extends MemoryGovernanceNodeService {
        private StubMemoryGovernanceNodeService() {
            super(null);
        }

        @Override
        public MemoryGovernanceOutputVO govern(yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceInputVO input,
                                               String agentId,
                                               yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            return MemoryGovernanceOutputVO.builder()
                    .actions(List.of(MemoryGovernanceActionVO.builder()
                            .action("DISABLE")
                            .memoryId("missing-memory")
                            .reason("Unknown id should be ignored safely.")
                            .build()))
                    .build();
        }
    }

    private static class FakeRepositories implements IMemoryRepository, IMemoryTaskRepository {
        private final List<AgentMemoryEntity> memories = new ArrayList<>();
        private final List<AgentMemoryTaskEntity> tasks = new ArrayList<>();
        private final List<AgentMemoryEventEntity> events = new ArrayList<>();

        @Override
        public List<AgentMemoryEntity> findMemoryCandidates(String userId, String sessionId, String query, int limit) {
            return memories;
        }

        @Override
        public Optional<AgentMemoryEntity> findMemory(String memoryId) {
            return memories.stream().filter(memory -> memoryId.equals(memory.getMemoryId())).findFirst();
        }

        @Override
        public List<AgentMemoryEntity> listActiveMemoriesBySession(String sessionId, int limit) {
            return memories.stream()
                    .filter(memory -> sessionId.equals(memory.getSessionId()))
                    .filter(memory -> "ACTIVE".equals(memory.getStatus()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void updateMemoryLifecycle(String memoryId, String status, String supersededBy) {
            findMemory(memoryId).ifPresent(memory -> {
                memory.setStatus(status);
                memory.setSupersededBy(supersededBy);
            });
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
            events.add(event);
            return event.getEventId();
        }

        @Override
        public String createTask(AgentMemoryTaskEntity task) {
            tasks.add(task);
            return task.getTaskId();
        }

        @Override
        public Optional<AgentMemoryTaskEntity> findByTaskId(String taskId) {
            return tasks.stream().filter(task -> taskId.equals(task.getTaskId())).findFirst();
        }

        @Override
        public boolean hasOpenTask(String taskType, String sessionId) {
            return false;
        }

        @Override
        public List<AgentMemoryTaskEntity> listRetryableFailedTasks(int maxAttempts, int limit) {
            return List.of();
        }

        @Override
        public List<AgentMemoryTaskEntity> listTasks(String status, int limit) {
            return tasks;
        }

        @Override
        public void markRunning(String taskId) {
            findByTaskId(taskId).ifPresent(task -> task.setStatus("RUNNING"));
        }

        @Override
        public void markSucceeded(String taskId, String outputRef) {
            findByTaskId(taskId).ifPresent(task -> task.setStatus("SUCCEEDED"));
        }

        @Override
        public void markFailed(String taskId, String failureCode, String failureMessage) {
            findByTaskId(taskId).ifPresent(task -> {
                task.setStatus("FAILED");
                task.setFailureCode(failureCode);
                task.setFailureMessage(failureMessage);
            });
        }
    }
}
