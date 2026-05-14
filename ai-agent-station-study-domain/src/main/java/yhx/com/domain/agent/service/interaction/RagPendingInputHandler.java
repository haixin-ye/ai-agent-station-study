package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;

public class RagPendingInputHandler implements PendingInputContinuationHandler {

    public static final String HANDLER_CODE = "RAG";

    @Override
    public String handlerCode() {
        return HANDLER_CODE;
    }

    @Override
    public RuntimeStepResult handle(UserAnswerVO answer, ContinuationCheckpointVO checkpoint, RuntimeExecutionContext context) {
        if (answer != null && answer.getStatus() == UserAnswerStatusEnumVO.CANCELLED) {
            return RuntimeStepResult.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.CANCELLED)
                    .nextRunStatus(RunStatusEnumVO.CANCELLED)
                    .nextPhase(RuntimePhaseEnumVO.CANCELLED)
                    .message("User cancelled RAG clarification.")
                    .build();
        }
        if (context.getRuntimeFacts() != null) {
            context.getRuntimeFacts().put("ragClarification", answer);
        }
        return RuntimeStepResult.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .status(RuntimeStepStatusEnumVO.CONTINUE)
                .nextRunStatus(RunStatusEnumVO.RUNNING)
                .nextPhase(RuntimePhaseEnumVO.PREPARING_CONTEXT)
                .message("RAG clarification resolved.")
                .build();
    }
}
