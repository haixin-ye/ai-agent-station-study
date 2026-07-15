package yhx.com.infrastructure.adapter.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import yhx.com.domain.agent.adapter.transaction.IInteractionTransactionExecutor;

import java.util.function.Supplier;

@Component
public class InteractionTransactionExecutor implements IInteractionTransactionExecutor {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public <T> T execute(Supplier<T> operation) {
        return operation.get();
    }
}
