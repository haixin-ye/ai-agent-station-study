package yhx.com.domain.agent.adapter.transaction;

import java.util.function.Supplier;

public interface IInteractionTransactionExecutor {

    <T> T execute(Supplier<T> operation);
}
