package yhx.com.domain.agent.service.contract;

public interface ContractRepairPolicy {

    boolean canRepair(String failureCode, int attemptedRepairs);
}
