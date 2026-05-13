package yhx.com.domain.agent.service.contract;

public interface RecoveryPolicy {

    String recoveryActionFor(String failureCode);
}
