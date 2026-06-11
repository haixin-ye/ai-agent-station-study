package yhx.com.domain.agent.model.valobj.enums.contract;

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

