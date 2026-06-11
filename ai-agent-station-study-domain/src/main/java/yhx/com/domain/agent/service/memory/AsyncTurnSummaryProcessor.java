package yhx.com.domain.agent.service.memory;

import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.service.memory.gc.worker.TurnSummaryGcWorker;
import yhx.com.domain.agent.service.node.turnsummary.TurnSummaryNodeService;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

public class AsyncTurnSummaryProcessor implements TurnCompletionPublisher {

    private final Executor executor;
    private final IMemoryTaskRepository taskRepository;
    private final TurnSummaryGcWorker worker;

    public AsyncTurnSummaryProcessor(Executor executor,
                                     ITurnRepository turnRepository,
                                     ITurnSummaryRepository summaryRepository,
                                     IMemoryTaskRepository taskRepository,
                                     IPayloadRepository payloadRepository,
                                     TurnSummaryNodeService nodeService) {
        this(executor, turnRepository, summaryRepository, taskRepository, payloadRepository, nodeService, null, null);
    }

    public AsyncTurnSummaryProcessor(Executor executor,
                                     ITurnRepository turnRepository,
                                     ITurnSummaryRepository summaryRepository,
                                     IMemoryTaskRepository taskRepository,
                                     IPayloadRepository payloadRepository,
                                     TurnSummaryNodeService nodeService,
                                     IVectorMemoryRepository vectorMemoryRepository,
                                     IVectorIndexRepository vectorIndexRepository) {
        this(executor, turnRepository, summaryRepository, taskRepository, payloadRepository, nodeService,
                new MemoryVectorIndexingService(vectorMemoryRepository, vectorIndexRepository, payloadRepository));
    }

    public AsyncTurnSummaryProcessor(Executor executor,
                                     ITurnRepository turnRepository,
                                     ITurnSummaryRepository summaryRepository,
                                     IMemoryTaskRepository taskRepository,
                                     IPayloadRepository payloadRepository,
                                     TurnSummaryNodeService nodeService,
                                     MemoryVectorIndexingService vectorIndexingService) {
        this.executor = executor;
        this.taskRepository = taskRepository;
        this.worker = new TurnSummaryGcWorker(turnRepository,
                summaryRepository,
                taskRepository,
                payloadRepository,
                nodeService,
                vectorIndexingService);
    }

    @Override
    public void onTurnCompleted(String turnId) {
        if (turnId == null || turnId.isBlank() || executor == null || taskRepository == null) {
            return;
        }
        String taskId = taskRepository.createTask(AgentMemoryTaskEntity.builder()
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId(turnId)
                .status("PENDING")
                .attemptCount(0)
                .createdAt(LocalDateTime.now())
                .build());
        executor.execute(() -> worker.handleTurn(taskId, turnId));
    }
}
