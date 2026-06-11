package yhx.com.test.domain.agent.runtime;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.MainAgentNotebookVO;
import yhx.com.domain.agent.model.valobj.runtime.NotebookStepVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeWorklogItemVO;
import yhx.com.domain.agent.model.valobj.runtime.RunWorkingStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.interaction.ContextPlannerPendingInputHandler;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.PendingInputContinuationDispatcher;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PendingInputContinuationDispatcherTest {

    @Test
    public void context_selection_option_resumes_preparing_context() {
        RuntimeStepResult result = RuntimeTestSupport.defaultContinuationDispatcher().dispatch(answer(Map.of("artifactId", "a1")),
                checkpoint(ContextPlannerPendingInputHandler.HANDLER_CODE), context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.CONTINUE, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.PREPARING_CONTEXT, result.getNextPhase());
    }

    @Test
    public void main_agent_free_text_resumes_preparing_context() {
        RuntimeStepResult result = RuntimeTestSupport.defaultContinuationDispatcher().dispatch(UserAnswerVO.builder()
                        .status(UserAnswerStatusEnumVO.RESOLVED)
                        .answerType(UserAnswerTypeEnumVO.FREE_TEXT)
                        .freeText("继续")
                        .value("继续")
                        .build(),
                checkpoint(MainAgentPendingInputHandler.HANDLER_CODE), context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.CONTINUE, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.PREPARING_CONTEXT, result.getNextPhase());
    }

    @Test
    public void main_agent_resume_restores_per_working_state_from_checkpoint_payload() {
        RuntimeExecutionContext context = context();
        RunWorkingStateVO workingState = RunWorkingStateVO.builder()
                .notebook(MainAgentNotebookVO.builder()
                        .mode("PER")
                        .goal("inspect project")
                        .steps(List.of(NotebookStepVO.builder()
                                .stepId("s1")
                                .status("IN_PROGRESS")
                                .build()))
                        .build())
                .worklog(List.of(RuntimeWorklogItemVO.builder()
                        .workId("work-1")
                        .sequence(1L)
                        .actionType("ASK_USER")
                        .status("WAITING_USER")
                        .build()))
                .evidencePack(List.of(MaterializedEvidenceVO.builder()
                        .evidenceId("ev-1")
                        .content("previous tool result")
                        .build()))
                .nextSequence(7L)
                .build();
        Object serializedLikeState = JSON.parseObject(JSON.toJSONString(workingState));

        RuntimeStepResult result = RuntimeTestSupport.defaultContinuationDispatcher().dispatch(UserAnswerVO.builder()
                        .status(UserAnswerStatusEnumVO.RESOLVED)
                        .answerType(UserAnswerTypeEnumVO.FREE_TEXT)
                        .freeText("continue")
                        .value("continue")
                        .build(),
                checkpoint(MainAgentPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.BUILDING_STATE_VIEW,
                        Map.of("workingState", serializedLikeState)), context);

        Assert.assertEquals(RuntimeStepStatusEnumVO.CONTINUE, result.getStatus());
        Assert.assertNotNull(context.getWorkingState());
        Assert.assertEquals("inspect project", context.getWorkingState().getNotebook().getGoal());
        Assert.assertEquals("work-1", context.getWorkingState().getWorklog().get(0).getWorkId());
        Assert.assertEquals("previous tool result", context.getWorkingState().getEvidencePack().get(0).getContent());
        Assert.assertEquals(Long.valueOf(7L), context.getWorkingState().getNextSequence());
    }

    @Test
    public void tool_approval_approve_resumes_preparing_tool() {
        RuntimeExecutionContext context = context();
        RuntimeStepResult result = RuntimeTestSupport.defaultContinuationDispatcher().dispatch(answer(Map.of("decision", "APPROVED")),
                checkpoint(ToolApprovalPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.PREPARING_TOOL,
                        Map.of("toolIntent", Map.of("capabilityCode", "publish", "goal", "publish content"))), context);

        Assert.assertEquals(RuntimeStepStatusEnumVO.CONTINUE, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.PREPARING_TOOL, result.getNextPhase());
        Assert.assertNotNull(context.getRuntimeFacts().get("resumeToolIntent"));
    }

    @Test
    public void tool_approval_reject_does_not_invoke_tool() {
        RuntimeExecutionContext context = context();
        RuntimeStepResult result = RuntimeTestSupport.defaultContinuationDispatcher().dispatch(answer(Map.of("decision", "REJECTED")),
                checkpoint(ToolApprovalPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.PREPARING_TOOL,
                        Map.of("toolIntent", Map.of("capabilityCode", "file_system_write_file", "toolName", "write_file"))), context);

        Assert.assertEquals(RuntimeStepStatusEnumVO.CONTINUE, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.BUILDING_STATE_VIEW, result.getNextPhase());
        List<?> clarifications = (List<?>) context.getRuntimeFacts().get("userClarifications");
        Assert.assertEquals(1, clarifications.size());
        UserClarificationVO clarification = (UserClarificationVO) clarifications.get(0);
        Assert.assertEquals("TOOL_APPROVAL_REJECTED", clarification.getAnswerType());
        Assert.assertEquals("REJECTED", ((Map<?, ?>) clarification.getValue()).get("decision"));
        Assert.assertEquals("write_file", ((Map<?, ?>) clarification.getMetadata().get("toolIntent")).get("toolName"));
    }

    @Test
    public void unknown_handler_returns_safe_failure() {
        PendingInputContinuationDispatcher dispatcher = RuntimeTestSupport.defaultContinuationDispatcher();

        RuntimeStepResult result = dispatcher.dispatch(answer(Map.of("decision", "APPROVED")), checkpoint("UNKNOWN"), context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertNotNull(result.getSafeFailure());
    }

    private RuntimeExecutionContext context() {
        return RuntimeExecutionContext.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .runtimeFacts(new HashMap<>())
                .build();
    }

    private ContinuationCheckpointVO checkpoint(String handler) {
        return ContinuationCheckpointVO.builder().handler(handler).resumePhase(RuntimePhaseEnumVO.PREPARING_CONTEXT).build();
    }

    private ContinuationCheckpointVO checkpoint(String handler, RuntimePhaseEnumVO resumePhase, Map<String, Object> payload) {
        return ContinuationCheckpointVO.builder()
                .handler(handler)
                .resumePhase(resumePhase)
                .payload(payload)
                .build();
    }

    private UserAnswerVO answer(Object value) {
        return UserAnswerVO.builder()
                .status(UserAnswerStatusEnumVO.RESOLVED)
                .answerType(UserAnswerTypeEnumVO.OPTION)
                .value(value)
                .build();
    }
}
