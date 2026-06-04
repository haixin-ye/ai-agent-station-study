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
import yhx.com.domain.agent.service.memory.MemoryVectorIndexingService;
import yhx.com.domain.agent.service.memory.gc.MemoryGcFollowupScheduler;
import yhx.com.domain.agent.service.memory.gc.MemoryGcTaskDispatcher;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGcTaskWorker;
import yhx.com.domain.agent.service.memory.gc.worker.TurnSummaryGcWorker;
import yhx.com.domain.agent.service.node.turnsummary.TurnSummaryNodeService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Test
    public void turn_summary_worker_dispatches_session_task_summary_when_active_summary_threshold_is_reached() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-2", AgentTurnEntity.builder()
                .turnId("turn-2")
                .runId("run-2")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("Expand the previous MCP article.").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("Expanded article content.").build());
        repositories.summaries.add(AgentTurnSummaryEntity.builder()
                .summaryId("turn-summary-1")
                .turnId("turn-1")
                .sessionId("session-1")
                .status("ACTIVE")
                .build());
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-2")
                .status("PENDING")
                .build());
        RecordingWorker rollupWorker = new RecordingWorker(MemoryTaskTypeEnumVO.SESSION_TASK_SUMMARY.name());
        MemoryGcFollowupScheduler scheduler = new MemoryGcFollowupScheduler(repositories,
                new MemoryGcTaskDispatcher(Runnable::run, List.of(rollupWorker)));
        TurnSummaryGcWorker worker = new TurnSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                new StubTurnSummaryNodeService(false),
                null,
                scheduler,
                2,
                15);

        worker.handleTurn("task-1", "turn-2");

        Assert.assertEquals(3, repositories.tasks.size());
        Assert.assertEquals(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name(), repositories.tasks.get(1).getTaskType());
        Assert.assertEquals(MemoryTaskTypeEnumVO.SESSION_TASK_SUMMARY.name(), repositories.tasks.get(2).getTaskType());
        Assert.assertEquals("session-1", repositories.tasks.get(2).getSessionId());
        Assert.assertEquals(List.of("task-3"), rollupWorker.handledTaskIds);
    }

    @Test
    public void turn_summary_worker_always_dispatches_long_term_extraction_after_summary() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-2", AgentTurnEntity.builder()
                .turnId("turn-2")
                .runId("run-2")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("What is HTTP?").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("HTTP is an application-layer protocol.").build());
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-2")
                .status("PENDING")
                .build());
        RecordingWorker extractionWorker = new RecordingWorker(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name());
        MemoryGcFollowupScheduler scheduler = new MemoryGcFollowupScheduler(repositories,
                new MemoryGcTaskDispatcher(Runnable::run, List.of(extractionWorker)));
        TurnSummaryGcWorker worker = new TurnSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                new StubTurnSummaryNodeService(false),
                null,
                scheduler);

        worker.handleTurn("task-1", "turn-2");

        Assert.assertEquals(2, repositories.tasks.size());
        Assert.assertEquals(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name(), repositories.tasks.get(1).getTaskType());
        Assert.assertEquals(repositories.tasks.get(0).getOutputRef(), repositories.tasks.get(1).getInputRef());
        Assert.assertEquals(List.of("task-2"), extractionWorker.handledTaskIds);
    }

    @Test
    public void turn_summary_worker_does_not_dispatch_duplicate_open_session_task_summary() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-2", AgentTurnEntity.builder()
                .turnId("turn-2")
                .runId("run-2")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("Expand the previous MCP article.").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("Expanded article content.").build());
        repositories.summaries.add(AgentTurnSummaryEntity.builder()
                .summaryId("turn-summary-1")
                .turnId("turn-1")
                .sessionId("session-1")
                .status("ACTIVE")
                .build());
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-2")
                .status("PENDING")
                .build());
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-rollup-open")
                .taskType(MemoryTaskTypeEnumVO.SESSION_TASK_SUMMARY.name())
                .sessionId("session-1")
                .status("PENDING")
                .build());
        RecordingWorker rollupWorker = new RecordingWorker(MemoryTaskTypeEnumVO.SESSION_TASK_SUMMARY.name());
        MemoryGcFollowupScheduler scheduler = new MemoryGcFollowupScheduler(repositories,
                new MemoryGcTaskDispatcher(Runnable::run, List.of(rollupWorker)));
        TurnSummaryGcWorker worker = new TurnSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                new StubTurnSummaryNodeService(false),
                null,
                scheduler,
                2,
                15);

        worker.handleTurn("task-1", "turn-2");

        Assert.assertEquals(3, repositories.tasks.size());
        Assert.assertEquals(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name(), repositories.tasks.get(2).getTaskType());
        Assert.assertTrue(rollupWorker.handledTaskIds.isEmpty());
    }

    @Test
    public void turn_summary_worker_dispatches_session_task_summary_every_five_turns_and_global_governance_every_ten_turns() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-10", AgentTurnEntity.builder()
                .turnId("turn-10")
                .runId("run-10")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("turn 10 user").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("turn 10 answer").build());
        for (int i = 1; i <= 9; i++) {
            repositories.summaries.add(AgentTurnSummaryEntity.builder()
                    .summaryId("turn-summary-" + i)
                    .turnId("turn-" + i)
                    .sessionId(i <= 4 ? "session-1" : "session-2")
                    .status("ACTIVE")
                    .build());
        }
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-10")
                .status("PENDING")
                .build());
        RecordingWorker sessionTaskWorker = new RecordingWorker(MemoryTaskTypeEnumVO.SESSION_TASK_SUMMARY.name());
        RecordingWorker governanceWorker = new RecordingWorker(MemoryTaskTypeEnumVO.MEMORY_GOVERNANCE.name());
        MemoryGcFollowupScheduler scheduler = new MemoryGcFollowupScheduler(repositories,
                new MemoryGcTaskDispatcher(Runnable::run, List.of(sessionTaskWorker, governanceWorker)));
        TurnSummaryGcWorker worker = new TurnSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                new StubTurnSummaryNodeService(false),
                null,
                scheduler,
                5,
                10);

        worker.handleTurn("task-1", "turn-10");

        Assert.assertEquals(4, repositories.tasks.size());
        Assert.assertEquals(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name(), repositories.tasks.get(1).getTaskType());
        Assert.assertEquals(MemoryTaskTypeEnumVO.SESSION_TASK_SUMMARY.name(), repositories.tasks.get(2).getTaskType());
        Assert.assertEquals(MemoryTaskTypeEnumVO.MEMORY_GOVERNANCE.name(), repositories.tasks.get(3).getTaskType());
        Assert.assertEquals(List.of("task-3"), sessionTaskWorker.handledTaskIds);
        Assert.assertEquals(List.of("task-4"), governanceWorker.handledTaskIds);
    }

    @Test
    public void turn_summary_worker_does_not_dispatch_global_governance_before_ten_active_summaries() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-8", AgentTurnEntity.builder()
                .turnId("turn-8")
                .runId("run-8")
                .sessionId("session-2")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("turn 8 user").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("turn 8 answer").build());
        for (int i = 1; i <= 7; i++) {
            repositories.summaries.add(AgentTurnSummaryEntity.builder()
                    .summaryId("turn-summary-" + i)
                    .turnId("turn-" + i)
                    .sessionId(i <= 4 ? "session-1" : "session-2")
                    .status("ACTIVE")
                    .build());
        }
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-8")
                .status("PENDING")
                .build());
        RecordingWorker governanceWorker = new RecordingWorker(MemoryTaskTypeEnumVO.MEMORY_GOVERNANCE.name());
        MemoryGcFollowupScheduler scheduler = new MemoryGcFollowupScheduler(repositories,
                new MemoryGcTaskDispatcher(Runnable::run, List.of(governanceWorker)));
        TurnSummaryGcWorker worker = new TurnSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                new StubTurnSummaryNodeService(false),
                null,
                scheduler,
                5,
                10);

        worker.handleTurn("task-1", "turn-8");

        Assert.assertEquals(2, repositories.tasks.size());
        Assert.assertEquals(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name(), repositories.tasks.get(1).getTaskType());
        Assert.assertTrue(governanceWorker.handledTaskIds.isEmpty());
    }

    @Test
    public void turn_summary_worker_dispatches_global_governance_when_only_existing_task_is_stale() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-5", AgentTurnEntity.builder()
                .turnId("turn-5")
                .runId("run-5")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("turn 5 user").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("turn 5 answer").build());
        for (int i = 1; i <= 4; i++) {
            repositories.summaries.add(AgentTurnSummaryEntity.builder()
                    .summaryId("turn-summary-" + i)
                    .turnId("turn-" + i)
                    .sessionId("session-1")
                    .status("ACTIVE")
                    .build());
        }
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-5")
                .status("PENDING")
                .build());
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-stale-governance")
                .taskType(MemoryTaskTypeEnumVO.MEMORY_GOVERNANCE.name())
                .turnId("turn-old")
                .status("RUNNING")
                .updatedAt(LocalDateTime.now().minusHours(2))
                .build());
        RecordingWorker governanceWorker = new RecordingWorker(MemoryTaskTypeEnumVO.MEMORY_GOVERNANCE.name());
        MemoryGcFollowupScheduler scheduler = new MemoryGcFollowupScheduler(repositories,
                new MemoryGcTaskDispatcher(Runnable::run, List.of(governanceWorker)));
        TurnSummaryGcWorker worker = new TurnSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                new StubTurnSummaryNodeService(false),
                null,
                scheduler,
                99,
                5,
                99);

        worker.handleTurn("task-1", "turn-5");

        Assert.assertEquals(4, repositories.tasks.size());
        Assert.assertEquals(MemoryTaskTypeEnumVO.MEMORY_GOVERNANCE.name(), repositories.tasks.get(3).getTaskType());
        Assert.assertEquals(List.of("task-4"), governanceWorker.handledTaskIds);
    }

    @Test
    public void turn_summary_worker_dispatches_summary_self_check_every_three_active_summaries() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-3", AgentTurnEntity.builder()
                .turnId("turn-3")
                .runId("run-3")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("turn 3 user").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("turn 3 answer").build());
        for (int i = 1; i <= 2; i++) {
            repositories.summaries.add(AgentTurnSummaryEntity.builder()
                    .summaryId("turn-summary-" + i)
                    .turnId("turn-" + i)
                    .sessionId("session-1")
                    .status("ACTIVE")
                    .build());
        }
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-3")
                .status("PENDING")
                .build());
        RecordingWorker selfCheckWorker = new RecordingWorker(MemoryTaskTypeEnumVO.TURN_SUMMARY_SELF_CHECK.name());
        MemoryGcFollowupScheduler scheduler = new MemoryGcFollowupScheduler(repositories,
                new MemoryGcTaskDispatcher(Runnable::run, List.of(selfCheckWorker)));
        TurnSummaryGcWorker worker = new TurnSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                new StubTurnSummaryNodeService(false),
                null,
                scheduler,
                5,
                10,
                3);

        worker.handleTurn("task-1", "turn-3");

        Assert.assertEquals(3, repositories.tasks.size());
        Assert.assertEquals(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name(), repositories.tasks.get(1).getTaskType());
        Assert.assertEquals(MemoryTaskTypeEnumVO.TURN_SUMMARY_SELF_CHECK.name(), repositories.tasks.get(2).getTaskType());
        Assert.assertEquals("turn-3", repositories.tasks.get(2).getTurnId());
        Assert.assertEquals(List.of("task-3"), selfCheckWorker.handledTaskIds);
    }

    @Test
    public void turn_summary_worker_retry_reuses_existing_summary_for_same_turn() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-1", AgentTurnEntity.builder()
                .turnId("turn-1")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .build());
        repositories.summaries.add(AgentTurnSummaryEntity.builder()
                .summaryId("turn-summary-existing")
                .turnId("turn-1")
                .sessionId("session-1")
                .runId("run-1")
                .userId("user-1")
                .summaryRef("payload-summary-existing")
                .status("ACTIVE")
                .build());
        repositories.payloads.put("payload-summary-existing", AgentPayloadEntity.builder()
                .payloadId("payload-summary-existing")
                .content("{\"summary\":\"existing summary\",\"intent\":\"existing intent\"}")
                .build());
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-1")
                .status("FAILED")
                .build());
        RecordingVectorIndexingService vectorIndexingService = new RecordingVectorIndexingService();
        TurnSummaryGcWorker worker = new TurnSummaryGcWorker(repositories,
                repositories,
                repositories,
                repositories,
                new FailingTurnSummaryNodeService(),
                vectorIndexingService,
                null);

        worker.handleTurn("task-1", "turn-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals("payload-summary-existing", repositories.tasks.get(0).getOutputRef());
        Assert.assertEquals(1, repositories.summaries.size());
        Assert.assertEquals(List.of("turn-summary-existing"), vectorIndexingService.indexedSummaryIds);
    }

    private static class StubTurnSummaryNodeService extends TurnSummaryNodeService {
        private final boolean requiresLongTermExtraction;

        private StubTurnSummaryNodeService() {
            this(true);
        }

        private StubTurnSummaryNodeService(boolean requiresLongTermExtraction) {
            super(null);
            this.requiresLongTermExtraction = requiresLongTermExtraction;
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
                    .requiresLongTermExtraction(requiresLongTermExtraction)
                    .build();
        }
    }

    private static class FailingTurnSummaryNodeService extends TurnSummaryNodeService {
        private FailingTurnSummaryNodeService() {
            super(null);
        }

        @Override
        public TurnSummaryOutputVO summarize(yhx.com.domain.agent.model.valobj.memory.TurnSummaryInputVO input,
                                             String agentId,
                                             yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            throw new AssertionError("existing summary retry should not invoke LLM again");
        }
    }

    private static class RecordingVectorIndexingService extends MemoryVectorIndexingService {
        private final List<String> indexedSummaryIds = new ArrayList<>();

        private RecordingVectorIndexingService() {
            super(null, null, null);
        }

        @Override
        public void indexTurnSummary(AgentTurnEntity turn, AgentTurnSummaryEntity summary, TurnSummaryOutputVO output) {
            indexedSummaryIds.add(summary.getSummaryId());
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
            return summaries.stream()
                    .filter(summary -> sessionId.equals(summary.getSessionId()))
                    .filter(summary -> "ACTIVE".equals(summary.getStatus()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public int countActiveSummaries(String sessionId) {
            return (int) summaries.stream()
                    .filter(summary -> sessionId.equals(summary.getSessionId()))
                    .filter(summary -> "ACTIVE".equals(summary.getStatus()))
                    .count();
        }

        @Override
        public int countAllActiveSummaries() {
            return (int) summaries.stream()
                    .filter(summary -> "ACTIVE".equals(summary.getStatus()))
                    .count();
        }

        @Override
        public void markSummariesRolledUp(List<String> summaryIds) {
            summaries.stream()
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
                            && isOpenMemoryTask(task));
        }

        @Override
        public boolean hasOpenTaskType(String taskType) {
            return tasks.stream()
                    .anyMatch(task -> taskType.equals(task.getTaskType())
                            && isOpenMemoryTask(task));
        }

        private boolean isOpenMemoryTask(AgentMemoryTaskEntity task) {
            if (!("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()))) {
                return false;
            }
            LocalDateTime touchedAt = task.getUpdatedAt() == null ? task.getCreatedAt() : task.getUpdatedAt();
            return touchedAt == null || !touchedAt.isBefore(LocalDateTime.now().minusMinutes(30));
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
    }
}
