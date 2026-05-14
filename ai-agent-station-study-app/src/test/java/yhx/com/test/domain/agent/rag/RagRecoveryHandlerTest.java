package yhx.com.test.domain.agent.rag;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeRecoveryCounters;
import yhx.com.domain.agent.service.rag.runtime.RagRecoveryHandler;

public class RagRecoveryHandlerTest {

    @Test
    public void rag_ungrounded_routes_to_final_repair() {
        MainActionHandlerResult result = new RagRecoveryHandler().handleVerificationFailure(failed("RAG_UNGROUNDED"), context(0, 0));

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.REPAIRING_FINAL, result.getNextPhase());
    }

    @Test
    public void rag_no_hit_uses_retry_when_budget_remains() {
        RuntimeExecutionContext context = context(0, 0);

        MainActionHandlerResult result = new RagRecoveryHandler().handleVerificationFailure(failed("RAG_NO_HIT"), context);

        Assert.assertEquals(RuntimePhaseEnumVO.PREPARING_CONTEXT, result.getNextPhase());
        Assert.assertEquals(1, context.getRecoveryCounters().ragRetryCountValue());
    }

    @Test
    public void rag_no_hit_routes_to_final_repair_when_retry_exhausted() {
        MainActionHandlerResult result = new RagRecoveryHandler().handleVerificationFailure(failed("RAG_NO_HIT"), context(1, 0));

        Assert.assertEquals(RuntimePhaseEnumVO.REPAIRING_FINAL, result.getNextPhase());
    }

    @Test
    public void final_invalid_citation_routes_to_final_repair() {
        MainActionHandlerResult result = new RagRecoveryHandler().handleVerificationFailure(failed("FINAL_INVALID_CITATION"), context(0, 0));

        Assert.assertEquals(RuntimePhaseEnumVO.REPAIRING_FINAL, result.getNextPhase());
    }

    @Test
    public void rag_contradiction_without_repair_budget_fails_safely() {
        MainActionHandlerResult result = new RagRecoveryHandler().handleVerificationFailure(failed("RAG_CONTRADICTION"), context(0, 1));

        Assert.assertEquals(MainActionHandlerStatusEnumVO.FAILED, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.FAILED, result.getNextPhase());
    }

    private VerificationResultVO failed(String code) {
        return VerificationResultVO.builder().status("FAILED").failureCode(code).detail(code).build();
    }

    private RuntimeExecutionContext context(int ragRetry, int finalRepair) {
        return RuntimeExecutionContext.builder()
                .recoveryCounters(RuntimeRecoveryCounters.builder()
                        .ragRetryCount(ragRetry)
                        .finalRepairCount(finalRepair)
                        .build())
                .build();
    }
}
