package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryOutputVO;
import yhx.com.domain.agent.service.memory.gc.MemoryGcFollowupScheduler;
import yhx.com.domain.agent.service.memory.gc.MemoryGcTaskDispatcher;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGcTaskWorker;
import yhx.com.domain.agent.service.memory.gc.worker.TurnSummaryGcWorker;
import yhx.com.domain.agent.service.node.turnsummary.TurnSummaryNodeService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TurnSummaryGcWorkerTest {

    @Test
    public void turn_summary_worker_dispatches_long_term_memory_extraction_when_required() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-1", AgentTurnEntity.builder()
                .turnId("turn-1")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("我希望回答详细一些").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("以后会默认更详细。").build());
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-1")
                .status("PENDING")
                .build());
        RecordingWorker extractionWorker = new RecordingWorker(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name());
        MemoryGcFollowupScheduler scheduler = new MemoryGcFollowupScheduler(repositories,
                new MemoryGcTaskDispatcher(Runnable::run, List.of(extractionWorker)));
        TurnSummaryGcWorker worker = new TurnSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                new StubTurnSummaryNodeService(),
                null,
                scheduler);

        worker.handleTurn("task-1", "turn-1");

        Assert.assertEquals(2, repositories.tasks.size());
        Assert.assertEquals(MemoryTaskTypeEnumVO.TURN_SUMMARY.name(), repositories.tasks.get(0).getTaskType());
        Assert.assertEquals(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name(), repositories.tasks.get(1).getTaskType());
        Assert.assertEquals(repositories.tasks.get(0).getOutputRef(), repositories.tasks.get(1).getInputRef());
        Assert.assertEquals(List.of("task-2"), extractionWorker.handledTaskIds);
    }

    private static class StubTurnSummaryNodeService extends TurnSummaryNodeService {
        private StubTurnSummaryNodeService() {
            super(null);
        }

        @Override
        public TurnSummaryOutputVO summarize(yhx.com.domain.agent.model.valobj.memory.TurnSummaryInputVO input,
                                             String agentId,
                                             yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            return TurnSummaryOutputVO.builder()
                    .summary("User stated a stable answer style preference.")
                    .intent("set preference")
                    .topics(List.of("answer style"))
                    .entities(List.of())
                    .artifactRefs(List.of())
                    .importanceScore(new BigDecimal("0.80"))
                    .requiresLongTermExtraction(true)
                    .build();
        }
    }

    private static class RecordingWorker implements MemoryGcTaskWorker {
        private final String taskType;
        private final List<String> handledTaskIds = new ArrayList<>();

        private RecordingWorker(String taskType) {
            this.taskType = taskType;
        }

        @Override
        public String taskType() {
            return taskType;
        }

        @Override
        public void handle(String taskId) {
            handledTaskIds.add(taskId);
        }
    }

    private static class FakeRepositories implements ITurnRepository, ITurnSummaryRepository, IMemoryTaskRepository, IPayloadRepository {
        private final Map<String, AgentTurnEntity> turns = new LinkedHashMap<>();
        private final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        private final List<AgentTurnSummaryEntity> summaries = new ArrayList<>();
        private final List<AgentMemoryTaskEntity> tasks = new ArrayList<>();

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
        public String saveSummary(AgentTurnSummaryEntity summary) {
            summaries.add(summary);
            return summary.getSummaryId();
        }

        @Override
        public Optional<AgentTurnSummaryEntity> findSummaryById(String summaryId) {
            return summaries.stream().filter(summary -> summaryId.equals(summary.getSummaryId())).findFirst();
        }

        @Override
        public Optional<AgentTurnSummaryEntity> findSummaryByTurnId(String turnId) {
            return summaries.stream().filter(summary -> turnId.equals(summary.getTurnId())).findFirst();
        }

        @Override
        public List<AgentTurnSummaryEntity> listByTurnIds(List<String> turnIds) {
            return summaries.stream().filter(summary -> turnIds.contains(summary.getTurnId())).toList();
        }

        @Override
        public List<AgentTurnSummaryEntity> listRecentActiveSummaries(String sessionId, int limit) {
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
    }
}
