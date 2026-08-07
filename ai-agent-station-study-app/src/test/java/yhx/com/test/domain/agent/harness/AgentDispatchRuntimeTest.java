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
        Assert.assertEquals(List.of("parent-run-child-b1-t1", "parent-run-child-b1-t2"), result.getChildRunIds());
        Assert.assertEquals(2, registry.listChildren("parent-run").size());
        Assert.assertEquals(ChildAgentRunStatusEnumVO.PENDING, registry.listChildren("parent-run").get(0).getStatus());
    }

    @Test
    public void wait_all_is_not_satisfied_until_all_children_are_terminal() {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        AgentDispatchRuntime runtime = new AgentDispatchRuntime(registry);
        AgentDispatchResultVO result = runtime.dispatch("parent-run", request("t1", "t2"));

        runtime.recordCommit(result.getChildRunIds().get(0), SubAgentCommitVO.builder()
                .taskId("t1")
                .status("SUCCESS")
                .result("done")
                .build());

        Assert.assertFalse(registry.isWaitSatisfied("parent-run"));

        runtime.recordCommit(result.getChildRunIds().get(1), SubAgentCommitVO.builder()
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
        AgentDispatchResultVO result = runtime.dispatch("parent-run", request("t1"));

        runtime.recordFailure(result.getChildRunIds().get(0), "tool failed");

        ParentChildRunRelationVO relation = registry.listChildren("parent-run").get(0);
        Assert.assertEquals(ChildAgentRunStatusEnumVO.FAILED, relation.getStatus());
        Assert.assertEquals("tool failed", relation.getFailureMessage());
        Assert.assertTrue(registry.isWaitSatisfied("parent-run"));
    }

    @Test
    public void each_dispatch_batch_has_an_independent_parent_resume_cycle() {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        AgentDispatchRuntime runtime = new AgentDispatchRuntime(registry);

        AgentDispatchResultVO first = runtime.dispatch("parent-run", request("t1"));
        runtime.recordCommit(first.getChildRunIds().get(0), SubAgentCommitVO.builder()
                .taskId("t1")
                .status("SUCCESS")
                .result("first done")
                .build());
        String firstBatchId = registry.findByChildRunId(first.getChildRunIds().get(0))
                .orElseThrow().getDispatchBatchId();

        Assert.assertTrue(registry.markParentResumeRequested("parent-run", firstBatchId));
        Assert.assertFalse(registry.markParentResumeRequested("parent-run", firstBatchId));

        AgentDispatchResultVO second = runtime.dispatch("parent-run", request("t2"));
        String secondBatchId = registry.findByChildRunId(second.getChildRunIds().get(0))
                .orElseThrow().getDispatchBatchId();
        Assert.assertFalse(registry.isWaitSatisfied("parent-run"));
        Assert.assertFalse(registry.markParentResumeRequested("parent-run", firstBatchId));

        runtime.recordCommit(second.getChildRunIds().get(0), SubAgentCommitVO.builder()
                .taskId("t2")
                .status("SUCCESS")
                .result("second done")
                .build());

        Assert.assertTrue(registry.isWaitSatisfied("parent-run"));
        Assert.assertTrue(registry.markParentResumeRequested("parent-run", secondBatchId));
    }

    @Test
    public void long_task_ids_produce_stable_unique_database_safe_child_run_ids() {
        String parentRunId = "run-71337e75-8571-48b0-942d-81494cac7e34";
        AgentDispatchRuntime firstRuntime = new AgentDispatchRuntime(new ParentChildRunRegistry());
        AgentDispatchResultVO first = firstRuntime.dispatch(parentRunId,
                request("write-complete-chinese-science-article", "write-complete-english-science-article"));
        AgentDispatchResultVO repeated = new AgentDispatchRuntime(new ParentChildRunRegistry()).dispatch(parentRunId,
                request("write-complete-chinese-science-article"));

        Assert.assertEquals(2, first.getChildRunIds().stream().distinct().count());
        Assert.assertTrue(first.getChildRunIds().stream().allMatch(id -> id.length() <= 64));
        Assert.assertTrue(first.getChildRunIds().stream().allMatch(id -> id.startsWith(parentRunId + "-c-")));
        Assert.assertEquals(first.getChildRunIds().get(0), repeated.getChildRunIds().get(0));
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
