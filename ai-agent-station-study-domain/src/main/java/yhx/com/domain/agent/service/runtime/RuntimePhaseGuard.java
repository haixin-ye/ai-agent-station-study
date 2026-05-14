package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;

public class RuntimePhaseGuard {

    private final RuntimeStateMachine stateMachine;
    private final RuntimeFailureFactory failureFactory;
    private final DeveloperTraceRecorder developerTraceRecorder;

    public RuntimePhaseGuard(RuntimeStateMachine stateMachine,
                             RuntimeFailureFactory failureFactory,
                             DeveloperTraceRecorder developerTraceRecorder) {
        this.stateMachine = stateMachine;
        this.failureFactory = failureFactory;
        this.developerTraceRecorder = developerTraceRecorder;
    }

    public RuntimeSafeFailureVO enter(RuntimeExecutionContext context, RuntimePhaseEnumVO nextPhase) {
        RuntimePhaseEnumVO current = context.getCurrentPhase();
        if (current != null && !stateMachine.canEnter(current, nextPhase)) {
            RuntimeSafeFailureVO failure = failureFactory.illegalTransition(current, nextPhase);
            developerTraceRecorder.error(context.getRunId(), context.getLoopIndex(),
                    RuntimeFailureCodeEnumVO.ILLEGAL_PHASE_TRANSITION, failure.getDeveloperMessage(), null);
            return failure;
        }
        developerTraceRecorder.phaseCompleted(context.getRunId(), context.getLoopIndex(), current);
        context.setCurrentPhase(nextPhase);
        developerTraceRecorder.phaseStarted(context.getRunId(), context.getLoopIndex(), nextPhase);
        return null;
    }
}
