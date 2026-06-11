package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeRecoveryCounters {

    private Integer loopCount;
    private Integer contractRepairCount;
    private Integer finalRepairCount;
    private Integer toolRetryCount;
    private Integer ragRetryCount;
    private Integer contextCompressionCount;

    public static RuntimeRecoveryCounters initial() {
        return RuntimeRecoveryCounters.builder()
                .loopCount(0)
                .contractRepairCount(0)
                .finalRepairCount(0)
                .toolRetryCount(0)
                .ragRetryCount(0)
                .contextCompressionCount(0)
                .build();
    }

    public int loopCountValue() {
        return value(loopCount);
    }

    public int contractRepairCountValue() {
        return value(contractRepairCount);
    }

    public int finalRepairCountValue() {
        return value(finalRepairCount);
    }

    public int toolRetryCountValue() {
        return value(toolRetryCount);
    }

    public int ragRetryCountValue() {
        return value(ragRetryCount);
    }

    public int contextCompressionCountValue() {
        return value(contextCompressionCount);
    }

    public void incrementLoop() {
        loopCount = loopCountValue() + 1;
    }

    public void incrementContractRepair() {
        contractRepairCount = contractRepairCountValue() + 1;
    }

    public void incrementFinalRepair() {
        finalRepairCount = finalRepairCountValue() + 1;
    }

    public void incrementToolRetry() {
        toolRetryCount = toolRetryCountValue() + 1;
    }

    public void incrementRagRetry() {
        ragRetryCount = ragRetryCountValue() + 1;
    }

    public void incrementContextCompression() {
        contextCompressionCount = contextCompressionCountValue() + 1;
    }

    private int value(Integer count) {
        return count == null ? 0 : count;
    }
}
