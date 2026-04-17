package cn.bugstack.ai.domain.agent.model.valobj.enums;

/**
 * Node3 emits a directive and Node1 consumes it on the next loop.
 */
public enum NextRoundDirectiveTypeEnumVO {
    REPLAN_SAME_STEP,
    ADVANCE_NEXT_STEP,
    FINISH_SUCCESS,
    FINISH_PARTIAL,
    FINISH_FAILED
}
