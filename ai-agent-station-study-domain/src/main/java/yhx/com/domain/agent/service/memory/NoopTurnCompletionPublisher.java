package yhx.com.domain.agent.service.memory;

public class NoopTurnCompletionPublisher implements TurnCompletionPublisher {

    @Override
    public void onTurnCompleted(String turnId) {
        // no-op
    }
}
