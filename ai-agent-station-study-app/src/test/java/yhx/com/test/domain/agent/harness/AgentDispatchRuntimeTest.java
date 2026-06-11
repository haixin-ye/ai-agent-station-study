package yhx.com.test.domain.agent.harness;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.agent.AgentDispatchResultVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentTaskVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentsRequestVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentCommitVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.service.agent.AgentDispatchRuntime;
import yhx.com.domain.agent.service.agent.ParentChildRunRegistry;

import java.util.List;

public class AgentDispatchRuntimeTest {

    @Test
    public void dispatch_creates_child_relations_and_parent_is_not_ready_immediately() {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        AgentDispatchResultVO result = new AgentDispatchRuntime(registry).dispatch("parent-run", request("t1", "t2"));

        Assert.assertFalse(result.isParentReady());
        Assert.assertEquals(List.of("parent-run-child-t1", "parent-run-child-t2"), result.getChildRunIds());
        Assert.assertEquals(2, registry.listChildren("parent-run").size());
        Assert.assertEquals(ChildAgentRunStatusEnumVO.PENDING, registry.listChildren("parent-run").get(0).getStatus());
    }

    @Test
    public void wait_all_is_not_satisfied_until_all_children_are_terminal() {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        AgentDispatchRuntime runtime = new AgentDispatchRuntime(registry);
        runtime.dispatch("parent-run", request("t1", "t2"));

        runtime.recordCommit("parent-run-child-t1", SubAgentCommitVO.builder()
                .taskId("t1")
                .status("SUCCESS")
                .result("done")
                .build());

        Assert.assertFalse(registry.isWaitSatisfied("parent-run"));

        runtime.recordCommit("parent-run-child-t2", SubAgentCommitVO.builder()
                .taskId("t2")
                .status("SUCCESS")
                .result("done")
                .build());

        Assert.assertTrue(registry.isWaitSatisfied("parent-run"));
    }

    @Test
    public void child_failure_is_terminal_but_does_not_fail_parent_directly() {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        AgentDispatchRuntime runtime = new AgentDispatchRuntime(registry);
        runtime.dispatch("parent-run", request("t1"));

        runtime.recordFailure("parent-run-child-t1", "tool failed");

        ParentChildRunRelationVO relation = registry.listChildren("parent-run").get(0);
        Assert.assertEquals(ChildAgentRunStatusEnumVO.FAILED, relation.getStatus());
        Assert.assertEquals("tool failed", relation.getFailureMessage());
        Assert.assertTrue(registry.isWaitSatisfied("parent-run"));
    }

    private DelegateAgentsRequestVO request(String... taskIds) {
        return DelegateAgentsRequestVO.builder()
                .waitMode("WAIT_ALL")
                .tasks(java.util.Arrays.stream(taskIds)
                        .map(taskId -> DelegateAgentTaskVO.builder()
                                .taskId(taskId)
                                .name("worker-" + taskId)
                                .objective("Do " + taskId)
                                .build())
                        .toList())
                .build();
    }
}
