package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public class RuntimeStateMachine {

    private final Map<RuntimePhaseEnumVO, EnumSet<RuntimePhaseEnumVO>> transitions = new EnumMap<>(RuntimePhaseEnumVO.class);

    public RuntimeStateMachine() {
        allow(RuntimePhaseEnumVO.CREATED, RuntimePhaseEnumVO.PREPARING_CONTEXT);
        allow(RuntimePhaseEnumVO.PREPARING_CONTEXT, RuntimePhaseEnumVO.PLANNING_CONTEXT, RuntimePhaseEnumVO.BUILDING_STATE_VIEW);
        allow(RuntimePhaseEnumVO.PLANNING_CONTEXT, RuntimePhaseEnumVO.PREPARING_CONTEXT,
                RuntimePhaseEnumVO.BUILDING_STATE_VIEW, RuntimePhaseEnumVO.WAITING_USER, RuntimePhaseEnumVO.FAILED);
        allow(RuntimePhaseEnumVO.BUILDING_STATE_VIEW, RuntimePhaseEnumVO.CALLING_MAIN_NODE);
        allow(RuntimePhaseEnumVO.CALLING_MAIN_NODE, RuntimePhaseEnumVO.VALIDATING_ACTION);
        allow(RuntimePhaseEnumVO.VALIDATING_ACTION, RuntimePhaseEnumVO.HANDLING_ACTION, RuntimePhaseEnumVO.REPAIRING_CONTRACT, RuntimePhaseEnumVO.FAILED);
        allow(RuntimePhaseEnumVO.REPAIRING_CONTRACT, RuntimePhaseEnumVO.VALIDATING_ACTION, RuntimePhaseEnumVO.FAILED);
        allow(RuntimePhaseEnumVO.HANDLING_ACTION, RuntimePhaseEnumVO.PREPARING_CONTEXT, RuntimePhaseEnumVO.EXECUTING_RAG,
                RuntimePhaseEnumVO.PREPARING_TOOL, RuntimePhaseEnumVO.VERIFYING_FINAL, RuntimePhaseEnumVO.WAITING_USER,
                RuntimePhaseEnumVO.BUILDING_STATE_VIEW, RuntimePhaseEnumVO.CALLING_MAIN_NODE,
                RuntimePhaseEnumVO.COMPLETED, RuntimePhaseEnumVO.FAILED);
        allow(RuntimePhaseEnumVO.EXECUTING_RAG, RuntimePhaseEnumVO.BUILDING_STATE_VIEW, RuntimePhaseEnumVO.CALLING_MAIN_NODE);
        allow(RuntimePhaseEnumVO.PREPARING_TOOL, RuntimePhaseEnumVO.HANDLING_ACTION, RuntimePhaseEnumVO.WAITING_USER, RuntimePhaseEnumVO.INVOKING_TOOL_RUNTIME, RuntimePhaseEnumVO.FAILED);
        allow(RuntimePhaseEnumVO.INVOKING_TOOL_RUNTIME, RuntimePhaseEnumVO.VERIFYING_TOOL);
        allow(RuntimePhaseEnumVO.VERIFYING_TOOL, RuntimePhaseEnumVO.BUILDING_STATE_VIEW, RuntimePhaseEnumVO.CALLING_MAIN_NODE,
                RuntimePhaseEnumVO.PREPARING_CONTEXT, RuntimePhaseEnumVO.FAILED);
        allow(RuntimePhaseEnumVO.VERIFYING_FINAL, RuntimePhaseEnumVO.REPAIRING_FINAL, RuntimePhaseEnumVO.COMPLETED, RuntimePhaseEnumVO.FAILED);
        allow(RuntimePhaseEnumVO.REPAIRING_FINAL, RuntimePhaseEnumVO.VERIFYING_FINAL);
        allow(RuntimePhaseEnumVO.WAITING_USER, RuntimePhaseEnumVO.RESOLVING_USER_ANSWER);
        allow(RuntimePhaseEnumVO.RESOLVING_USER_ANSWER, RuntimePhaseEnumVO.PREPARING_CONTEXT, RuntimePhaseEnumVO.BUILDING_STATE_VIEW,
                RuntimePhaseEnumVO.PREPARING_TOOL,
                RuntimePhaseEnumVO.CALLING_MAIN_NODE, RuntimePhaseEnumVO.CANCELLED, RuntimePhaseEnumVO.FAILED);
    }

    public RuntimePhaseEnumVO nextAfterStart() {
        return RuntimePhaseEnumVO.PREPARING_CONTEXT;
    }

    public RuntimePhaseEnumVO nextAfterContextPrepared(ContextPlannerOutputVO plannerOutput) {
        if (plannerOutput == null || plannerOutput.getStatus() == null) {
            return RuntimePhaseEnumVO.BUILDING_STATE_VIEW;
        }
        if ("NEEDS_USER_CLARIFICATION".equals(plannerOutput.getStatus())) {
            return RuntimePhaseEnumVO.WAITING_USER;
        }
        if ("FAILED".equals(plannerOutput.getStatus())) {
            return RuntimePhaseEnumVO.FAILED;
        }
        return RuntimePhaseEnumVO.BUILDING_STATE_VIEW;
    }

    public RuntimePhaseEnumVO nextAfterMainAction(MainAgentActionTypeEnumVO actionType) {
        if (actionType == null) {
            return RuntimePhaseEnumVO.FAILED;
        }
        return switch (actionType) {
            case FINAL, FAIL, REPAIR_FINAL -> RuntimePhaseEnumVO.VERIFYING_FINAL;
            case RETRIEVE_RAG -> RuntimePhaseEnumVO.EXECUTING_RAG;
            case CALL_TOOL -> RuntimePhaseEnumVO.PREPARING_TOOL;
            case ASK_USER -> RuntimePhaseEnumVO.WAITING_USER;
            case PLAN, CONTINUE -> RuntimePhaseEnumVO.CALLING_MAIN_NODE;
        };
    }

    public boolean canEnter(RuntimePhaseEnumVO from, RuntimePhaseEnumVO to) {
        if (from == null || to == null) {
            return false;
        }
        return transitions.getOrDefault(from, EnumSet.noneOf(RuntimePhaseEnumVO.class)).contains(to);
    }

    public boolean isTerminalRunStatus(RunStatusEnumVO status) {
        return status == RunStatusEnumVO.COMPLETED || status == RunStatusEnumVO.FAILED || status == RunStatusEnumVO.CANCELLED;
    }

    public boolean isPausedRunStatus(RunStatusEnumVO status) {
        return status == RunStatusEnumVO.WAITING_USER;
    }

    private void allow(RuntimePhaseEnumVO from, RuntimePhaseEnumVO... to) {
        transitions.computeIfAbsent(from, key -> EnumSet.noneOf(RuntimePhaseEnumVO.class)).addAll(EnumSet.of(to[0], to));
    }
}
