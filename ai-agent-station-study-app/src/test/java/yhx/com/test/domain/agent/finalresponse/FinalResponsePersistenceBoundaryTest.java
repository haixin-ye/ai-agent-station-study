package yhx.com.test.domain.agent.finalresponse;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;
import yhx.com.domain.agent.service.finalresponse.FinalResponsePersistenceService;

public class FinalResponsePersistenceBoundaryTest {

    @Test
    public void raw_model_output_is_not_saved_as_assistant_message() {
        FinalResponseTestSupport.Repository repository = repository();
        FinalResponsePersistenceService service = service(repository);
        service.saveCandidateDebugPayload(command("raw model output"));

        Assert.assertEquals(0, repository.messages.size());
    }

    @Test
    public void verifier_result_is_not_saved_as_assistant_message() {
        FinalResponseTestSupport.Repository repository = repository();
        service(repository).saveGuardDetail("run-001", FinalResponseGuardResultVO.builder().status("FAILED").detail("verifier result").build());

        Assert.assertEquals(0, repository.messages.size());
    }

    @Test
    public void tool_receipt_is_not_saved_as_assistant_message() {
        FinalResponseTestSupport.Repository repository = repository();
        service(repository).saveGuardDetail("run-001", FinalResponseGuardResultVO.builder().status("FAILED").detail("tool receipt").build());

        Assert.assertEquals(0, repository.messages.size());
    }

    @Test
    public void guard_detail_is_saved_as_developer_trace() {
        FinalResponseTestSupport.Repository repository = repository();
        service(repository).saveGuardDetail("run-001", FinalResponseGuardResultVO.builder().status("FAILED").detail("guard detail").build());

        Assert.assertEquals(1, repository.traces.size());
    }

    @Test
    public void guard_pass_is_the_only_path_that_persists_assistant_message() {
        FinalResponseTestSupport.Repository repository = repository();
        service(repository).persistDelivered(command("answer"), FinalResponseVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .content("answer")
                .format("PLAIN_TEXT")
                .build());

        Assert.assertEquals(1, repository.messages.size());
    }

    private FinalResponsePersistenceService service(FinalResponseTestSupport.Repository repository) {
        return new FinalResponsePersistenceService(repository, repository, repository, repository);
    }

    private FinalDeliveryCommandVO command(String content) {
        return FinalDeliveryCommandVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .sourceAction(MainAgentActionTypeEnumVO.FINAL)
                .finalAnswerCandidate(yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO.builder().content(content).build())
                .build();
    }

    private FinalResponseTestSupport.Repository repository() {
        FinalResponseTestSupport.Repository repository = new FinalResponseTestSupport.Repository();
        repository.createRun(AgentRunEntity.builder().runId("run-001").sessionId("sess-001").build());
        return repository;
    }
}
