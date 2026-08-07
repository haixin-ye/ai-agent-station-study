package yhx.com.test.trigger.agent;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import yhx.com.api.dto.agent.AgentChatRequestDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.api.AgentRuntimeFacade;
import yhx.com.trigger.http.AgentChatController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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

    @Test
    public void fatal_async_failure_is_reported_instead_of_escaping_the_worker_boundary() {
        AgentChatController controller = new AgentChatController();
        AtomicReference<Throwable> reportedFailure = new AtomicReference<>();
        AgentRuntimeFacade runtimeFacade = new AgentRuntimeFacade(null) {
            @Override
            public RuntimeStepResult startWithRunId(String runId,
                                                    String sessionId,
                                                    String agentId,
                                                    String userId,
                                                    String content,
                                                    String inputType,
                                                    Map<String, Object> metadata) {
                throw new OutOfMemoryError("Java heap space");
            }

            @Override
            public RuntimeStepResult reportUnexpectedFailure(String runId, String sessionId, Throwable error) {
                reportedFailure.set(error);
                return null;
            }
        };
        ReflectionTestUtils.setField(controller, "agentRuntimeFacade", runtimeFacade);
        ReflectionTestUtils.setField(controller, "agentExecutionExecutor", (Executor) Runnable::run);

        Response<?> response = controller.chat(AgentChatRequestDTO.builder()
                .sessionId("sess-fatal")
                .content("trigger fatal failure")
                .build());

        Assert.assertEquals("0000", response.getCode());
        Assert.assertTrue(reportedFailure.get() instanceof OutOfMemoryError);
    }
}
