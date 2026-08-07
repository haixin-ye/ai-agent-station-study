package yhx.com.test.domain.agent.node.mainagent;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionCallVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.runtime.RunBaseContextVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunRuntimeControlVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.TaskLedgerVO;
import yhx.com.domain.agent.service.invocation.NodeFunctionSpecRegistry;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.node.mainagent.MainAgentNodeService;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.test.domain.agent.invocation.support.FakeNodeClientPort;
import yhx.com.test.domain.agent.invocation.support.InMemoryPromptContentProvider;

import java.util.Map;
import java.util.ArrayList;

public class MainAgentNodeServiceFunctionCallTest {

    @Test
    public void work_stages_expose_work_functions_without_final_answer_function() {
        java.util.Set<String> names = NodeFunctionSpecRegistry.defaultRegistry()
                .resolveMainAgent(MainAgentStageEnumVO.EXECUTING).stream()
                .map(yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO::getName)
                .collect(java.util.stream.Collectors.toSet());

        Assert.assertTrue(names.contains("main_call_tool"));
        Assert.assertTrue(names.contains("main_ready_to_deliver"));
        Assert.assertTrue(names.contains("main_fail"));
        Assert.assertFalse(names.contains("main_final_answer"));
    }

    @Test
    public void profile_can_enable_function_call_mode_for_main_agent() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueueFunctionCall(NodeFunctionCallVO.builder()
                        .name("main_final_answer")
                        .arguments(Map.of(
                                "taskUpdate", Map.of("lastDecision", "Deliver the completed answer."),
                                "content", "ok"))
                        .build());
        MainAgentNodeService service = new MainAgentNodeService(
                new NodeInvocationPipeline(new PromptAssembler(new InMemoryPromptContentProvider()), client),
                NodeInvocationProfileVO.builder()
                        .modelCode("fake")
                        .invocationMode(NodeInvocationModeEnumVO.FUNCTION_CALL)
                        .maxRepairAttempts(0)
                        .build());

        MainAgentActionVO action = service.invoke(RuntimeExecutionContext.builder()
                .runId("run-1")
                .agentId("agent-1")
                .loopIndex(0)
                .runContextState(RunContextStateVO.builder()
                        .mainAgentStage(MainAgentStageEnumVO.DELIVERING)
                        .baseContext(RunBaseContextVO.builder().runId("run-1").userInput("answer").build())
                        .taskLedger(TaskLedgerVO.builder().deliverables(new ArrayList<>()).steps(new ArrayList<>()).build())
                        .runtimeControl(RunRuntimeControlVO.builder().build())
                        .loopTimeline(new ArrayList<>())
                        .build())
                .runtimeFacts(new java.util.LinkedHashMap<>())
                .build());

        Assert.assertEquals("FINAL", action.getAction());
        Assert.assertEquals(NodeInvocationModeEnumVO.FUNCTION_CALL, client.requests().get(0).getInvocationMode());
        Assert.assertEquals(
                java.util.Set.of("main_final_answer", "main_fail"),
                client.requests().get(0).getFunctionSpecs().stream()
                        .map(yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO::getName)
                        .collect(java.util.stream.Collectors.toSet()));
    }
}
