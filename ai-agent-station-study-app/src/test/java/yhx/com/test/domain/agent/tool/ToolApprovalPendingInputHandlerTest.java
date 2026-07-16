package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.domain.agent.service.tool.ToolApprovalService;

import java.util.HashMap;
import java.util.Map;

public class ToolApprovalPendingInputHandlerTest {

    @Test
    public void approval_checkpoint_requires_tool_call_to_still_wait_for_approval() {
        ToolTestSupport.Repository repository = repositoryWithApproval(ToolCallStatusEnumVO.CREATED, "write_file");
        ToolApprovalPendingInputHandler handler = handler(repository);

        RuntimeStepResult result = handler.handle(approvedAnswer(), checkpoint("write_file"), context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertTrue(result.getMessage().contains("approval-pending"));
        Assert.assertEquals(ToolApprovalStatusEnumVO.PENDING,
                repository.findApprovalByApprovalKey("approval-key").orElseThrow().getStatus());
    }

    @Test
    public void approval_checkpoint_must_match_persisted_tool_call_identity() {
        ToolTestSupport.Repository repository = repositoryWithApproval(
                ToolCallStatusEnumVO.APPROVAL_PENDING, "delete_file");
        ToolApprovalPendingInputHandler handler = handler(repository);

        RuntimeStepResult result = handler.handle(approvedAnswer(), checkpoint("write_file"), context());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertTrue(result.getMessage().contains("persisted tool call"));
        Assert.assertEquals(ToolApprovalStatusEnumVO.PENDING,
                repository.findApprovalByApprovalKey("approval-key").orElseThrow().getStatus());
    }

    private ToolTestSupport.Repository repositoryWithApproval(ToolCallStatusEnumVO status, String toolName) {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        repository.createToolCall(ToolCallEntity.builder()
                .toolCallId("tool-call-001")
                .runId("run-001")
                .mcpServerName("server")
                .toolName(toolName)
                .status(status)
                .build());
        repository.saveApproval(ToolApprovalEntity.builder()
                .approvalKey("approval-key")
                .runId("run-001")
                .toolCallId("tool-call-001")
                .argumentsHash("args-hash")
                .permissionMode("ASK_USER")
                .status(ToolApprovalStatusEnumVO.PENDING)
                .build());
        return repository;
    }

    private ToolApprovalPendingInputHandler handler(ToolTestSupport.Repository repository) {
        ToolApprovalService service = new ToolApprovalService(repository, repository);
        return new ToolApprovalPendingInputHandler(() -> service);
    }

    private UserAnswerVO approvedAnswer() {
        return UserAnswerVO.builder()
                .status(UserAnswerStatusEnumVO.RESOLVED)
                .answerType(UserAnswerTypeEnumVO.OPTION)
                .value(Map.of("decision", "APPROVED"))
                .build();
    }

    private ContinuationCheckpointVO checkpoint(String toolName) {
        Map<String, Object> toolIntent = Map.of(
                "capabilityCode", "file_system_write_file",
                "mcpServerCode", "server",
                "toolName", toolName);
        Map<String, Object> payload = new HashMap<>();
        payload.put("approvalKey", "approval-key");
        payload.put("toolCallId", "tool-call-001");
        payload.put("argumentsHash", "args-hash");
        payload.put("capabilityCode", "file_system_write_file");
        payload.put("mcpServerCode", "server");
        payload.put("toolName", toolName);
        payload.put("toolIntent", toolIntent);
        return ContinuationCheckpointVO.builder()
                .handler(ToolApprovalPendingInputHandler.HANDLER_CODE)
                .resumePhase(RuntimePhaseEnumVO.PREPARING_TOOL)
                .relatedRunId("run-001")
                .payload(payload)
                .build();
    }

    private RuntimeExecutionContext context() {
        return RuntimeExecutionContext.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .runtimeFacts(new HashMap<>())
                .build();
    }
}
