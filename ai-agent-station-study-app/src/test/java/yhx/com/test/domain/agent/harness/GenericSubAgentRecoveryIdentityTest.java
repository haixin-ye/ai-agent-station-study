package yhx.com.test.domain.agent.harness;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.agent.AgentDispatchRuntime;
import yhx.com.domain.agent.service.agent.GenericSubAgentDispatchOrchestrator;
import yhx.com.domain.agent.service.agent.NoopChildAgentResultProjector;
import yhx.com.domain.agent.service.agent.ParentChildRunRegistry;

import java.util.Map;

public class GenericSubAgentRecoveryIdentityTest {

    @Test
    public void child_continuation_cannot_resume_under_another_parent_run() {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        registry.register(ParentChildRunRelationVO.builder()
                .parentRunId("run-parent-a")
                .childRunId("run-child-a")
                .taskId("task-a")
                .status(ChildAgentRunStatusEnumVO.WAITING_USER)
                .build());
        GenericSubAgentDispatchOrchestrator orchestrator = new GenericSubAgentDispatchOrchestrator(
                new AgentDispatchRuntime(registry), registry, new NoopChildAgentResultProjector(), Map.of());

        try {
            orchestrator.resumeChildAndProject(RuntimeExecutionContext.builder()
                            .runId("run-parent-b")
                            .build(),
                    "run-child-a",
                    UserAnswerVO.builder().pendingId("pending-a").build());
            Assert.fail("Expected parent-child identity validation to reject the resume.");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("does not belong to parent Run"));
        }
    }
}
