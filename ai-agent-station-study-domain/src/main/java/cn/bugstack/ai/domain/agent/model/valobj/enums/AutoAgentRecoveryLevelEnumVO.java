package cn.bugstack.ai.domain.agent.model.valobj.enums;

/**
 * Unified recovery levels used by prompt/harness contracts.
 */
public enum AutoAgentRecoveryLevelEnumVO {

    FORMAT_NOISE,
    STRUCTURE_RECOVERABLE,
    SEMANTIC_UNCERTAIN,
    EXECUTION_UNVERIFIED,
    CONTRACT_VIOLATION
}
