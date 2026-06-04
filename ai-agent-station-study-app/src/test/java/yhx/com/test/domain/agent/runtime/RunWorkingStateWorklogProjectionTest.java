package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionEffectStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeWorklogItemVO;
import yhx.com.domain.agent.service.runtime.RunWorkingStateManager;

import java.util.List;
import java.util.Map;

public class RunWorkingStateWorklogProjectionTest {

    @Test
    public void action_effect_is_appended_to_ordered_worklog_and_projected() {
        RunWorkingStateManager manager = new RunWorkingStateManager();
        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runId("run-worklog")
                .sessionId("sess-worklog")
                .userId("user-001")
                .loopIndex(4)
                .lastStateView(MainAgentStateViewVO.builder().build())
                .build();
        MaterializedEvidenceVO evidence = MaterializedEvidenceVO.builder()
                .evidenceId("evidence-success")
                .evidenceType("TOOL")
                .summary("Tool action succeeded: read file")
                .boundedSnippet("Tool action succeeded: read file")
                .build();

        manager.apply(context, callToolAction(), MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                .createdEvidenceIds(List.of("evidence-success"))
                .createdEvidence(List.of(evidence))
                .actionEffect(ActionEffectVO.builder()
                        .action("CALL_TOOL")
                        .status(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name())
                        .loopIndex(4)
                        .toolIntent(toolIntent())
                        .createdEvidenceIds(List.of("evidence-success"))
                        .createdEvidence(List.of(evidence))
                        .build())
                .message("Tool completed.")
                .build());

        MainAgentStateViewVO projected = manager.project(context.getWorkingState());

        Assert.assertEquals(1, projected.getWorklog().size());
        RuntimeWorklogItemVO item = projected.getWorklog().get(0);
        Assert.assertEquals("run-worklog", item.getRunId());
        Assert.assertEquals(Integer.valueOf(4), item.getLoopIndex());
        Assert.assertEquals(Long.valueOf(1L), item.getSequence());
        Assert.assertEquals("CALL_TOOL", item.getActionType());
        Assert.assertEquals(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name(), item.getStatus());
        Assert.assertEquals(List.of("evidence-success"), item.getResultEvidenceIds());
        Assert.assertEquals("read_file", item.getRequest().getToolName());
        Assert.assertEquals("Tool completed.", item.getResult().getMessage());
    }

    @Test
    public void identical_call_tool_arguments_create_same_repeat_guard_key() {
        RunWorkingStateManager manager = new RunWorkingStateManager();
        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runId("run-repeat-key")
                .sessionId("sess-repeat-key")
                .userId("user-001")
                .loopIndex(1)
                .lastStateView(MainAgentStateViewVO.builder().build())
                .build();

        manager.apply(context, callToolAction(), successfulToolResult(1));
        context.setLoopIndex(2);
        manager.apply(context, callToolAction(), successfulToolResult(2));

        MainAgentStateViewVO projected = manager.project(context.getWorkingState());

        Assert.assertEquals(2, projected.getWorklog().size());
        Assert.assertNotNull(projected.getWorklog().get(0).getRepeatGuardKey());
        Assert.assertEquals(projected.getWorklog().get(0).getRepeatGuardKey(),
                projected.getWorklog().get(1).getRepeatGuardKey());
        Assert.assertTrue(projected.getWorklog().get(0).getRepeatGuardKey()
                .startsWith("CALL_TOOL:file_system_read_file:file-system:read_file:"));
    }

    private MainAgentActionVO callToolAction() {
        return MainAgentActionVO.builder()
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolIntent", toolIntent()))
                .build();
    }

    private MainActionHandlerResult successfulToolResult(int loopIndex) {
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                .actionEffect(ActionEffectVO.builder()
                        .action("CALL_TOOL")
                        .status(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name())
                        .loopIndex(loopIndex)
                        .toolIntent(toolIntent())
                        .build())
                .message("Tool completed.")
                .build();
    }

    private Map<String, Object> toolIntent() {
        return Map.of(
                "capabilityCode", "file_system_read_file",
                "mcpServerCode", "file-system",
                "toolName", "read_file",
                "arguments", Map.of("path", "E:/demo.txt")
        );
    }
}
