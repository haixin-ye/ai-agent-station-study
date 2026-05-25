package yhx.com.domain.agent.service.memory.gc;

import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.service.memory.TurnCompletionPublisher;

import java.time.LocalDateTime;

public class MemoryGcOrchestrator implements TurnCompletionPublisher {

    private final IMemoryTaskRepository taskRepository;
    private final MemoryGcTaskDispatcher taskDispatcher;

    public MemoryGcOrchestrator(IMemoryTaskRepository taskRepository, MemoryGcTaskDispatcher taskDispatcher) {
        this.taskRepository = taskRepository;
        this.taskDispatcher = taskDispatcher;
    }

    @Override
    public void onTurnCompleted(String turnId) {
        if (turnId == null || turnId.isBlank() || taskRepository == null) {
            return;
        }
        createAndDispatch(MemoryTaskTypeEnumVO.TURN_SUMMARY.name(), turnId);
    }

    private void createAndDispatch(String taskType, String turnId) {
        String taskId = taskRepository.createTask(AgentMemoryTaskEntity.builder()
                .taskType(taskType)
                .turnId(turnId)
                .status("PENDING")
                .attemptCount(0)
                .createdAt(LocalDateTime.now())
                .build());
        if (taskDispatcher != null) {
            taskDispatcher.dispatch(taskType, taskId);
        }
    }
}
