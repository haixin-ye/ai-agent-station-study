package yhx.com.test.domain.agent.migration;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class OldHarnessRoutingIsolationTest {

    @Test
    public void normal_chat_route_uses_new_runtime_facade() throws Exception {
        String controller = Files.readString(root().resolve("ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentChatController.java"));

        Assert.assertTrue(controller.contains("AgentRuntimeFacade"));
        Assert.assertFalse(controller.contains("AutoAgentExecuteStrategy"));
    }

    @Test
    public void normal_chat_route_does_not_call_auto_agent_execute_strategy() throws Exception {
        Assert.assertFalse(MigrationTestSupport.containsAny(root().resolve("ai-agent-station-study-trigger/src/main/java"),
                "AutoAgentExecuteStrategy",
                "DefaultAutoAgentExecuteStrategyFactory",
                "Step1AnalyzerNode",
                "Step2PrecisionExecutorNode",
                "Step3QualitySupervisorNode",
                "Step4LogExecutionSummaryNode"));
    }

    @Test
    public void old_step_nodes_are_not_registered_as_normal_runtime_nodes() throws Exception {
        Assert.assertFalse(MigrationTestSupport.containsAny(root().resolve("ai-agent-station-study-app/src/main/java/yhx/com/config"),
                "Step1AnalyzerNode",
                "Step2PrecisionExecutorNode",
                "Step3QualitySupervisorNode",
                "Step4LogExecutionSummaryNode"));
    }

    private Path root() {
        return MigrationTestSupport.projectRoot();
    }
}

