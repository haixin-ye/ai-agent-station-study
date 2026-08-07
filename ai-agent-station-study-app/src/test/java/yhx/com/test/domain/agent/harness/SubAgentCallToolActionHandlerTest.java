package yhx.com.test.domain.agent.harness;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentRunCommandVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentCapabilityCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionResultVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateResult;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputPauseIntentVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.agent.ParentChildRunRegistry;
import yhx.com.domain.agent.service.agent.SubAgentCallToolActionHandler;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SubAgentCallToolActionHandlerTest {

    @Test
    public void generic_mcp_grant_allows_a_specific_configured_tool_capability() {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        ParentChildRunRelationVO relation = ParentChildRunRelationVO.builder()
                .parentRunId("parent-1")
                .childRunId("child-1")
                .taskId("task-1")
                .build();
        registry.register(relation);
        AtomicReference<String> capability = new AtomicReference<>();
        SubAgentCallToolActionHandler handler = new SubAgentCallToolActionHandler(registry, command -> {
            capability.set(command.getCapabilityCode());
            return ToolActionResultVO.builder()
                    .status(ToolActionStatusEnumVO.CONTINUE_LOOP)
                    .message("called")
                    .build();
        });
        SubAgentActionExecutionContextVO context = SubAgentActionExecutionContextVO.builder()
                .relation(relation)
                .loopIndex(1)
                .command(GenericSubAgentRunCommandVO.builder()
                        .effectiveCapabilityCodes(Set.of(AgentCapabilityCodeEnumVO.MCP_TOOL.code()))
                        .build())
                .build();

        SubAgentActionHandlerResultVO result = handler.handle(context, SubAgentActionVO.builder()
                .action("CALL_TOOL")
                .actionInput(Map.of(
                        "capabilityCode", "csdn_publisher_publisharticle",
                        "toolName", "publishArticle",
                        "goal", "Publish the completed article.",
                        "arguments", Map.of("request", Map.of("title", "Title"))))
                .build());

        Assert.assertFalse(Boolean.TRUE.equals(result.getTerminal()));
        Assert.assertEquals("csdn_publisher_publisharticle", capability.get());
    }

    @Test
    public void approval_for_child_tool_is_created_on_parent_run_and_keeps_child_resume_metadata() {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        ParentChildRunRelationVO relation = ParentChildRunRelationVO.builder()
                .parentRunId("parent-1")
                .childRunId("child-1")
                .taskId("task-1")
                .build();
        registry.register(relation);
        UserInteractionManager interactionManager = mock(UserInteractionManager.class);
        when(interactionManager.createPendingInput(any())).thenReturn(PendingInputCreateResult.builder()
                .created(true)
                .pendingInputId("pending-1")
                .build());
        RuntimeExecutionContext parentContext = RuntimeExecutionContext.builder()
                .runId("parent-1")
                .sessionId("session-1")
                .build();
        PendingInputPauseIntentVO pauseIntent = PendingInputPauseIntentVO.builder()
                .handler(ToolApprovalPendingInputHandler.HANDLER_CODE)
                .resumePhase(RuntimePhaseEnumVO.PREPARING_TOOL)
                .pendingType("TOOL_APPROVAL")
                .expectedAnswerValueType("OPTION")
                .askUserRequest(AskUserRequestVO.builder()
                        .question("Allow publish?")
                        .inputMode("SINGLE_CHOICE")
                        .allowFreeText(false)
                        .options(java.util.List.of())
                        .build())
                .sourcePayload(Map.of(
                        "approvalId", "approval-1",
                        "approvalKey", "approval-key",
                        "toolCallId", "tool-call-1",
                        "argumentsHash", "args-hash",
                        "permissionMode", "ASK_USER",
                        "capabilityCode", "csdn_publisher_publisharticle",
                        "mcpServerCode", "csdn",
                        "toolName", "publishArticle",
                        "toolIntent", Map.of(
                                "capabilityCode", "csdn_publisher_publisharticle",
                                "mcpServerCode", "csdn",
                                "toolName", "publishArticle")))
                .build();
        SubAgentCallToolActionHandler handler = new SubAgentCallToolActionHandler(registry,
                command -> ToolActionResultVO.builder()
                        .status(ToolActionStatusEnumVO.WAITING_USER)
                        .pauseIntent(pauseIntent)
                        .askUserRequest(pauseIntent.getAskUserRequest())
                        .build(), interactionManager);

        SubAgentActionHandlerResultVO result = handler.handle(
                SubAgentActionExecutionContextVO.builder()
                        .relation(relation)
                        .loopIndex(1)
                        .command(GenericSubAgentRunCommandVO.builder()
                                .sessionId("session-1")
                                .effectiveCapabilityCodes(Set.of(AgentCapabilityCodeEnumVO.MCP_TOOL.code()))
                                .parentRuntimeContext(parentContext)
                                .build())
                        .build(),
                SubAgentActionVO.builder()
                        .action("CALL_TOOL")
                        .actionInput(Map.of(
                                "capabilityCode", "MCP_TOOL",
                                "toolName", "publishArticle",
                                "goal", "Publish the completed article.",
                                "arguments", Map.of("title", "Title")))
                        .build());

        Assert.assertEquals(ChildAgentRunStatusEnumVO.WAITING_USER, result.getStatus());
        Assert.assertEquals("pending-1", result.getPendingInputId());
        org.mockito.ArgumentCaptor<yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand> captor =
                org.mockito.ArgumentCaptor.forClass(yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand.class);
        verify(interactionManager).createPendingInput(captor.capture());
        Assert.assertEquals("parent-1", captor.getValue().getRunId());
        Assert.assertEquals("parent-1", captor.getValue().getContinuation().getRelatedRunId());
        Assert.assertEquals("child-1", captor.getValue().getContinuation().getPayload().get("childRunId"));
        Assert.assertEquals("child-1", captor.getValue().getContinuation().getPayload().get("approvalRunId"));
    }
}
