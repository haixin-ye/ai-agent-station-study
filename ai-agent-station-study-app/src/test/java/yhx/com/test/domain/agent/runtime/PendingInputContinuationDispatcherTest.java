package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
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
import yhx.com.domain.agent.service.interaction.PendingInputContinuationHandler;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.domain.agent.service.interaction.RuntimeUserClarificationRecorder;
import yhx.com.domain.agent.service.interaction.SubAgentPendingInputHandler;
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
                checkpoint(MainAgentPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.BUILDING_STATE_VIEW, Map.of()), context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.CONTINUE, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.BUILDING_STATE_VIEW, result.getNextPhase());
    }

    @Test
    public void tool_approval_approve_resumes_preparing_tool() {
        RuntimeExecutionContext context = context();
        RuntimeStepResult result = RuntimeTestSupport.defaultContinuationDispatcher().dispatch(answer(Map.of("decision", "APPROVED")),
                checkpoint(ToolApprovalPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.PREPARING_TOOL,
                        toolApprovalPayload(Map.of(
                                "capabilityCode", "publish",
                                "mcpServerCode", "default-mcp",
                                "toolName", "publish",
                                "goal", "publish content"))), context);

        Assert.assertEquals(RuntimeStepStatusEnumVO.CONTINUE, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.PREPARING_TOOL, result.getNextPhase());
        Assert.assertNotNull(context.getRuntimeFacts().get("resumeToolIntent"));
    }

    @Test
    public void tool_approval_reject_does_not_invoke_tool() {
        RuntimeExecutionContext context = context();
        RuntimeStepResult result = RuntimeTestSupport.defaultContinuationDispatcher().dispatch(answer(Map.of("decision", "REJECTED")),
                checkpoint(ToolApprovalPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.PREPARING_TOOL,
                        toolApprovalPayload(Map.of(
                                "capabilityCode", "file_system_write_file",
                                "mcpServerCode", "file-system",
                                "toolName", "write_file"))), context);

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
    public void tool_approval_rejects_mismatched_tool_identity() {
        Map<String, Object> payload = new HashMap<>(toolApprovalPayload(Map.of(
                "capabilityCode", "file_system_write_file",
                "mcpServerCode", "file-system",
                "toolName", "write_file")));
        payload.put("toolName", "delete_file");

        RuntimeStepResult result = new ToolApprovalPendingInputHandler().handle(
                answer(Map.of("decision", "APPROVED")),
                checkpoint(ToolApprovalPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.PREPARING_TOOL, payload),
                context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertTrue(result.getMessage().contains("identity"));
    }

    @Test
    public void sub_agent_resume_rejects_missing_child_task_relation() {
        RuntimeStepResult result = new SubAgentPendingInputHandler().handle(
                answer("continue"),
                checkpoint(SubAgentPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.WAITING_CHILDREN,
                        Map.of("parentRunId", "run-001")),
                context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertTrue(result.getMessage().contains("relation"));
    }

    @Test
    public void unknown_handler_returns_safe_failure() {
        PendingInputContinuationDispatcher dispatcher = RuntimeTestSupport.defaultContinuationDispatcher();

        RuntimeStepResult result = dispatcher.dispatch(answer(Map.of("decision", "APPROVED")), checkpoint("UNKNOWN"), context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertNotNull(result.getSafeFailure());
    }

    @Test
    public void handler_exception_is_converted_to_safe_failure() {
        PendingInputContinuationHandler brokenHandler = new PendingInputContinuationHandler() {
            @Override
            public String handlerCode() {
                return MainAgentPendingInputHandler.HANDLER_CODE;
            }

            @Override
            public RuntimeStepResult handle(UserAnswerVO answer,
                                            ContinuationCheckpointVO checkpoint,
                                            RuntimeExecutionContext context) {
                throw new IllegalStateException("broken source payload");
            }
        };
        PendingInputContinuationDispatcher dispatcher = new PendingInputContinuationDispatcher(List.of(brokenHandler));

        RuntimeStepResult result = dispatcher.dispatch(answer("continue"),
                checkpoint(MainAgentPendingInputHandler.HANDLER_CODE,
                        RuntimePhaseEnumVO.BUILDING_STATE_VIEW, Map.of()), context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertNotNull(result.getSafeFailure());
        Assert.assertTrue(result.getMessage().contains(MainAgentPendingInputHandler.HANDLER_CODE));
    }

    @Test
    public void semantic_clarification_replaces_generic_record_for_same_pending_input() {
        RuntimeExecutionContext context = context();
        context.getRuntimeFacts().put("userClarifications", List.of(UserClarificationVO.builder()
                .pendingId("pending-1")
                .answerType("OPTION")
                .value(Map.of("decision", "REJECTED"))
                .build()));

        new RuntimeUserClarificationRecorder().append(context, UserClarificationVO.builder()
                .pendingId("pending-1")
                .answerType("TOOL_APPROVAL_REJECTED")
                .value(Map.of("decision", "REJECTED"))
                .build());

        List<?> clarifications = (List<?>) context.getRuntimeFacts().get("userClarifications");
        Assert.assertEquals(1, clarifications.size());
        Assert.assertEquals("TOOL_APPROVAL_REJECTED",
                ((UserClarificationVO) clarifications.get(0)).getAnswerType());
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
                .relatedRunId("run-001")
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

    private Map<String, Object> toolApprovalPayload(Map<String, Object> toolIntent) {
        return Map.of(
                "approvalKey", "approval-key",
                "toolCallId", "tool-call-1",
                "argumentsHash", "args-hash",
                "capabilityCode", toolIntent.getOrDefault("capabilityCode", "publish"),
                "mcpServerCode", toolIntent.getOrDefault("mcpServerCode", "default-mcp"),
                "toolName", toolIntent.getOrDefault("toolName", "publish"),
                "toolIntent", toolIntent);
    }
}
