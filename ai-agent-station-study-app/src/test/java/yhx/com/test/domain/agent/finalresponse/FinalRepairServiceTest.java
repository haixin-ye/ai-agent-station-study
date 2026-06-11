package yhx.com.test.domain.agent.finalresponse;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.finalresponse.FinalRepairPromptContextVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.service.node.finalrepair.FinalRepairNodeService;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;

import java.util.Map;

public class FinalRepairServiceTest {

    @Test
    public void repair_invokes_final_repair_component() {
        FakePipeline pipeline = new FakePipeline(repairAction("fixed"));
        FinalRepairNodeService service = new FinalRepairNodeService(pipeline);

        service.repair(context());

        Assert.assertEquals("FINAL_REPAIR", pipeline.lastCommand.getComponentCode());
    }

    @Test
    public void repair_requires_repair_final_action() {
        FakePipeline pipeline = new FakePipeline(MainAgentActionVO.builder().action("FINAL").stateDelta(Map.of()).build());
        FinalRepairNodeService service = new FinalRepairNodeService(pipeline);

        Assert.assertNull(service.repair(context()));
    }

    @Test
    public void repair_prompt_excludes_raw_receipt_and_trace() {
        FakePipeline pipeline = new FakePipeline(repairAction("fixed"));
        FinalRepairNodeService service = new FinalRepairNodeService(pipeline);

        service.repair(context());

        String input = String.valueOf(pipeline.lastCommand.getInputView());
        Assert.assertFalse(input.contains("rawReceipt"));
        Assert.assertFalse(input.contains("developerTrace"));
    }

    @Test
    public void repair_output_does_not_explain_repair_process() {
        FakePipeline pipeline = new FakePipeline(repairAction("fixed answer"));
        FinalRepairNodeService service = new FinalRepairNodeService(pipeline);

        FinalAnswerCandidateVO repaired = service.repair(context());

        Assert.assertEquals("fixed answer", repaired.getContent());
    }

    private FinalRepairPromptContextVO context() {
        return FinalRepairPromptContextVO.builder()
                .runId("run-001")
                .agentId("agent-001")
                .userInput("question")
                .failedCandidate(FinalAnswerCandidateVO.builder().content("Runtime trace leaked").build())
                .failureCode("FINAL_INTERNAL_LEAK")
                .guardSummary("internal leak")
                .repairInstruction("rewrite")
                .build();
    }

    private MainAgentActionVO repairAction(String content) {
        return MainAgentActionVO.builder()
                .action("REPAIR_FINAL")
                .stateDelta(Map.of("finalAnswerCandidate", Map.of("content", content, "format", "PLAIN_TEXT")))
                .build();
    }

    private static class FakePipeline extends NodeInvocationPipeline {
        private final MainAgentActionVO action;
        private NodeInvocationCommand lastCommand;

        FakePipeline(MainAgentActionVO action) {
            super(null, null);
            this.action = action;
        }

        @Override
        public NodeInvocationResult invoke(NodeInvocationCommand command) {
            lastCommand = command;
            return NodeInvocationResult.builder()
                    .status(NodeInvocationStatusEnumVO.SUCCESS)
                    .typedOutput(action)
                    .build();
        }
    }
}
