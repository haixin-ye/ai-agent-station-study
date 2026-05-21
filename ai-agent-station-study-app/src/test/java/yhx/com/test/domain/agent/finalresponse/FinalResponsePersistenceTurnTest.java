package yhx.com.test.domain.agent.finalresponse;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.MessageRoleEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;
import yhx.com.domain.agent.service.finalresponse.FinalResponseBuilder;
import yhx.com.domain.agent.service.finalresponse.FinalResponsePersistenceService;
import yhx.com.domain.agent.service.memory.TurnCompletionPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FinalResponsePersistenceTurnTest {

    @Test
    public void persist_delivered_saves_completed_turn_and_publishes_completion() {
        FinalResponseTestSupport.Repository repository = new FinalResponseTestSupport.Repository();
        FakeTurnRepository turnRepository = new FakeTurnRepository();
        RecordingPublisher publisher = new RecordingPublisher();
        repository.savePayload(AgentPayloadEntity.builder()
                .payloadId("payload-user-1")
                .content("user asks")
                .preview("user asks")
                .build());
        repository.appendMessage(AgentMessageEntity.builder()
                .messageId("msg-user-1")
                .sessionId("sess-1")
                .runId("run-1")
                .role(MessageRoleEnumVO.USER)
                .contentRef("payload-user-1")
                .visibleToUser(true)
                .build());
        FinalResponsePersistenceService service = new FinalResponsePersistenceService(repository,
                repository,
                repository,
                repository,
                turnRepository,
                publisher);

        service.persistDelivered(command(), new FinalResponseBuilder().build(command(),
                FinalAnswerCandidateVO.builder().content("final answer").format("PLAIN_TEXT").build(),
                null));

        Assert.assertEquals(1, turnRepository.turns.size());
        AgentTurnEntity turn = turnRepository.turns.get(0);
        Assert.assertEquals("msg-user-1", turn.getUserMessageId());
        Assert.assertNotNull(turn.getAssistantMessageId());
        Assert.assertEquals("payload-user-1", turn.getUserPayloadRef());
        Assert.assertNotNull(turn.getAssistantPayloadRef());
        Assert.assertEquals("COMPLETED", turn.getStatus());
        Assert.assertEquals(turn.getTurnId(), publisher.completedTurnIds.get(0));
    }

    private FinalDeliveryCommandVO command() {
        return FinalDeliveryCommandVO.builder()
                .runId("run-1")
                .sessionId("sess-1")
                .userId("user-1")
                .agentId("agent-1")
                .userMessageId("msg-user-1")
                .userInput("user asks")
                .build();
    }

    private static class FakeTurnRepository implements ITurnRepository {
        private final List<AgentTurnEntity> turns = new ArrayList<>();

        @Override
        public String saveCompletedTurn(AgentTurnEntity turn) {
            turn.setTurnId("turn-1");
            turns.add(turn);
            return turn.getTurnId();
        }

        @Override
        public Optional<AgentTurnEntity> findByTurnId(String turnId) {
            return turns.stream().filter(turn -> turnId.equals(turn.getTurnId())).findFirst();
        }

        @Override
        public Optional<AgentTurnEntity> findByRunId(String runId) {
            return turns.stream().filter(turn -> runId.equals(turn.getRunId())).findFirst();
        }

        @Override
        public List<AgentTurnEntity> listRecentCompletedTurns(String sessionId, int limit) {
            return turns.stream().limit(limit).toList();
        }

        @Override
        public List<AgentTurnEntity> listCompletedTurnsBefore(String sessionId, Long beforeTurnNo, int limit) {
            return List.of();
        }
    }

    private static class RecordingPublisher implements TurnCompletionPublisher {
        private final List<String> completedTurnIds = new ArrayList<>();

        @Override
        public void onTurnCompleted(String turnId) {
            completedTurnIds.add(turnId);
        }
    }
}
