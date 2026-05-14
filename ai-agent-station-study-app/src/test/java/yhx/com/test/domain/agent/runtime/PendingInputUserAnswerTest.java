package yhx.com.test.domain.agent.runtime;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.interaction.UserInputResolveCommand;
import yhx.com.domain.agent.service.interaction.UserReplyProcessor;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.List;
import java.util.Map;

public class PendingInputUserAnswerTest {

    @Test
    public void option_click_uses_stored_option_value() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AgentPendingInputEntity pendingInput = pending(repository, "SINGLE_CHOICE", "CONTEXT_CLARIFICATION");

        UserAnswerVO answer = new UserReplyProcessor(repository).process(pendingInput, UserInputResolveCommand.builder()
                .pendingId("pending-001")
                .selectedOptionId("opt-1")
                .build());

        Assert.assertEquals(UserAnswerStatusEnumVO.RESOLVED, answer.getStatus());
        Assert.assertEquals(UserAnswerTypeEnumVO.OPTION, answer.getAnswerType());
        Assert.assertEquals("artifact-latest", ((Map<?, ?>) answer.getValue()).get("artifactId"));
    }

    @Test
    public void free_text_is_preserved_without_semantic_parsing() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AgentPendingInputEntity pendingInput = pending(repository, "SINGLE_CHOICE_OR_FREE_TEXT", "CONTEXT_CLARIFICATION");

        UserAnswerVO answer = new UserReplyProcessor(repository).process(pendingInput, UserInputResolveCommand.builder()
                .pendingId("pending-001")
                .freeText("就用上一轮那篇文章")
                .build());

        Assert.assertEquals(UserAnswerStatusEnumVO.RESOLVED, answer.getStatus());
        Assert.assertEquals(UserAnswerTypeEnumVO.FREE_TEXT, answer.getAnswerType());
        Assert.assertEquals("就用上一轮那篇文章", answer.getValue());
    }

    @Test
    public void single_choice_rejects_free_text() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AgentPendingInputEntity pendingInput = pending(repository, "SINGLE_CHOICE", "CONTEXT_CLARIFICATION");

        UserAnswerVO answer = new UserReplyProcessor(repository).process(pendingInput, UserInputResolveCommand.builder()
                .freeText("随便")
                .build());

        Assert.assertEquals(UserAnswerStatusEnumVO.FAILED, answer.getStatus());
    }

    @Test
    public void tool_approval_rejects_free_text() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AgentPendingInputEntity pendingInput = pending(repository, "SINGLE_CHOICE", PendingInputTypeEnumVO.TOOL_APPROVAL.code());

        UserAnswerVO answer = new UserReplyProcessor(repository).process(pendingInput, UserInputResolveCommand.builder()
                .freeText("ok")
                .build());

        Assert.assertEquals(UserAnswerStatusEnumVO.FAILED, answer.getStatus());
    }

    @Test
    public void unknown_option_id_returns_failed_answer() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AgentPendingInputEntity pendingInput = pending(repository, "SINGLE_CHOICE", "CONTEXT_CLARIFICATION");

        UserAnswerVO answer = new UserReplyProcessor(repository).process(pendingInput, UserInputResolveCommand.builder()
                .selectedOptionId("missing")
                .build());

        Assert.assertEquals(UserAnswerStatusEnumVO.FAILED, answer.getStatus());
    }

    @Test
    public void cancel_returns_cancelled_answer() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AgentPendingInputEntity pendingInput = pending(repository, "SINGLE_CHOICE", "CONTEXT_CLARIFICATION");

        UserAnswerVO answer = new UserReplyProcessor(repository).process(pendingInput, UserInputResolveCommand.builder()
                .cancelled(true)
                .build());

        Assert.assertEquals(UserAnswerStatusEnumVO.CANCELLED, answer.getStatus());
        Assert.assertEquals(UserAnswerTypeEnumVO.CANCEL, answer.getAnswerType());
    }

    private AgentPendingInputEntity pending(RuntimeTestSupport.InMemoryRuntimeRepository repository, String inputMode, String pendingType) {
        String optionsRef = repository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(List.of(
                        Map.of("id", "opt-1", "label", "最新文章", "value", Map.of("artifactId", "artifact-latest")),
                        Map.of("id", "approve", "label", "同意", "value", Map.of("decision", "APPROVED")),
                        Map.of("id", "reject", "label", "拒绝", "value", Map.of("decision", "REJECTED"))
                )))
                .build());
        return AgentPendingInputEntity.builder()
                .pendingId("pending-001")
                .runId("run-001")
                .pendingType(pendingType)
                .inputMode(inputMode)
                .status("PENDING")
                .optionsRef(optionsRef)
                .build();
    }
}
