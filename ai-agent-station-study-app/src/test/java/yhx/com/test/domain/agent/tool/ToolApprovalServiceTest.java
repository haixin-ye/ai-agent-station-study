package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolApprovalDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.PermissionDecisionVO;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalDecisionCommandVO;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalDecisionResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolIntentVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.service.tool.ToolApprovalService;

import java.util.Map;

public class ToolApprovalServiceTest {

    @Test
    public void pending_approval_is_reused_by_approval_key() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        repository.saveApproval(approval("key-1", ToolApprovalStatusEnumVO.PENDING));
        ToolApprovalService service = service(repository, new ToolTestSupport.FakeUserInteractionManager());

        ToolApprovalDecisionResultVO result = service.ensureApproval(command("key-1"));

        Assert.assertEquals(ToolApprovalDecisionStatusEnumVO.PENDING, result.getStatus());
        Assert.assertEquals(1, repository.approvals.size());
    }

    @Test
    public void tool_approval_uses_single_choice_without_free_text() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolTestSupport.FakeUserInteractionManager interactionManager = new ToolTestSupport.FakeUserInteractionManager();
        ToolApprovalService service = service(repository, interactionManager);

        service.ensureApproval(command("key-1"));

        Assert.assertEquals("SINGLE_CHOICE", interactionManager.askUserRequest().getInputMode());
        Assert.assertFalse(interactionManager.askUserRequest().getAllowFreeText());
        Assert.assertEquals(2, interactionManager.askUserRequest().getOptions().size());
    }

    @Test
    public void tool_approval_question_summarizes_tool_intent_and_arguments() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolTestSupport.FakeUserInteractionManager interactionManager = new ToolTestSupport.FakeUserInteractionManager();
        ToolApprovalService service = service(repository, interactionManager);

        service.ensureApproval(command("key-1",
                ToolIntentVO.builder()
                        .capabilityCode("file_system_write_file")
                        .toolName("write_file")
                        .goal("Replace the target file with the expanded story.")
                        .arguments(Map.of(
                                "path", "E:/project/docs/story.md",
                                "content", "Story title\n\nThis is a long replacement content that should be previewed without rendering the full payload."))
                        .build()));

        String question = interactionManager.askUserRequest().getQuestion();
        Assert.assertTrue(question.contains("write_file"));
        Assert.assertTrue(question.contains("Replace the target file with the expanded story."));
        Assert.assertTrue(question.contains("E:/project/docs/story.md"));
        Assert.assertTrue(question.contains("内容预览"));
        Assert.assertFalse(question.contains("without rendering the full payload."));
    }

    @Test
    public void free_text_does_not_approve_tool() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolApprovalService service = service(repository, new ToolTestSupport.FakeUserInteractionManager());
        ToolApprovalEntity approval = approval("key-1", ToolApprovalStatusEnumVO.PENDING);
        repository.saveApproval(approval);

        ToolApprovalDecisionResultVO result = service.handleUserDecision(UserAnswerVO.builder()
                .answerType(UserAnswerTypeEnumVO.FREE_TEXT)
                .value("yes")
                .build(), approval);

        Assert.assertEquals(ToolApprovalDecisionStatusEnumVO.REJECTED, result.getStatus());
    }

    @Test
    public void approve_option_marks_approval_approved() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolApprovalService service = service(repository, new ToolTestSupport.FakeUserInteractionManager());
        ToolApprovalEntity approval = approval("key-1", ToolApprovalStatusEnumVO.PENDING);
        repository.saveApproval(approval);

        ToolApprovalDecisionResultVO result = service.handleUserDecision(UserAnswerVO.builder()
                .answerType(UserAnswerTypeEnumVO.OPTION)
                .value(Map.of("decision", "APPROVED"))
                .build(), approval);

        Assert.assertEquals(ToolApprovalDecisionStatusEnumVO.APPROVED, result.getStatus());
        Assert.assertEquals(ToolApprovalStatusEnumVO.APPROVED, repository.approvals.get(approval.getApprovalId()).getStatus());
    }

    @Test
    public void reject_option_marks_approval_rejected() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolApprovalService service = service(repository, new ToolTestSupport.FakeUserInteractionManager());
        ToolApprovalEntity approval = approval("key-1", ToolApprovalStatusEnumVO.PENDING);
        repository.saveApproval(approval);

        ToolApprovalDecisionResultVO result = service.handleUserDecision(UserAnswerVO.builder()
                .answerType(UserAnswerTypeEnumVO.OPTION)
                .value(Map.of("decision", "REJECTED"))
                .build(), approval);

        Assert.assertEquals(ToolApprovalDecisionStatusEnumVO.REJECTED, result.getStatus());
        Assert.assertEquals(ToolApprovalStatusEnumVO.REJECTED, repository.approvals.get(approval.getApprovalId()).getStatus());
    }

    @Test
    public void decided_approval_cannot_be_decided_again() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolApprovalService service = service(repository, new ToolTestSupport.FakeUserInteractionManager());
        ToolApprovalEntity approval = approval("key-1", ToolApprovalStatusEnumVO.APPROVED);
        repository.saveApproval(approval);

        ToolApprovalDecisionResultVO result = service.handleUserDecision(UserAnswerVO.builder()
                .answerType(UserAnswerTypeEnumVO.OPTION)
                .value(Map.of("decision", "REJECTED"))
                .build(), approval);

        Assert.assertEquals(ToolApprovalDecisionStatusEnumVO.DENIED, result.getStatus());
        Assert.assertEquals("TOOL_APPROVAL_ALREADY_RESOLVED", result.getFailureCode());
        Assert.assertEquals(ToolApprovalStatusEnumVO.APPROVED, repository.approvals.get(approval.getApprovalId()).getStatus());
    }

    @Test
    public void approval_checkpoint_uses_resolved_canonical_tool_identity() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolTestSupport.FakeUserInteractionManager interactionManager = new ToolTestSupport.FakeUserInteractionManager();
        ToolApprovalService service = service(repository, interactionManager);

        service.ensureApproval(command("key-canonical", ToolIntentVO.builder()
                .capabilityCode("publish")
                .toolName("tool")
                .arguments(Map.of("path", "docs/story.md"))
                .build()));

        Map<?, ?> payload = interactionManager.lastCommand.getContinuation().getPayload();
        Map<?, ?> toolIntent = (Map<?, ?>) payload.get("toolIntent");
        Assert.assertEquals("publish", toolIntent.get("capabilityCode"));
        Assert.assertEquals("server", toolIntent.get("mcpServerCode"));
        Assert.assertEquals("tool", toolIntent.get("toolName"));
    }

    private ToolApprovalService service(ToolTestSupport.Repository repository, ToolTestSupport.FakeUserInteractionManager interactionManager) {
        return new ToolApprovalService(repository, repository, interactionManager);
    }

    private ToolApprovalDecisionCommandVO command(String approvalKey) {
        return command(approvalKey, null);
    }

    private ToolApprovalDecisionCommandVO command(String approvalKey, ToolIntentVO toolIntent) {
        return ToolApprovalDecisionCommandVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .toolCallId("tool-call-001")
                .approvalKey(approvalKey)
                .argumentsHash("hash-1")
                .capability(CapabilitySpecVO.builder()
                        .capabilityCode("publish")
                        .mcpServerCode("server")
                        .toolName("tool")
                        .permissionMode(PermissionModeEnumVO.ASK_USER)
                        .build())
                .toolSpec(McpToolSpecVO.builder()
                        .mcpServerCode("server")
                        .toolName("tool")
                        .build())
                .permissionDecision(PermissionDecisionVO.builder()
                        .status(PermissionDecisionStatusEnumVO.ASK_USER)
                        .failureCode("TOOL_APPROVAL_REQUIRED")
                        .reason("approval required")
                        .build())
                .toolIntent(toolIntent)
                .build();
    }

    private ToolApprovalEntity approval(String approvalKey, ToolApprovalStatusEnumVO status) {
        return ToolApprovalEntity.builder()
                .approvalKey(approvalKey)
                .runId("run-001")
                .toolCallId("tool-call-001")
                .status(status)
                .permissionMode("ASK_USER")
                .argumentsHash("hash-1")
                .build();
    }
}
