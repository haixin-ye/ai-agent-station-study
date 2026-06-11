package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryInputVO;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryOutputVO;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.node.turnsummary.TurnSummaryNodeService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class TurnSummaryNodeServiceTest {

    @Test
    public void summarize_invokes_turn_summary_component_and_returns_typed_output() {
        FakePipeline pipeline = new FakePipeline(TurnSummaryOutputVO.builder()
                .summary("User asked for MCP notes and the agent explained the protocol.")
                .intent("explain protocol")
                .topics(List.of("MCP"))
                .entities(List.of(Map.of("name", "MCP", "type", "protocol")))
                .artifactRefs(List.of("artifact-1"))
                .importanceScore(new BigDecimal("0.70"))
                .requiresLongTermExtraction(false)
                .build());
        TurnSummaryNodeService service = new TurnSummaryNodeService(pipeline);

        TurnSummaryOutputVO output = service.summarize(TurnSummaryInputVO.builder()
                .runId("run-1")
                .sessionId("sess-1")
                .turnId("turn-1")
                .userInput("What is MCP?")
                .finalAnswer("MCP is a protocol.")
                .build(), "agent-1", null);

        Assert.assertEquals("TURN_SUMMARY", pipeline.lastCommand.getComponentCode());
        Assert.assertEquals("turn-summary-output-v1", pipeline.lastCommand.getContractVersion());
        Assert.assertEquals("explain protocol", output.getIntent());
        Assert.assertEquals("MCP", output.getTopics().get(0));
    }

    private static class FakePipeline extends NodeInvocationPipeline {
        private final TurnSummaryOutputVO output;
        private NodeInvocationCommand lastCommand;

        FakePipeline(TurnSummaryOutputVO output) {
            super(null, null);
            this.output = output;
        }

        @Override
        public NodeInvocationResult invoke(NodeInvocationCommand command) {
            lastCommand = command;
            return NodeInvocationResult.builder()
                    .status(NodeInvocationStatusEnumVO.SUCCESS)
                    .typedOutput(output)
                    .build();
        }
    }
}
