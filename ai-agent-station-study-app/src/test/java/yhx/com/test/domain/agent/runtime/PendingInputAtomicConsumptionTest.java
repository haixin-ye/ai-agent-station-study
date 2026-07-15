package yhx.com.test.domain.agent.runtime;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputResolutionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserInputResolveCommand;
import yhx.com.domain.agent.model.valobj.interaction.UserInputResolveResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.interaction.PendingInputContinuationDispatcher;
import yhx.com.domain.agent.service.interaction.PendingInputContinuationHandler;
import yhx.com.domain.agent.service.interaction.PendingInputManager;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.interaction.UserReplyProcessor;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RunTranscriptRecorder;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PendingInputAtomicConsumptionTest {

    @Test
    public void repeated_submission_dispatches_continuation_exactly_once() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AtomicInteger dispatchCount = new AtomicInteger();
        UserInteractionManager manager = manager(repository, dispatchCount);
        createPending(repository, "run-atomic", "pending-atomic", null);
        UserInputResolveCommand command = UserInputResolveCommand.builder()
                .runId("run-atomic")
                .pendingId("pending-atomic")
                .freeText("continue")
                .build();

        UserInputResolveResult first = manager.resolveUserInput(command, context("run-atomic"));
        UserInputResolveResult second = manager.resolveUserInput(command, context("run-atomic"));

        Assert.assertTrue(first.getResolved());
        Assert.assertFalse(second.getResolved());
        Assert.assertEquals(PendingInputResolutionStatusEnumVO.ALREADY_RESOLVED, second.getResolutionStatus());
        Assert.assertEquals(1, dispatchCount.get());
        Assert.assertEquals(1, repository.transcriptBlocks.size());
    }

    @Test
    public void expired_or_foreign_pending_input_cannot_dispatch() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AtomicInteger dispatchCount = new AtomicInteger();
        UserInteractionManager manager = manager(repository, dispatchCount);
        createPending(repository, "run-owner", "pending-expired", LocalDateTime.now().minusSeconds(1));

        UserInputResolveResult expired = manager.resolveUserInput(UserInputResolveCommand.builder()
                .runId("run-owner")
                .pendingId("pending-expired")
                .freeText("continue")
                .build(), context("run-owner"));
        UserInputResolveResult foreign = manager.resolveUserInput(UserInputResolveCommand.builder()
                .runId("run-other")
                .pendingId("pending-expired")
                .freeText("continue")
                .build(), context("run-other"));

        Assert.assertFalse(expired.getResolved());
        Assert.assertEquals(PendingInputResolutionStatusEnumVO.EXPIRED, expired.getResolutionStatus());
        Assert.assertFalse(foreign.getResolved());
        Assert.assertEquals(PendingInputResolutionStatusEnumVO.RUN_MISMATCH, foreign.getResolutionStatus());
        Assert.assertNull(foreign.getContinuationResult().getNextRunStatus());
        Assert.assertEquals(0, dispatchCount.get());
    }

    @Test
    public void concurrent_submission_allows_only_one_continuation_dispatch() throws Exception {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AtomicInteger dispatchCount = new AtomicInteger();
        UserInteractionManager manager = manager(repository, dispatchCount);
        createPending(repository, "run-concurrent", "pending-concurrent", null);
        UserInputResolveCommand command = UserInputResolveCommand.builder()
                .runId("run-concurrent")
                .pendingId("pending-concurrent")
                .freeText("continue")
                .build();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UserInputResolveResult> first = executor.submit(() -> resolveTogether(manager, command, ready, start));
            Future<UserInputResolveResult> second = executor.submit(() -> resolveTogether(manager, command, ready, start));
            ready.await();
            start.countDown();

            List<UserInputResolveResult> results = List.of(first.get(), second.get());
            Assert.assertEquals(1, results.stream().filter(result -> Boolean.TRUE.equals(result.getResolved())).count());
            Assert.assertEquals(1, results.stream()
                    .filter(result -> result.getResolutionStatus() == PendingInputResolutionStatusEnumVO.ALREADY_RESOLVED)
                    .count());
            Assert.assertEquals(1, dispatchCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private UserInputResolveResult resolveTogether(UserInteractionManager manager,
                                                   UserInputResolveCommand command,
                                                   CountDownLatch ready,
                                                   CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return manager.resolveUserInput(command, context(command.getRunId()));
    }

    private UserInteractionManager manager(RuntimeTestSupport.InMemoryRuntimeRepository repository,
                                           AtomicInteger dispatchCount) {
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        RunTranscriptRecorder transcriptRecorder = new RunTranscriptRecorder(repository, repository);
        PendingInputContinuationHandler handler = new PendingInputContinuationHandler() {
            @Override
            public String handlerCode() {
                return MainAgentPendingInputHandler.HANDLER_CODE;
            }

            @Override
            public RuntimeStepResult handle(yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO answer,
                                            ContinuationCheckpointVO checkpoint,
                                            RuntimeExecutionContext context) {
                dispatchCount.incrementAndGet();
                return RuntimeStepResult.builder()
                        .runId(context.getRunId())
                        .status(RuntimeStepStatusEnumVO.CONTINUE)
                        .nextRunStatus(RunStatusEnumVO.RUNNING)
                        .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                        .build();
            }
        };
        return new UserInteractionManager(
                new PendingInputManager(repository, repository),
                new UserReplyProcessor(repository),
                new PendingInputContinuationDispatcher(List.of(handler)),
                repository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
    }

    private void createPending(RuntimeTestSupport.InMemoryRuntimeRepository repository,
                               String runId,
                               String pendingId,
                               LocalDateTime expiresAt) {
        String checkpointRef = repository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(ContinuationCheckpointVO.builder()
                        .handler(MainAgentPendingInputHandler.HANDLER_CODE)
                        .resumePhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                        .relatedRunId(runId)
                        .build()))
                .build());
        repository.createPendingInput(AgentPendingInputEntity.builder()
                .pendingId(pendingId)
                .runId(runId)
                .sourceComponent(MainAgentPendingInputHandler.HANDLER_CODE)
                .pendingType("MAIN_AGENT_QUESTION")
                .inputMode("FREE_TEXT")
                .status("PENDING")
                .question("Continue?")
                .continuationRef(checkpointRef)
                .createdAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .build());
    }

    private RuntimeExecutionContext context(String runId) {
        return RuntimeExecutionContext.builder()
                .runId(runId)
                .sessionId("sess-atomic")
                .loopIndex(3)
                .runStatus(RunStatusEnumVO.WAITING_USER)
                .currentPhase(RuntimePhaseEnumVO.RESOLVING_USER_ANSWER)
                .runtimeFacts(new HashMap<>())
                .build();
    }
}
