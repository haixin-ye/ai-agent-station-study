package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;

import java.util.EnumSet;

public class RuntimeRoutePolicy {

    private static final EnumSet<MainAgentActionTypeEnumVO> EXECUTION_ACTIONS = EnumSet.of(
            MainAgentActionTypeEnumVO.RETRIEVE_RAG,
            MainAgentActionTypeEnumVO.CALL_TOOL
    );

    public RuntimePhaseEnumVO nextLoopPhase(RuntimeExecutionContext context, RuntimeStepResult stepResult) {
        RuntimePhaseEnumVO requested = requestedPhase(stepResult);
        if (stepResult == null || stepResult.getStatus() != RuntimeStepStatusEnumVO.CONTINUE) {
            return requested;
        }
        MainAgentActionTypeEnumVO actionType = actionType(stepResult.getAction());
        if (EXECUTION_ACTIONS.contains(actionType)) {
            return RuntimePhaseEnumVO.BUILDING_STATE_VIEW;
        }
        if (requested == RuntimePhaseEnumVO.PREPARING_CONTEXT
                && context != null
                && context.getLastStateView() != null
                && !forceContextReplan(context)) {
            return RuntimePhaseEnumVO.BUILDING_STATE_VIEW;
        }
        return requested;
    }

    private RuntimePhaseEnumVO requestedPhase(RuntimeStepResult stepResult) {
        return stepResult == null || stepResult.getNextPhase() == null
                ? RuntimePhaseEnumVO.CALLING_MAIN_NODE
                : stepResult.getNextPhase();
    }

    private MainAgentActionTypeEnumVO actionType(MainAgentActionVO action) {
        if (action == null || action.getAction() == null) {
            return null;
        }
        return MainAgentActionTypeEnumVO.ofCode(action.getAction()).orElse(null);
    }

    private boolean forceContextReplan(RuntimeExecutionContext context) {
        return context != null
                && context.getRuntimeFacts() != null
                && Boolean.TRUE.equals(context.getRuntimeFacts().get("forceContextReplan"));
    }
}
