package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.LoopRuntimeOutcomeVO;
import yhx.com.domain.agent.model.valobj.runtime.RunBaseContextVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.service.runtime.RunTimelineQueryService;

import java.util.List;
import java.util.Map;

public class RunTimelineQueryServiceTest {

    private final RunTimelineQueryService service = new RunTimelineQueryService();

    @Test
    public void finds_successful_matching_tool_call_from_canonical_timeline() {
        Map<String, Object> intent = toolIntent("write_file", Map.of("path", "E:/project/result.txt"));
        RunLoopRecordVO record = RunLoopRecordVO.builder()
                .loopIndex(0)
                .mainOutput(action("CALL_TOOL", Map.of("toolIntent", intent)))
                .runtimeOutcome(LoopRuntimeOutcomeVO.builder()
                        .details(Map.of("effectStatus", "TOOL_SUCCEEDED"))
                        .build())
                .build();

        Assert.assertSame(record, service.findSuccessfulToolCall(state(record), intent));
    }

    @Test
    public void distinguishes_different_tool_arguments() {
        Map<String, Object> completed = toolIntent("write_file", Map.of("path", "E:/project/a.txt"));
        Map<String, Object> requested = toolIntent("write_file", Map.of("path", "E:/project/b.txt"));
        RunLoopRecordVO record = RunLoopRecordVO.builder()
                .mainOutput(action("CALL_TOOL", Map.of("toolIntent", completed)))
                .runtimeOutcome(LoopRuntimeOutcomeVO.builder()
                        .details(Map.of("effectStatus", "TOOL_SUCCEEDED"))
                        .build())
                .build();

        Assert.assertNull(service.findSuccessfulToolCall(state(record), requested));
    }

    @Test
    public void optional_mcp_server_hint_does_not_hide_the_same_successful_tool_call() {
        Map<String, Object> completed = Map.of(
                "capabilityCode", "file_system_write_file",
                "toolName", "write_file",
                "arguments", Map.of("path", "E:/project/result.txt"));
        Map<String, Object> requested = toolIntent("write_file", Map.of("path", "E:/project/result.txt"));
        RunLoopRecordVO record = RunLoopRecordVO.builder()
                .mainOutput(action("CALL_TOOL", Map.of("toolIntent", completed)))
                .runtimeOutcome(LoopRuntimeOutcomeVO.builder()
                        .details(Map.of("effectStatus", "TOOL_SUCCEEDED"))
                        .build())
                .build();

        Assert.assertSame(record, service.findSuccessfulToolCall(state(record), requested));
    }

    @Test
    public void explicit_different_mcp_servers_remain_distinct() {
        Map<String, Object> completed = toolIntent("write_file", Map.of("path", "E:/project/result.txt"));
        Map<String, Object> requested = Map.of(
                "capabilityCode", "file_system_write_file",
                "mcpServerCode", "other-file-system",
                "toolName", "write_file",
                "arguments", Map.of("path", "E:/project/result.txt"));
        RunLoopRecordVO record = RunLoopRecordVO.builder()
                .mainOutput(action("CALL_TOOL", Map.of("toolIntent", completed)))
                .runtimeOutcome(LoopRuntimeOutcomeVO.builder()
                        .details(Map.of("effectStatus", "TOOL_SUCCEEDED"))
                        .build())
                .build();

        Assert.assertNull(service.findSuccessfulToolCall(state(record), requested));
    }

    @Test
    public void rejected_tool_decision_is_available_to_runtime_and_main_agent_timeline() {
        Map<String, Object> intent = toolIntent("delete_file", Map.of("path", "E:/project/a.txt"));
        RunLoopRecordVO record = RunLoopRecordVO.builder()
                .mainOutput(action("CALL_TOOL", Map.of("toolIntent", intent)))
                .userInteraction(Map.of("answer", Map.of("value", Map.of("decision", "REJECTED"))))
                .build();

        Assert.assertTrue(service.wasToolCallRejected(state(record), intent));
    }

    @Test
    public void pre_main_clarification_is_answered_from_canonical_run_context() {
        RunContextStateVO state = RunContextStateVO.builder()
                .baseContext(RunBaseContextVO.builder()
                        .userClarifications(List.of(UserClarificationVO.builder()
                                .pendingId("pending-context")
                                .question("Which draft?")
                                .answerType("FREE_TEXT")
                                .freeText("The latest draft")
                                .build()))
                        .build())
                .loopTimeline(List.of())
                .build();

        Assert.assertTrue(service.hasAnsweredQuestion(state, "Which draft?"));
        Assert.assertEquals(1, service.userClarifications(state).size());
    }

    @Test
    public void timeline_projection_does_not_duplicate_canonical_clarification() {
        UserClarificationVO clarification = UserClarificationVO.builder()
                .pendingId("pending-duplicate")
                .question("Which format?")
                .answerType("FREE_TEXT")
                .freeText("Markdown")
                .build();
        RunLoopRecordVO record = RunLoopRecordVO.builder()
                .mainOutput(action("ASK_USER", Map.of("askUserRequest", Map.of("question", "Which format?"))))
                .userInteraction(Map.of("answer", Map.of(
                        "pendingId", "pending-duplicate",
                        "answerType", "FREE_TEXT",
                        "freeText", "Markdown",
                        "value", "Markdown")))
                .build();
        RunContextStateVO state = RunContextStateVO.builder()
                .baseContext(RunBaseContextVO.builder()
                        .userClarifications(List.of(clarification))
                        .build())
                .loopTimeline(List.of(record))
                .build();

        Assert.assertEquals(1, service.userClarifications(state).size());
    }

    private RunContextStateVO state(RunLoopRecordVO record) {
        return RunContextStateVO.builder().loopTimeline(List.of(record)).build();
    }

    private MainAgentActionVO action(String action, Map<String, Object> stateDelta) {
        return MainAgentActionVO.builder().action(action).stateDelta(stateDelta).build();
    }

    private Map<String, Object> toolIntent(String toolName, Map<String, Object> arguments) {
        return Map.of(
                "capabilityCode", "file_system_" + toolName,
                "mcpServerCode", "file-system",
                "toolName", toolName,
                "arguments", arguments);
    }
}
