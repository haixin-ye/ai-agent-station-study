package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;

public interface PendingInputContinuationHandler {

    String handlerCode();

    RuntimeStepResult handle(UserAnswerVO answer, ContinuationCheckpointVO checkpoint, RuntimeExecutionContext context);
}
