package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeRecoveryCounters;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;

public class RuntimeLoopPolicyTest {

    @Test
    public void max_loop_reached_returns_true() {
        RuntimeLoopPolicy policy = new RuntimeLoopPolicy(1, 1, 2, 1, 2, 2);
        RuntimeRecoveryCounters counters = RuntimeRecoveryCounters.initial();
        counters.incrementLoop();

        Assert.assertTrue(policy.maxLoopReached(counters));
    }

    @Test
    public void contract_repair_attempt_does_not_increment_loop_count() {
        RuntimeRecoveryCounters counters = RuntimeRecoveryCounters.initial();
        counters.incrementContractRepair();

        Assert.assertEquals(0, counters.loopCountValue());
        Assert.assertEquals(1, counters.contractRepairCountValue());
    }

    @Test
    public void waiting_user_does_not_increment_loop_count() {
        RuntimeRecoveryCounters counters = RuntimeRecoveryCounters.initial();

        Assert.assertEquals(0, counters.loopCountValue());
    }

    @Test
    public void resume_same_run_after_user_answer_is_policy_compatible() {
        RuntimeLoopPolicy policy = new RuntimeLoopPolicy();
        RuntimeRecoveryCounters counters = RuntimeRecoveryCounters.initial();

        Assert.assertFalse(policy.maxLoopReached(counters));
    }
}
