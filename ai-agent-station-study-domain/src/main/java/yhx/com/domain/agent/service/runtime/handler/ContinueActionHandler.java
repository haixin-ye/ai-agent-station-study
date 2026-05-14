package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;

public class ContinueActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final RuntimeLoopPolicy loopPolicy;

    public ContinueActionHandler(RuntimeLoopPolicy loopPolicy,
                                 RuntimeFailureFactory failureFactory,
                                 DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
        this.loopPolicy = loopPolicy == null ? new RuntimeLoopPolicy() : loopPolicy;
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.CONTINUE;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        if (loopPolicy.maxLoopReached(context.countersOrInitial())) {
            return safeFailure(context, RuntimeFailureCodeEnumVO.MAX_LOOP_REACHED,
                    "The task required too many steps.", "Continue action reached loop budget.");
        }
        Object hint = requireStateDelta(action).get("nextActionHint");
        if (hint == null || String.valueOf(hint).isBlank()) {
            return validationFailure(context, "CONTINUE requires stateDelta.nextActionHint to avoid empty loops.");
        }
        if (context.getRuntimeFacts() != null) {
            context.getRuntimeFacts().put("nextActionHint", hint);
        }
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                .nextPhase(RuntimePhaseEnumVO.PREPARING_CONTEXT)
                .message("Continue hint accepted.")
                .build();
    }
}
