package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.runtime.RuntimeRecoveryCounters;

public class RuntimeLoopPolicy {

    private final int maxLoop;
    private final int maxContractRepair;
    private final int maxFinalRepair;
    private final int maxToolRetry;
    private final int maxRagRetry;
    private final int maxContextCompression;

    public RuntimeLoopPolicy() {
        this(6, 1, 2, 1, 2, 2);
    }

    public RuntimeLoopPolicy(int maxLoop, int maxContractRepair, int maxFinalRepair,
                             int maxToolRetry, int maxRagRetry, int maxContextCompression) {
        this.maxLoop = maxLoop;
        this.maxContractRepair = maxContractRepair;
        this.maxFinalRepair = maxFinalRepair;
        this.maxToolRetry = maxToolRetry;
        this.maxRagRetry = maxRagRetry;
        this.maxContextCompression = maxContextCompression;
    }

    public int maxLoop() {
        return maxLoop;
    }

    public boolean maxLoopReached(RuntimeRecoveryCounters counters) {
        return counters != null && counters.loopCountValue() >= maxLoop;
    }

    public boolean canRepairContract(RuntimeRecoveryCounters counters) {
        return counters == null || counters.contractRepairCountValue() < maxContractRepair;
    }

    public boolean canRepairFinal(RuntimeRecoveryCounters counters) {
        return counters == null || counters.finalRepairCountValue() < maxFinalRepair;
    }

    public boolean canRetryTool(RuntimeRecoveryCounters counters) {
        return counters == null || counters.toolRetryCountValue() < maxToolRetry;
    }

    public boolean canRetryRag(RuntimeRecoveryCounters counters) {
        return counters == null || counters.ragRetryCountValue() < maxRagRetry;
    }

    public boolean canCompressContext(RuntimeRecoveryCounters counters) {
        return counters == null || counters.contextCompressionCountValue() < maxContextCompression;
    }
}
