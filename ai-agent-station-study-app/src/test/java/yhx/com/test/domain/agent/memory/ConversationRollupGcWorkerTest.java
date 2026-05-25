package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentVectorIndexEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.ConversationRollupOutputVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.service.memory.MemoryManager;
import yhx.com.domain.agent.service.memory.MemoryVectorIndexingService;
import yhx.com.domain.agent.service.memory.gc.worker.ConversationRollupGcWorker;
import yhx.com.domain.agent.service.node.conversationrollup.ConversationRollupNodeService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConversationRollupGcWorkerTest {

    @Test
    public void conversation_rollup_worker_saves_summary_and_indexes_vector() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType("CONVERSATION_ROLLUP")
                .sessionId("session-1")
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
        repositories.payloads.put("payload-summary-1", AgentPayloadEntity.builder().payloadId("payload-summary-1").content("User planned memory recall architecture.").build());
        repositories.payloads.put("payload-summary-2", AgentPayloadEntity.builder().payloadId("payload-summary-2").content("Agent implemented vector indexing foundation.").build());
        ConversationRollupGcWorker worker = new ConversationRollupGcWorker(repositories,
                repositories,
                repositories,
                new MemoryManager(repositories, new yhx.com.domain.agent.service.memory.MemoryCandidatePreselector(),
                        new MemoryVectorIndexingService(repositories, repositories, repositories)),
                new StubConversationRollupNodeService(),
                8);

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals(1, repositories.conversationSummaries.size());
        Assert.assertTrue(repositories.payloads.get(repositories.conversationSummaries.get(0).getSummaryRef()).getContent().contains("memory recall architecture"));
        Assert.assertEquals(1, repositories.vectorRecords.size());
        Assert.assertEquals(VectorCollectionTypeEnumVO.CONVERSATION_SUMMARY, repositories.vectorRecords.get(0).getCollectionType());
        Assert.assertEquals("ROLLED_UP", repositories.turnSummaries.get(0).getStatus());
        Assert.assertEquals("ROLLED_UP", repositories.turnSummaries.get(1).getStatus());
    }

    private static class StubConversationRollupNodeService extends ConversationRollupNodeService {
        private StubConversationRollupNodeService() {
            super(null);
        }

        @Override
        public ConversationRollupOutputVO summarize(yhx.com.domain.agent.model.valobj.memory.ConversationRollupInputVO input,
                                                    String agentId,
                                                    yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            return ConversationRollupOutputVO.builder()
                    .summary("Rolling summary: user planned memory recall architecture and agent implemented vector indexing foundation.")
                    .build();
        }
    }

    private static class FakeRepositories implements ITurnSummaryRepository, IMemoryTaskRepository, IPayloadRepository, IMemoryRepository, IVectorMemoryRepository, IVectorIndexRepository {
        private final List<AgentTurnSummaryEntity> turnSummaries = new ArrayList<>();
        private final List<AgentMemoryTaskEntity> tasks = new ArrayList<>();
        private final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        private final List<AgentConversationSummaryEntity> conversationSummaries = new ArrayList<>();
        private final List<VectorIndexRecordVO> vectorRecords = new ArrayList<>();
        private final List<AgentVectorIndexEntity> vectorIndexes = new ArrayList<>();

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
        public void markSummariesRolledUp(List<String> summaryIds) {
            turnSummaries.stream()
                    .filter(summary -> summaryIds.contains(summary.getSummaryId()))
                    .forEach(summary -> summary.setStatus("ROLLED_UP"));
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
            return List.of();
        }

        @Override
        public Optional<AgentMemoryEntity> findMemory(String memoryId) {
            return Optional.empty();
        }

        @Override
        public String saveConversationSummary(AgentConversationSummaryEntity summary) {
            if (summary.getSummaryId() == null) {
                summary.setSummaryId("conversation-summary-" + (conversationSummaries.size() + 1));
            }
            conversationSummaries.add(summary);
            return summary.getSummaryId();
        }

        @Override
        public String saveLongTermMemory(AgentMemoryEntity memory) {
            return memory.getMemoryId();
        }

        @Override
        public String recordMemoryEvent(AgentMemoryEventEntity event) {
            return event.getEventId();
        }

        @Override
        public String upsert(VectorIndexRecordVO record) {
            vectorRecords.add(record);
            return "vector-" + vectorRecords.size();
        }

        @Override
        public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
            return List.of();
        }

        @Override
        public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
        }

        @Override
        public String saveOrUpdate(AgentVectorIndexEntity index) {
            vectorIndexes.add(index);
            return "index-" + vectorIndexes.size();
        }

        @Override
        public Optional<AgentVectorIndexEntity> findBySource(String collectionType, String sourceType, String sourceId) {
            return vectorIndexes.stream()
                    .filter(index -> collectionType.equals(index.getCollectionType())
                            && sourceType.equals(index.getSourceType())
                            && sourceId.equals(index.getSourceId()))
                    .findFirst();
        }

        @Override
        public void markDisabled(String collectionType, String sourceType, String sourceId) {
        }
    }
}
