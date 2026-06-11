package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.Map;

public class AutoAgentDirectRuntimeSliceTest {

    @Test
    public void direct_final_action_completes_same_run() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository, RuntimeTestSupport.fixedPorts(finalAction()), true, new RuntimeLoopPolicy());

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run_001")
                .sessionId("sess_001")
                .userId("user_001")
                .userInput("Explain RAG in one sentence.")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.COMPLETED, result.getStatus());
        Assert.assertEquals(RunStatusEnumVO.COMPLETED, repository.runs.get("run_001").getStatus());
        Assert.assertEquals("RAG combines retrieval with generation.", result.getFinalAnswer());
        Assert.assertEquals(1, repository.messages.size());
        Assert.assertFalse(repository.transcriptBlocks.isEmpty());
    }

    @Test
    public void missing_action_handler_returns_safe_failure() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        MainAgentActionVO toolAction = MainAgentActionVO.builder()
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolRequest", Map.of("toolName", "demo")))
                .build();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository, RuntimeTestSupport.fixedPorts(toolAction), false, new RuntimeLoopPolicy());

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run_002")
                .sessionId("sess_002")
                .userId("user_001")
                .userInput("Call a tool.")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertEquals(RunStatusEnumVO.FAILED, repository.runs.get("run_002").getStatus());
        Assert.assertNotNull(result.getSafeFailure());
    }

    private MainAgentActionVO finalAction() {
        return MainAgentActionVO.builder()
                .action("FINAL")
                .stateDelta(Map.of("finalAnswerCandidate", Map.of("content", "RAG combines retrieval with generation.")))
                .build();
    }
}
