package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.valobj.memory.ExtractedMemoryVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryExtractionOutputVO;
import yhx.com.domain.agent.service.memory.MemoryManager;
import yhx.com.domain.agent.service.memory.gc.worker.LongTermMemoryGcWorker;
import yhx.com.domain.agent.service.node.memoryextraction.MemoryExtractionNodeService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LongTermMemoryGcWorkerTest {

    @Test
    public void long_term_memory_worker_saves_memory_and_user_preference() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType("LONG_TERM_MEMORY_EXTRACTION")
                .turnId("turn-1")
                .inputRef("payload-summary")
                .status("PENDING")
                .build());
        repositories.turns.put("turn-1", AgentTurnEntity.builder()
                .turnId("turn-1")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("我正在开发 AutoAgent 记忆系统，我喜欢详细中文解释。").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("已记录你的项目目标和回答偏好。").build());
        repositories.payloads.put("payload-summary", AgentPayloadEntity.builder().payloadId("payload-summary").content("{\"summary\":\"User stated project goal and answer preference.\"}").build());
        LongTermMemoryGcWorker worker = new LongTermMemoryGcWorker(repositories,
                repositories,
                repositories,
                new MemoryManager(repositories),
                new StubMemoryExtractionNodeService());

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals(2, repositories.memories.size());
        Assert.assertEquals("LONG_TERM_MEMORY", repositories.memories.get(0).getMemoryType());
        Assert.assertEquals("USER_PREFERENCE", repositories.memories.get(1).getMemoryType());
        Assert.assertTrue(repositories.memories.get(0).getSummary().contains("AutoAgent memory system"));
        Assert.assertTrue(repositories.memories.get(1).getSummary().contains("detailed Chinese"));
        Assert.assertEquals("ACTIVE", repositories.memories.get(0).getStatus());
        Assert.assertEquals("run-1", repositories.memories.get(0).getSourceRunId());
        Assert.assertEquals("turn-1", repositories.memories.get(0).getSourceTurnId());
        Assert.assertNotNull(repositories.memories.get(0).getLastSeenAt());
        Assert.assertNotNull(repositories.memories.get(0).getContentRef());
        Assert.assertTrue(repositories.memories.get(0).getMetadataJson().contains("recallText"));
        Assert.assertTrue(repositories.memories.get(1).getMetadataJson().contains("用户偏好"));
    }

    @Test
    public void long_term_memory_worker_succeeds_without_saving_memory_when_extraction_is_empty() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType("LONG_TERM_MEMORY_EXTRACTION")
                .turnId("turn-1")
                .inputRef("payload-summary")
                .status("PENDING")
                .build());
        repositories.turns.put("turn-1", AgentTurnEntity.builder()
                .turnId("turn-1")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("What is HTTP?").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("HTTP is an application-layer protocol.").build());
        repositories.payloads.put("payload-summary", AgentPayloadEntity.builder().payloadId("payload-summary").content("{\"summary\":\"User asked a public-knowledge question about HTTP.\"}").build());
        LongTermMemoryGcWorker worker = new LongTermMemoryGcWorker(repositories,
                repositories,
                repositories,
                new MemoryManager(repositories),
                new EmptyMemoryExtractionNodeService());

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertTrue(repositories.memories.isEmpty());
    }

    @Test
    public void long_term_memory_worker_falls_back_for_explicit_user_display_name() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType("LONG_TERM_MEMORY_EXTRACTION")
                .turnId("turn-1")
                .inputRef("payload-summary")
                .status("PENDING")
                .build());
        repositories.turns.put("turn-1", AgentTurnEntity.builder()
                .turnId("turn-1")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("\u6211\u53eb\u5c0f\u5e05\u54e5").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("\u4f60\u597d\uff0c\u5c0f\u5e05\u54e5\uff01").build());
        repositories.payloads.put("payload-summary", AgentPayloadEntity.builder().payloadId("payload-summary").content("{\"summary\":\"\u7528\u6237\u81ea\u6211\u4ecb\u7ecd\u4e3a\u5c0f\u5e05\u54e5\"}").build());
        LongTermMemoryGcWorker worker = new LongTermMemoryGcWorker(repositories,
                repositories,
                repositories,
                new MemoryManager(repositories),
                new EmptyMemoryExtractionNodeService());

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals(1, repositories.memories.size());
        Assert.assertEquals("LONG_TERM_MEMORY", repositories.memories.get(0).getMemoryType());
        Assert.assertTrue(repositories.memories.get(0).getSummary().contains("\u5c0f\u5e05\u54e5"));
        Assert.assertNotNull(repositories.memories.get(0).getContentRef());
        Assert.assertTrue(repositories.memories.get(0).getMetadataJson().contains("\u6211\u53eb\u4ec0\u4e48"));
    }

    @Test
    public void long_term_memory_worker_saves_human_content_and_keeps_recall_text_in_metadata() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType("LONG_TERM_MEMORY_EXTRACTION")
                .turnId("turn-1")
                .inputRef("payload-summary")
                .status("PENDING")
                .build());
        repositories.turns.put("turn-1", AgentTurnEntity.builder()
                .turnId("turn-1")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("我叫张三。").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("好的。").build());
        repositories.payloads.put("payload-summary", AgentPayloadEntity.builder().payloadId("payload-summary").content("{\"summary\":\"User provided their name.\"}").build());
        LongTermMemoryGcWorker worker = new LongTermMemoryGcWorker(repositories,
                repositories,
                repositories,
                new MemoryManager(repositories),
                new RecallTextMemoryExtractionNodeService());

        worker.handle("task-1");

        Assert.assertEquals("用户姓名是张三。", repositories.memories.get(0).getSummary());
        String contentPayloadRef = repositories.memories.get(0).getContentRef();
        Assert.assertNotNull(contentPayloadRef);
        Assert.assertEquals("User explicitly said their name is Zhang San.",
                repositories.payloads.get(contentPayloadRef).getContent());
        Assert.assertTrue(repositories.memories.get(0).getMetadataJson().contains("recallText"));
        Assert.assertTrue(repositories.memories.get(0).getMetadataJson().contains("张三"));
        Assert.assertFalse(repositories.payloads.get(contentPayloadRef).getContent().contains("用户的名字、姓名、称呼"));
    }

    private static class StubMemoryExtractionNodeService extends MemoryExtractionNodeService {
        private StubMemoryExtractionNodeService() {
            super(null);
        }

        @Override
        public MemoryExtractionOutputVO extract(yhx.com.domain.agent.model.valobj.memory.MemoryExtractionInputVO input,
                                                String agentId,
                                                yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            return MemoryExtractionOutputVO.builder()
                    .memories(List.of(
                            ExtractedMemoryVO.builder()
                                    .memoryType("LONG_TERM_MEMORY")
                                    .summary("User is developing an AutoAgent memory system.")
                                    .content("The user is developing an AutoAgent memory system and expects memory-related work to align with that project.")
                                    .score(new BigDecimal("0.85"))
                                    .build(),
                            ExtractedMemoryVO.builder()
                                    .memoryType("USER_PREFERENCE")
                                    .summary("User prefers detailed Chinese engineering explanations.")
                                    .score(new BigDecimal("0.90"))
                                    .build()))
                    .build();
        }
    }

    private static class EmptyMemoryExtractionNodeService extends MemoryExtractionNodeService {
        private EmptyMemoryExtractionNodeService() {
            super(null);
        }

        @Override
        public MemoryExtractionOutputVO extract(yhx.com.domain.agent.model.valobj.memory.MemoryExtractionInputVO input,
                                                String agentId,
                                                yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            return MemoryExtractionOutputVO.builder().memories(List.of()).build();
        }
    }

    private static class RecallTextMemoryExtractionNodeService extends MemoryExtractionNodeService {
        private RecallTextMemoryExtractionNodeService() {
            super(null);
        }

        @Override
        public MemoryExtractionOutputVO extract(yhx.com.domain.agent.model.valobj.memory.MemoryExtractionInputVO input,
                                                String agentId,
                                                yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            return MemoryExtractionOutputVO.builder()
                    .memories(List.of(ExtractedMemoryVO.builder()
                            .memoryType("LONG_TERM_MEMORY")
                            .summary("用户姓名是张三。")
                            .content("User explicitly said their name is Zhang San.")
                            .recallText("用户的名字、姓名、称呼、个人姓名是张三。用户提到“我的名字”时指张三。")
                            .score(new BigDecimal("0.95"))
                            .build()))
                    .build();
        }
    }

    private static class FakeRepositories implements ITurnRepository, IMemoryTaskRepository, IPayloadRepository, IMemoryRepository {
        private final Map<String, AgentTurnEntity> turns = new LinkedHashMap<>();
        private final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        private final List<AgentMemoryTaskEntity> tasks = new ArrayList<>();
        private final List<AgentMemoryEntity> memories = new ArrayList<>();

        @Override
        public String saveCompletedTurn(AgentTurnEntity turn) {
            turns.put(turn.getTurnId(), turn);
            return turn.getTurnId();
        }

        @Override
        public Optional<AgentTurnEntity> findByTurnId(String turnId) {
            return Optional.ofNullable(turns.get(turnId));
        }

        @Override
        public Optional<AgentTurnEntity> findByRunId(String runId) {
            return turns.values().stream().filter(turn -> runId.equals(turn.getRunId())).findFirst();
        }

        @Override
        public List<AgentTurnEntity> listRecentCompletedTurns(String sessionId, int limit) {
            return List.of();
        }

        @Override
        public List<AgentTurnEntity> listCompletedTurnsBefore(String sessionId, Long beforeTurnNo, int limit) {
            return List.of();
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
            return tasks.stream()
                    .filter(task -> "FAILED".equals(task.getStatus()))
                    .filter(task -> task.getAttemptCount() == null || task.getAttemptCount() < maxAttempts)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<AgentMemoryTaskEntity> listTasks(String status, int limit) {
            return tasks.stream()
                    .filter(task -> status == null || status.isBlank() || status.equals(task.getStatus()))
                    .limit(limit)
                    .toList();
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
        public List<AgentMemoryEntity> findMemoryCandidates(String userId, String sessionId, String query, int limit) {
            return memories;
        }

        @Override
        public Optional<AgentMemoryEntity> findMemory(String memoryId) {
            return memories.stream().filter(memory -> memoryId.equals(memory.getMemoryId())).findFirst();
        }

        @Override
        public String saveConversationSummary(yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity summary) {
            return summary.getSummaryId();
        }

        @Override
        public String saveLongTermMemory(AgentMemoryEntity memory) {
            if (memory.getMemoryId() == null) {
                memory.setMemoryId("memory-" + (memories.size() + 1));
            }
            memories.add(memory);
            return memory.getMemoryId();
        }

        @Override
        public String recordMemoryEvent(AgentMemoryEventEntity event) {
            return event.getEventId();
        }
    }
}
