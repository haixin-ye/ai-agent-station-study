package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceActionVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceOutputVO;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGovernanceGcWorker;
import yhx.com.domain.agent.service.node.memorygovernance.MemoryGovernanceNodeService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

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

    @Test
    public void governance_worker_stores_action_json_as_payload_ref() {
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
                .summary("Old preference.")
                .status("ACTIVE")
                .build());
        MemoryGovernanceGcWorker worker = new MemoryGovernanceGcWorker(repositories,
                repositories,
                repositories,
                null,
                null,
                new DisableMemoryGovernanceNodeService(),
                20);

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals("DISABLED", repositories.memories.get(0).getStatus());
        Assert.assertEquals(1, repositories.events.size());
        String payloadRef = repositories.events.get(0).getPayloadRef();
        Assert.assertNotNull(payloadRef);
        Assert.assertTrue(payloadRef.startsWith("payload-"));
        Assert.assertTrue(repositories.payloads.get(payloadRef).getContent().contains("\"memoryId\":\"memory-1\""));
    }

    @Test
    public void governance_worker_reads_active_memories_globally_not_only_current_session() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType("MEMORY_GOVERNANCE")
                .sessionId("session-1")
                .runId("run-1")
                .status("PENDING")
                .build());
        repositories.memories.add(AgentMemoryEntity.builder()
                .memoryId("memory-other-session")
                .sessionId("session-2")
                .memoryType("LONG_TERM_MEMORY")
                .summary("Cross-session obsolete memory.")
                .status("ACTIVE")
                .build());
        MemoryGovernanceGcWorker worker = new MemoryGovernanceGcWorker(repositories,
                repositories,
                repositories,
                null,
                null,
                new CrossSessionDisableMemoryGovernanceNodeService(),
                20);

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals("DISABLED", repositories.memories.get(0).getStatus());
    }

    @Test
    public void governance_worker_provides_content_and_timestamps_so_node_can_supersede_old_user_profile() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType("MEMORY_GOVERNANCE")
                .sessionId("session-1")
                .runId("run-1")
                .status("PENDING")
                .build());
        repositories.payloads.put("payload-old-name", AgentPayloadEntity.builder()
                .payloadId("payload-old-name")
                .content("用户的称呼或昵称是小美。")
                .build());
        repositories.payloads.put("payload-new-name", AgentPayloadEntity.builder()
                .payloadId("payload-new-name")
                .content("用户明确更正：真实姓名是小菊，不是小美。")
                .build());
        repositories.memories.add(AgentMemoryEntity.builder()
                .memoryId("memory-old-name")
                .memoryType("LONG_TERM_MEMORY")
                .summary("用户的称呼或昵称是小美。")
                .contentRef("payload-old-name")
                .status("ACTIVE")
                .createdAt(LocalDateTime.parse("2026-05-27T10:00:00"))
                .updatedAt(LocalDateTime.parse("2026-05-27T10:00:00"))
                .build());
        repositories.memories.add(AgentMemoryEntity.builder()
                .memoryId("memory-new-name")
                .memoryType("LONG_TERM_MEMORY")
                .summary("用户的真实姓名是小菊。")
                .contentRef("payload-new-name")
                .status("ACTIVE")
                .createdAt(LocalDateTime.parse("2026-05-27T16:00:00"))
                .updatedAt(LocalDateTime.parse("2026-05-27T16:00:00"))
                .build());
        MemoryGovernanceGcWorker worker = new MemoryGovernanceGcWorker(repositories,
                repositories,
                repositories,
                null,
                null,
                new SupersedeOldNameGovernanceNodeService(),
                20);

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals("SUPERSEDED", repositories.memories.get(0).getStatus());
        Assert.assertEquals("memory-new-name", repositories.memories.get(0).getSupersededBy());
        Assert.assertEquals("ACTIVE", repositories.memories.get(1).getStatus());
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

    private static class DisableMemoryGovernanceNodeService extends MemoryGovernanceNodeService {
        private DisableMemoryGovernanceNodeService() {
            super(null);
        }

        @Override
        public MemoryGovernanceOutputVO govern(yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceInputVO input,
                                               String agentId,
                                               yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            return MemoryGovernanceOutputVO.builder()
                    .actions(List.of(MemoryGovernanceActionVO.builder()
                            .action("DISABLE")
                            .memoryId("memory-1")
                            .reason("Obsolete.")
                            .build()))
                    .build();
        }
    }

    private static class CrossSessionDisableMemoryGovernanceNodeService extends MemoryGovernanceNodeService {
        private CrossSessionDisableMemoryGovernanceNodeService() {
            super(null);
        }

        @Override
        public MemoryGovernanceOutputVO govern(yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceInputVO input,
                                               String agentId,
                                               yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            Assert.assertTrue(input.getMemories().stream()
                    .anyMatch(memory -> "memory-other-session".equals(memory.getMemoryId())));
            return MemoryGovernanceOutputVO.builder()
                    .actions(List.of(MemoryGovernanceActionVO.builder()
                            .action("DISABLE")
                            .memoryId("memory-other-session")
                            .reason("Obsolete.")
                            .build()))
                    .build();
        }
    }

    private static class SupersedeOldNameGovernanceNodeService extends MemoryGovernanceNodeService {
        private SupersedeOldNameGovernanceNodeService() {
            super(null);
        }

        @Override
        public MemoryGovernanceOutputVO govern(yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceInputVO input,
                                               String agentId,
                                               yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            Assert.assertTrue(input.getMemories().stream()
                    .anyMatch(memory -> "memory-old-name".equals(memory.getMemoryId())
                            && memory.getContent() != null
                            && memory.getCreatedAt() != null));
            Assert.assertTrue(input.getMemories().stream()
                    .anyMatch(memory -> "memory-new-name".equals(memory.getMemoryId())
                            && memory.getContent() != null
                            && memory.getCreatedAt() != null));
            return MemoryGovernanceOutputVO.builder()
                    .actions(List.of(MemoryGovernanceActionVO.builder()
                            .action("SUPERSEDE")
                            .memoryId("memory-old-name")
                            .targetMemoryId("memory-new-name")
                            .reason("用户后续明确更正真实姓名，新事实覆盖旧称呼。")
                            .build()))
                    .build();
        }
    }

    private static class FakeRepositories implements IMemoryRepository, IMemoryTaskRepository, IPayloadRepository {
        private final List<AgentMemoryEntity> memories = new ArrayList<>();
        private final List<AgentMemoryTaskEntity> tasks = new ArrayList<>();
        private final List<AgentMemoryEventEntity> events = new ArrayList<>();
        private final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();

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
        public List<AgentMemoryEntity> listActiveMemoriesForGovernance(int limit) {
            return memories.stream()
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
