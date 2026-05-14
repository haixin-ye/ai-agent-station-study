package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.interaction.ContextPlannerPendingInputHandler;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.PendingInputContinuationDispatcher;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.HashMap;
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
    public void tool_approval_approve_resumes_preparing_tool() {
        RuntimeStepResult result = RuntimeTestSupport.defaultContinuationDispatcher().dispatch(answer(Map.of("decision", "APPROVED")),
                checkpoint(ToolApprovalPendingInputHandler.HANDLER_CODE), context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.CONTINUE, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.PREPARING_TOOL, result.getNextPhase());
    }

    @Test
    public void tool_approval_reject_does_not_invoke_tool() {
        RuntimeStepResult result = RuntimeTestSupport.defaultContinuationDispatcher().dispatch(answer(Map.of("decision", "REJECTED")),
                checkpoint(ToolApprovalPendingInputHandler.HANDLER_CODE), context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.CONTINUE, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.PREPARING_CONTEXT, result.getNextPhase());
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

    private UserAnswerVO answer(Object value) {
        return UserAnswerVO.builder()
                .status(UserAnswerStatusEnumVO.RESOLVED)
                .answerType(UserAnswerTypeEnumVO.OPTION)
                .value(value)
                .build();
    }
}
