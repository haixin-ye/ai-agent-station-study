package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.util.Map;

public class ArtifactActionHandlerTest {

    @Test
    public void create_artifact_persists_payload_and_metadata() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        MainActionDispatcher dispatcher = dispatcher(repository, new ActionHandlerTestSupport.FakeFinalDeliveryPort());

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), createArtifact(false));

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(1, repository.artifacts.size());
        Assert.assertFalse(repository.payloads.isEmpty());
    }

    @Test
    public void create_artifact_with_final_candidate_uses_final_delivery() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        MainActionDispatcher dispatcher = dispatcher(repository, finalPort);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), createArtifact(true));

        Assert.assertEquals(MainActionHandlerStatusEnumVO.COMPLETED, result.getStatus());
        Assert.assertEquals(1, finalPort.calls.size());
    }

    @Test
    public void update_artifact_validates_target() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        MainActionDispatcher dispatcher = dispatcher(repository, new ActionHandlerTestSupport.FakeFinalDeliveryPort());

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), MainAgentActionVO.builder()
                .action("UPDATE_ARTIFACT")
                .stateDelta(Map.of("artifactPatch", Map.of("updateMode", "REPLACE_FULL", "content", "new")))
                .build());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.FAILED, result.getStatus());
    }

    @Test
    public void artifact_event_does_not_include_full_body() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        MainActionDispatcher dispatcher = dispatcher(repository, new ActionHandlerTestSupport.FakeFinalDeliveryPort());

        dispatcher.dispatch(ActionHandlerTestSupport.context(), createArtifact(false));

        String eventPayload = repository.payloads.get(repository.events.get(0).getPayloadRef()).getContent();
        Assert.assertFalse(eventPayload.contains("full artifact body"));
    }

    private MainActionDispatcher dispatcher(ActionHandlerTestSupport.FullRepository repository, ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort) {
        return ActionHandlerTestSupport.dispatcher(repository, finalPort,
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort(),
                new ActionHandlerTestSupport.FakePlanStatePort());
    }

    private MainAgentActionVO createArtifact(boolean withFinal) {
        Map<String, Object> draft = Map.of(
                "artifactType", "ARTICLE",
                "title", "RAG",
                "content", "full artifact body"
        );
        if (withFinal) {
            return MainAgentActionVO.builder()
                    .action("CREATE_ARTIFACT")
                    .stateDelta(Map.of("artifactDraft", draft, "finalAnswerCandidate", Map.of("content", "created")))
                    .build();
        }
        return MainAgentActionVO.builder()
                .action("CREATE_ARTIFACT")
                .stateDelta(Map.of("artifactDraft", draft))
                .build();
    }
}
