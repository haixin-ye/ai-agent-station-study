package yhx.com.test.domain.agent.runtime;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeResumeCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.RuntimeComponentPorts;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RuntimePendingInputCheckpointRecoveryTest {

    @Test
    public void plan_execute_pause_json_round_trip_restores_accumulated_state_before_main_agent() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AtomicInteger mainCalls = new AtomicInteger();
        AtomicReference<Map<String, Object>> restoredSnapshot = new AtomicReference<>();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository, new RuntimeComponentPorts() {
            @Override
            public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
                return ContextPlannerHandlingResult.builder()
                        .stateView(MainAgentStateViewVO.builder().build())
                        .effectiveSelections(List.of())
                        .build();
            }

            @Override
            public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
                throw new AssertionError("Resumed Runtime must project the restored WorkingState.");
            }

            @Override
            public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
                int call = mainCalls.getAndIncrement();
                if (call == 0) {
                    return MainAgentActionVO.builder()
                            .action("CONTINUE")
                            .stateDelta(Map.of("nextActionHint", "ask for destination"))
                            .build();
                }
                if (call == 1) {
                    return askUserAction();
                }
                restoredSnapshot.set(Map.of(
                        "loopIndex", context.getLoopIndex(),
                        "loopCount", context.getRecoveryCounters().getLoopCount(),
                        "lastAction", context.getLastAction().getAction(),
                        "actionHistorySize", context.getWorkingState().getActionHistory().size(),
                        "clarificationCount", context.getWorkingState().getUserClarifications().size(),
                        "clarificationValue", context.getWorkingState().getUserClarifications().get(0).getValue()));
                return MainAgentActionVO.builder()
                        .action("FINAL")
                        .stateDelta(Map.of("finalAnswer", "done"))
                        .build();
            }
        }, true, new RuntimeLoopPolicy());

        RuntimeStepResult waiting = runtime.start(RuntimeStartCommand.builder()
                .runId("run-checkpoint")
                .sessionId("sess-checkpoint")
                .userId("user-1")
                .agentId("agent-1")
                .userInput("prepare then ask")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.WAITING_USER, waiting.getStatus());
        AgentPendingInputEntity pending = repository.pendingInputs.get(waiting.getPendingInputId());
        ContinuationCheckpointVO persisted = JSON.parseObject(
                repository.payloads.get(pending.getContinuationRef()).getContent(), ContinuationCheckpointVO.class);
        Assert.assertEquals(Integer.valueOf(1), persisted.getSnapshotVersion());
        Assert.assertEquals(Integer.valueOf(1), persisted.getRuntimeSnapshot().getLoopIndex());
        Assert.assertEquals(Integer.valueOf(1), persisted.getRuntimeSnapshot().getRecoveryCounters().getLoopCount());
        Assert.assertEquals(2, persisted.getRuntimeSnapshot().getWorkingState().getActionHistory().size());

        RuntimeStepResult completed = runtime.resume(RuntimeResumeCommand.builder()
                .runId("run-checkpoint")
                .pendingId(waiting.getPendingInputId())
                .freeText("Shanghai")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.COMPLETED, completed.getStatus());
        Map<String, Object> restored = restoredSnapshot.get();
        Assert.assertNotNull(restored);
        Assert.assertEquals(1, restored.get("loopIndex"));
        Assert.assertEquals(1, restored.get("loopCount"));
        Assert.assertEquals("ASK_USER", restored.get("lastAction"));
        Assert.assertEquals(2, restored.get("actionHistorySize"));
        Assert.assertEquals(1, restored.get("clarificationCount"));
        Assert.assertEquals("Shanghai", restored.get("clarificationValue"));

        RuntimeStepResult duplicate = runtime.resume(RuntimeResumeCommand.builder()
                .runId("run-checkpoint")
                .pendingId(waiting.getPendingInputId())
                .freeText("Shanghai")
                .build());
        Assert.assertEquals(RuntimeStepStatusEnumVO.COMPLETED, duplicate.getStatus());
        Assert.assertTrue(duplicate.getMessage().startsWith("ALREADY_RESOLVED"));
        Assert.assertEquals(3, mainCalls.get());
    }

    private MainAgentActionVO askUserAction() {
        return MainAgentActionVO.builder()
                .action("ASK_USER")
                .stateDelta(Map.of("askUserRequest", Map.of(
                        "question", "Destination?",
                        "inputMode", "FREE_TEXT",
                        "allowFreeText", true)))
                .build();
    }
}
