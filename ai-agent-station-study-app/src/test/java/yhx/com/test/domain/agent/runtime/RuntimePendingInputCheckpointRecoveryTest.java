package yhx.com.test.domain.agent.runtime;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputPauseIntentVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeResumeCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
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
    public void tool_approval_checkpoint_is_created_after_action_is_applied_to_working_state() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        MainAgentActionVO toolAction = MainAgentActionVO.builder()
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolIntent", Map.of(
                        "capabilityCode", "file_system_write_file",
                        "toolName", "write_file",
                        "arguments", Map.of("path", "docs/story.md"))) )
                .build();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(
                repository,
                RuntimeTestSupport.fixedPorts(toolAction),
                (context, action) -> MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.WAITING_USER)
                        .nextPhase(RuntimePhaseEnumVO.WAITING_USER)
                        .pauseIntent(PendingInputPauseIntentVO.builder()
                                .handler(ToolApprovalPendingInputHandler.HANDLER_CODE)
                                .resumePhase(RuntimePhaseEnumVO.PREPARING_TOOL)
                                .sourceComponent("ToolApprovalService")
                                .pendingType("TOOL_APPROVAL")
                                .expectedAnswerValueType("OPTION")
                                .askUserRequest(yhx.com.domain.agent.model.valobj.context.AskUserRequestVO.builder()
                                        .question("Approve write_file?")
                                        .inputMode("SINGLE_CHOICE")
                                        .allowFreeText(false)
                                        .options(List.of(
                                                Map.of("id", "approve", "label", "Approve", "value", "APPROVED"),
                                                Map.of("id", "reject", "label", "Reject", "value", "REJECTED")))
                                        .build())
                                .sourcePayload(Map.of(
                                        "approvalKey", "approval-key",
                                        "toolCallId", "tool-call-1",
                                        "argumentsHash", "hash",
                                        "toolIntent", Map.of("toolName", "write_file")))
                                .build())
                        .message("waiting for approval")
                        .build(),
                new RuntimeLoopPolicy());

        RuntimeStepResult waiting = runtime.start(RuntimeStartCommand.builder()
                .runId("run-tool-checkpoint")
                .sessionId("sess-tool-checkpoint")
                .userId("user-1")
                .agentId("agent-1")
                .userInput("write a file")
                .build());

        AgentPendingInputEntity pending = repository.pendingInputs.get(waiting.getPendingInputId());
        ContinuationCheckpointVO persisted = JSON.parseObject(
                repository.payloads.get(pending.getContinuationRef()).getContent(), ContinuationCheckpointVO.class);
        Assert.assertEquals(RuntimeStepStatusEnumVO.WAITING_USER, waiting.getStatus());
        Assert.assertEquals(1, persisted.getRuntimeSnapshot().getWorkingState().getActionHistory().size());
        Assert.assertEquals("CALL_TOOL", persisted.getRuntimeSnapshot().getWorkingState()
                .getActionHistory().get(0).getAction());
        Assert.assertEquals(1, persisted.getRuntimeSnapshot().getWorkingState().getWorklog().size());
    }

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
