package yhx.com.domain.agent.service.memory.gc.worker;

public interface MemoryGcTaskWorker {

    String taskType();

    void handle(String taskId);
}
