package yhx.com.domain.agent.service.memory.gc;

import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;

import java.util.List;

public class MemoryGcTaskQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final IMemoryTaskRepository taskRepository;

    public MemoryGcTaskQueryService(IMemoryTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<AgentMemoryTaskEntity> listTasks(String status, int limit) {
        if (taskRepository == null) {
            return List.of();
        }
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return taskRepository.listTasks(normalize(status), effectiveLimit);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
