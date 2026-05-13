package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.RunStatusEnumVO;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.DefaultAutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.MainAgentNodePort;
import yhx.com.domain.agent.service.runtime.RuntimeResult;
import yhx.com.domain.agent.service.runtime.RuntimeStartCommand;

public class AutoAgentDirectRuntimeSliceTest {

    @Test
    public void test_directAnswerFinalAction_returnsGuardedFinalResponse() {
        MainAgentNodePort fakeMainAgent = stateViewJson -> "{"
                + "\"action\":\"FINAL\","
                + "\"content\":{\"text\":\"RAG combines retrieval with generation.\"},"
                + "\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"RAG combines retrieval with generation.\"}}"
                + "}";
        AutoAgentRuntimeService runtime = new DefaultAutoAgentRuntimeService(fakeMainAgent);

        RuntimeResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run_001")
                .sessionId("sess_001")
                .userId("user_001")
                .userInput("Explain RAG in one sentence.")
                .build());

        Assert.assertEquals(RunStatusEnumVO.COMPLETED, result.getRunStatus());
        Assert.assertEquals("RAG combines retrieval with generation.", result.getFinalAnswer());
        Assert.assertFalse(result.getFinalAnswer().contains("Runtime"));
        Assert.assertFalse(result.getFinalAnswer().contains("StateDelta"));
    }

    @Test
    public void test_directAnswerFinalAction_rejectsInternalLeak() {
        MainAgentNodePort fakeMainAgent = stateViewJson -> "{"
                + "\"action\":\"FINAL\","
                + "\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"Runtime accepted the StateDelta.\"}}"
                + "}";
        AutoAgentRuntimeService runtime = new DefaultAutoAgentRuntimeService(fakeMainAgent);

        RuntimeResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run_002")
                .sessionId("sess_001")
                .userId("user_001")
                .userInput("Explain RAG.")
                .build());

        Assert.assertEquals(RunStatusEnumVO.FAILED, result.getRunStatus());
        Assert.assertEquals("The answer could not be safely delivered.", result.getFinalAnswer());
    }
}
