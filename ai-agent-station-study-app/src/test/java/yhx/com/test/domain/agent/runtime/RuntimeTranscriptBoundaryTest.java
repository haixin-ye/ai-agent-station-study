package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.MessageRoleEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TranscriptBlockTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeResumeCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.List;
import java.util.Map;

public class RuntimeTranscriptBoundaryTest {

    @Test
    public void user_message_appended_before_run_loop() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository, RuntimeTestSupport.fixedPorts(finalAction()), true, new RuntimeLoopPolicy());

        runtime.start(RuntimeStartCommand.builder().runId("run-001").sessionId("sess-001").userId("u1").userInput("hello").build());

        Assert.assertEquals(1, repository.messages.size());
        Assert.assertEquals(MessageRoleEnumVO.USER, repository.messages.get(0).getRole());
        Assert.assertEquals(TranscriptBlockTypeEnumVO.USER_MESSAGE, repository.transcriptBlocks.get(0).getBlockType());
    }

    @Test
    public void pending_input_checkpoint_recorded_before_waiting_user() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository, RuntimeTestSupport.fixedPorts(askUserAction()), true, new RuntimeLoopPolicy());

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder().runId("run-002").sessionId("sess-002").userId("u1").userInput("which one").build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.WAITING_USER, result.getStatus());
        Assert.assertEquals(1, repository.pendingInputs.size());
        Assert.assertNotNull(repository.pendingInputs.values().iterator().next().getContinuationRef());
    }

    @Test
    public void user_reply_recorded_before_continuation_dispatch() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository, RuntimeTestSupport.fixedPorts(askUserAction()), true, new RuntimeLoopPolicy());
        RuntimeStepResult waiting = runtime.start(RuntimeStartCommand.builder().runId("run-003").sessionId("sess-003").userId("u1").userInput("which one").build());

        runtime.resume(RuntimeResumeCommand.builder()
                .runId("run-003")
                .pendingId(waiting.getPendingInputId())
                .freeText("继续")
                .build());

        Assert.assertTrue(repository.transcriptBlocks.stream()
                .anyMatch(block -> TranscriptBlockTypeEnumVO.USER_MESSAGE == block.getBlockType() && !block.getPayloadRef().equals(repository.messages.get(0).getContentRef())));
    }

    @Test
    public void normal_message_table_is_not_used_as_internal_transcript() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository, RuntimeTestSupport.fixedPorts(finalAction()), true, new RuntimeLoopPolicy());

        runtime.start(RuntimeStartCommand.builder().runId("run-004").sessionId("sess-004").userId("u1").userInput("hello").build());

        Assert.assertEquals(1, repository.messages.size());
        Assert.assertTrue(repository.transcriptBlocks.size() > repository.messages.size());
    }

    private MainAgentActionVO finalAction() {
        return MainAgentActionVO.builder()
                .action("FINAL")
                .stateDelta(Map.of("finalAnswerCandidate", Map.of("content", "done")))
                .build();
    }

    private MainAgentActionVO askUserAction() {
        return MainAgentActionVO.builder()
                .action("ASK_USER")
                .stateDelta(Map.of("askUserRequest", Map.of(
                        "question", "请选择",
                        "inputMode", "SINGLE_CHOICE_OR_FREE_TEXT",
                        "allowFreeText", true,
                        "options", List.of(Map.of("id", "opt-1", "label", "A", "value", Map.of("choice", "A")))
                )))
                .build();
    }
}
