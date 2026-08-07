package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.RunBaseContextVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.interaction.RuntimeUserClarificationRecorder;

import java.util.ArrayList;
import java.util.HashMap;

public class RuntimeUserClarificationPersistenceTest {

    @Test
    public void clarification_is_written_to_canonical_context_and_runtime_projection() {
        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runtimeFacts(new HashMap<>())
                .runContextState(RunContextStateVO.builder()
                        .mainAgentStage(MainAgentStageEnumVO.PLANNING)
                        .baseContext(RunBaseContextVO.builder()
                                .runId("run-clarification")
                                .userClarifications(new ArrayList<>())
                                .build())
                        .build())
                .build();

        new RuntimeUserClarificationRecorder().append(context, UserClarificationVO.builder()
                .pendingId("pending-1")
                .question("Which format?")
                .answerType("FREE_TEXT")
                .freeText("Markdown")
                .build());

        Assert.assertEquals("Markdown",
                context.getRunContextState().getBaseContext().getUserClarifications().get(0).getFreeText());
        Assert.assertEquals("Markdown",
                ((UserClarificationVO) ((java.util.List<?>) context.getRuntimeFacts()
                        .get("userClarifications")).get(0)).getFreeText());
    }
}
