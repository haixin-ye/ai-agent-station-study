package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.domain.agent.service.tool.ToolApprovalPauseParticipant;

import java.util.Map;

public class ToolApprovalPauseParticipantTest {

    @Test
    public void approval_and_tool_call_transition_join_the_coordinated_pause_transaction() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        repository.createToolCall(ToolCallEntity.builder()
                .toolCallId("tool-call-1")
                .runId("run-1")
                .status(ToolCallStatusEnumVO.CREATED)
                .build());
        ToolApprovalPauseParticipant participant = new ToolApprovalPauseParticipant(repository);

        participant.beforePendingInputPersisted(PendingInputCreateCommand.builder()
                .runId("run-1")
                .continuation(ContinuationCheckpointVO.builder()
                        .handler(ToolApprovalPendingInputHandler.HANDLER_CODE)
                        .payload(Map.of(
                                "approvalId", "approval-1",
                                "approvalKey", "key-1",
                                "toolCallId", "tool-call-1",
                                "argumentsHash", "hash-1",
                                "permissionMode", "ASK_USER"))
                        .build())
                .build());

        Assert.assertEquals(ToolApprovalStatusEnumVO.PENDING,
                repository.findApprovalByApprovalKey("key-1").orElseThrow().getStatus());
        Assert.assertEquals(ToolCallStatusEnumVO.APPROVAL_PENDING,
                repository.findToolCall("tool-call-1").orElseThrow().getStatus());
    }
}
