package yhx.com.test.trigger.agent;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import yhx.com.api.dto.agent.AgentChatRequestDTO;
import yhx.com.api.response.Response;
import yhx.com.trigger.http.AgentChatController;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public class AgentExecutorSubmissionTest {

    @Test
    public void rejected_chat_submission_returns_failed_response() {
        AgentChatController controller = new AgentChatController();
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("saturated");
        };
        ReflectionTestUtils.setField(controller, "agentExecutionExecutor", rejectingExecutor);

        Response<?> response = controller.chat(AgentChatRequestDTO.builder()
                .sessionId("sess-1")
                .content("hello")
                .build());

        Assert.assertEquals("0001", response.getCode());
        Assert.assertNull(response.getData());
    }
}
