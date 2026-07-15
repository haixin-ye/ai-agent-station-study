package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;

import java.util.Map;
import java.util.Set;

public class ContinuationResumePhasePolicy {

    private static final Map<String, Set<RuntimePhaseEnumVO>> ALLOWED_PHASES = Map.of(
            ContextPlannerPendingInputHandler.HANDLER_CODE,
            Set.of(RuntimePhaseEnumVO.PREPARING_CONTEXT, RuntimePhaseEnumVO.BUILDING_STATE_VIEW),
            MainAgentPendingInputHandler.HANDLER_CODE,
            Set.of(RuntimePhaseEnumVO.BUILDING_STATE_VIEW, RuntimePhaseEnumVO.CALLING_MAIN_NODE),
            ToolApprovalPendingInputHandler.HANDLER_CODE,
            Set.of(RuntimePhaseEnumVO.PREPARING_TOOL, RuntimePhaseEnumVO.BUILDING_STATE_VIEW),
            SubAgentPendingInputHandler.HANDLER_CODE,
            Set.of(RuntimePhaseEnumVO.WAITING_CHILDREN),
            RagPendingInputHandler.HANDLER_CODE,
            Set.of(RuntimePhaseEnumVO.BUILDING_STATE_VIEW, RuntimePhaseEnumVO.PREPARING_CONTEXT),
            FinalRepairPendingInputHandler.HANDLER_CODE,
            Set.of(RuntimePhaseEnumVO.REPAIRING_FINAL));

    public boolean isAllowed(String handler, RuntimePhaseEnumVO phase) {
        return handler != null && phase != null && ALLOWED_PHASES.getOrDefault(handler, Set.of()).contains(phase);
    }
}
