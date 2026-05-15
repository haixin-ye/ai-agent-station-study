package yhx.com.test.domain.agent.finalresponse;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.FinalDeliveryStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerificationRouteCommandVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerificationRouteResultVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryResultVO;
import yhx.com.domain.agent.service.finalresponse.FinalDeliveryService;
import yhx.com.domain.agent.service.finalresponse.FinalRepairService;
import yhx.com.domain.agent.service.finalresponse.FinalResponseBuilder;
import yhx.com.domain.agent.service.finalresponse.FinalResponseGuard;
import yhx.com.domain.agent.service.finalresponse.FinalResponseGuardInputBuilder;
import yhx.com.domain.agent.service.finalresponse.FinalResponsePersistenceService;
import yhx.com.domain.agent.service.finalresponse.FixedSafeFallbackFactory;
import yhx.com.domain.agent.service.rag.runtime.RagVerificationRouter;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;

public class FinalDeliveryServiceTest {

    @Test
    public void final_delivery_appends_assistant_message_only_after_guard_pass() {
        FinalResponseTestSupport.Repository repository = repository(false);
        FinalDeliveryService service = service(repository, null, null);

        FinalDeliveryResultVO result = service.deliver(command("clean answer"));

        Assert.assertEquals(FinalDeliveryStatusEnumVO.DELIVERED, result.getStatus());
        Assert.assertEquals(1, repository.messages.size());
        Assert.assertEquals(RunStatusEnumVO.COMPLETED, repository.runs.get("run-001").getStatus());
    }

    @Test
    public void rag_verification_runs_before_guard_when_rag_was_used() {
        FinalResponseTestSupport.Repository repository = repository(true);
        FakeRagVerificationRouter router = new FakeRagVerificationRouter(false);
        FinalDeliveryService service = service(repository, router, null);

        service.deliver(command("clean answer"));

        Assert.assertEquals(1, router.calls);
    }

    @Test
    public void rag_verification_skips_when_rag_was_not_used() {
        FinalResponseTestSupport.Repository repository = repository(false);
        FakeRagVerificationRouter router = new FakeRagVerificationRouter(false);
        FinalDeliveryService service = service(repository, router, null);

        service.deliver(command("clean answer"));

        Assert.assertEquals(0, router.calls);
    }

    @Test
    public void guard_failure_requests_repair_when_budget_remains() {
        FinalResponseTestSupport.Repository repository = repository(false);
        FinalDeliveryService service = service(repository, null, new StubFinalRepairService(FinalAnswerCandidateVO.builder().content("repaired answer").build()));

        FinalDeliveryResultVO result = service.deliver(command("Runtime trace leaked"));

        Assert.assertEquals(FinalDeliveryStatusEnumVO.DELIVERED, result.getStatus());
        Assert.assertTrue(result.isRepairRequested());
        Assert.assertEquals("repaired answer", result.getDeliveredContent());
    }

    @Test
    public void guard_failure_uses_fixed_fallback_when_repair_exhausted() {
        FinalResponseTestSupport.Repository repository = repository(false);
        FinalDeliveryService service = service(repository, null, new StubFinalRepairService(FinalAnswerCandidateVO.builder().content("should not be used").build()));
        FinalDeliveryCommandVO command = command("Runtime trace leaked");
        command.setFinalRepairCount(2);
        command.setMaxFinalRepairAttempts(2);

        FinalDeliveryResultVO result = service.deliver(command);

        Assert.assertEquals(FinalDeliveryStatusEnumVO.DELIVERED, result.getStatus());
        Assert.assertEquals(FixedSafeFallbackFactory.FALLBACK_TEXT, result.getDeliveredContent());
    }

    @Test
    public void fail_action_user_message_goes_through_guard() {
        FinalResponseTestSupport.Repository repository = repository(false);
        FinalDeliveryService service = service(repository, null, null);
        FinalDeliveryCommandVO command = command("will be replaced");
        command.setSourceAction(MainAgentActionTypeEnumVO.FAIL);

        FinalDeliveryResultVO result = service.deliver(command);

        Assert.assertEquals(FinalDeliveryStatusEnumVO.DELIVERED, result.getStatus());
        Assert.assertFalse(result.getDeliveredContent().contains("Runtime"));
    }

    private FinalDeliveryService service(FinalResponseTestSupport.Repository repository,
                                         RagVerificationRouter router,
                                         FinalRepairService repairService) {
        return new FinalDeliveryService(repository, router, new FinalResponseGuardInputBuilder(), new FinalResponseGuard(),
                new FinalResponseBuilder(), repairService, new FixedSafeFallbackFactory(),
                new FinalResponsePersistenceService(repository, repository, repository, repository),
                new RuntimeFailureFactory(), null);
    }

    private FinalDeliveryCommandVO command(String content) {
        return FinalDeliveryCommandVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .agentId("agent-001")
                .userInput("user asks")
                .sourceAction(MainAgentActionTypeEnumVO.FINAL)
                .finalAnswerCandidate(FinalAnswerCandidateVO.builder().content(content).format("PLAIN_TEXT").build())
                .maxOutputChars(1000)
                .build();
    }

    private FinalResponseTestSupport.Repository repository(boolean ragWasUsed) {
        FinalResponseTestSupport.Repository repository = new FinalResponseTestSupport.Repository();
        repository.createRun(AgentRunEntity.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .status(RunStatusEnumVO.RUNNING)
                .phase(RuntimePhaseEnumVO.HANDLING_ACTION)
                .ragWasUsed(ragWasUsed)
                .build());
        return repository;
    }

    private static class FakeRagVerificationRouter extends RagVerificationRouter {
        private final boolean fail;
        private int calls;

        FakeRagVerificationRouter(boolean fail) {
            super(null, null, null, null);
            this.fail = fail;
        }

        @Override
        public RagVerificationRouteResultVO verifyIfRequired(RagVerificationRouteCommandVO command) {
            calls++;
            return RagVerificationRouteResultVO.builder()
                    .verificationRequired(true)
                    .verificationResult(VerificationResultVO.builder().status(fail ? "FAILED" : "PASSED").detail("rag checked").build())
                    .failureCode(fail ? yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO.RAG_VERIFICATION_FAILED : null)
                    .message("rag checked")
                    .build();
        }
    }

    private static class StubFinalRepairService extends FinalRepairService {
        private final FinalAnswerCandidateVO repaired;

        StubFinalRepairService(FinalAnswerCandidateVO repaired) {
            super(null);
            this.repaired = repaired;
        }

        @Override
        public FinalAnswerCandidateVO repair(yhx.com.domain.agent.model.valobj.finalresponse.FinalRepairPromptContextVO context) {
            return repaired;
        }
    }
}
