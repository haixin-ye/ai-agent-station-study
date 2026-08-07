package yhx.com.test.domain.agent.runtime;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RunRuntimeControlVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.RuntimeComponentPorts;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RuntimeRunTimelineFlowTest {

    @Test
    public void tool_outcome_is_visible_before_delivery_stage_and_context_is_not_recalled_again() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        RuntimeComponentPorts ports = new RuntimeComponentPorts() {
            @Override
            public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
                return ContextPlannerHandlingResult.builder()
                        .stateView(MainAgentStateViewVO.builder().build())
                        .effectiveSelections(List.of())
                        .build();
            }

            @Override
            public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
                refreshes.incrementAndGet();
                throw new AssertionError("Same-run action results must not trigger context recall.");
            }

            @Override
            public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
                int call = calls.incrementAndGet();
                if (call == 1) return toolAction();
                if (call == 2) {
                    Assert.assertEquals(Integer.valueOf(1),
                            context.getRunContextState().getRuntimeControl().getCurrentLoopIndex());
                    Assert.assertEquals(Integer.valueOf(1),
                            context.getRunContextState().getRuntimeControl().getRecoveryCounters().getLoopCount());
                    RunRuntimeControlVO persistedControl = JSON.parseObject(
                            repository.payloads.get(repository.runContexts.get(context.getRunId())
                                    .getRuntimeControlRef()).getContent(),
                            RunRuntimeControlVO.class);
                    Assert.assertEquals(Integer.valueOf(1), persistedControl.getCurrentLoopIndex());
                    Assert.assertEquals(Integer.valueOf(1), persistedControl.getRecoveryCounters().getLoopCount());
                    Assert.assertEquals("CONTINUE_LOOP", context.getRunContextState().getLoopTimeline()
                            .get(0).getRuntimeOutcome().getStatus());
                    Assert.assertEquals("payload-tool-1", context.getRunContextState().getLoopTimeline()
                            .get(0).getRuntimeOutcome().getResultPayloadRef());
                    return readyAction();
                }
                Assert.assertEquals(MainAgentStageEnumVO.DELIVERING,
                        context.getRunContextState().getMainAgentStage());
                Assert.assertEquals(2, context.getRunContextState().getLoopTimeline().size());
                return finalAction();
            }
        };

        RuntimeStepResult result = RuntimeTestSupport.runtime(repository, ports, (context, action) -> {
            if ("CALL_TOOL".equals(action.getAction())) {
                repository.savePayload(yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity.builder()
                        .payloadId("payload-tool-1")
                        .payloadType(yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO.TOOL_RECEIPT)
                        .content("write succeeded")
                        .build());
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                        .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                        .actionEffect(yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO.builder()
                                .action("CALL_TOOL").status("TOOL_SUCCEEDED").resultRef("payload-tool-1").build())
                        .message("write succeeded")
                        .build();
            }
            if ("READY_TO_DELIVER".equals(action.getAction())) {
                context.getRunContextState().setMainAgentStage(MainAgentStageEnumVO.DELIVERING);
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                        .nextPhase(RuntimePhaseEnumVO.CALLING_MAIN_NODE)
                        .message("delivery ready")
                        .build();
            }
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.COMPLETED)
                    .nextPhase(RuntimePhaseEnumVO.COMPLETED)
                    .finalAnswerCandidate(FinalAnswerCandidateVO.builder().content("File created successfully.").build())
                    .message("delivered")
                    .build();
        }, new RuntimeLoopPolicy()).start(RuntimeStartCommand.builder()
                .runId("run-timeline-flow")
                .sessionId("sess-timeline-flow")
                .userInput("Create a file.")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.COMPLETED, result.getStatus());
        Assert.assertEquals("File created successfully.", result.getFinalAnswer());
        Assert.assertEquals(3, calls.get());
        Assert.assertEquals(0, refreshes.get());
    }

    private MainAgentActionVO toolAction() {
        return MainAgentActionVO.builder()
                .taskUpdate(Map.of(
                        "goal", "create requested file",
                        "deliverableUpdates", List.of(Map.of("deliverableId", "file", "status", "IN_PROGRESS")),
                        "stepUpdates", List.of(Map.of("stepId", "write", "status", "IN_PROGRESS"))))
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolIntent", Map.of("toolName", "write_file")))
                .build();
    }

    private MainAgentActionVO readyAction() {
        return MainAgentActionVO.builder()
                .taskUpdate(Map.of(
                        "deliverableUpdates", List.of(Map.of("deliverableId", "file", "status", "COMPLETED")),
                        "stepUpdates", List.of(Map.of("stepId", "write", "status", "COMPLETED"))))
                .action("READY_TO_DELIVER")
                .stateDelta(Map.of("deliveryRequest", Map.of("reason", "file was written")))
                .build();
    }

    private MainAgentActionVO finalAction() {
        return MainAgentActionVO.builder()
                .taskUpdate(Map.of("lastDecision", "deliver completed file result"))
                .action("FINAL")
                .stateDelta(Map.of("finalAnswerCandidate", Map.of("content", "File created successfully.")))
                .build();
    }
}
