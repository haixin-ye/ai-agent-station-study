package yhx.com.domain.agent.service.interaction;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RunWorkingStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;

import java.util.Map;

public class MainAgentPendingInputHandler implements PendingInputContinuationHandler {

    public static final String HANDLER_CODE = "MAIN_AGENT";

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
                    .message("User cancelled MainAgent question.")
                    .build();
        }
        if (context.getRuntimeFacts() != null) {
            context.getRuntimeFacts().put("mainAgentUserAnswer", answer);
        }
        restoreWorkingState(checkpoint, context);
        return RuntimeStepResult.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .status(RuntimeStepStatusEnumVO.CONTINUE)
                .nextRunStatus(RunStatusEnumVO.RUNNING)
                .nextPhase(ContinuationCheckpointSupport.resumePhase(checkpoint, RuntimePhaseEnumVO.BUILDING_STATE_VIEW))
                .message("MainAgent pending input resolved.")
                .build();
    }

    private void restoreWorkingState(ContinuationCheckpointVO checkpoint, RuntimeExecutionContext context) {
        if (checkpoint == null || checkpoint.getPayload() == null || context == null) {
            return;
        }
        Object value = firstNonNull(checkpoint.getPayload().get("workingState"), checkpoint.getPayload().get("runWorkingState"));
        if (value == null) {
            return;
        }
        if (value instanceof RunWorkingStateVO workingState) {
            context.setWorkingState(workingState);
            return;
        }
        if (value instanceof Map<?, ?>) {
            context.setWorkingState(JSON.parseObject(JSON.toJSONString(value), RunWorkingStateVO.class));
        }
    }

    private Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }
}
