package yhx.com.trigger.http;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.service.memory.gc.MemoryGcRetryService;
import yhx.com.domain.agent.service.memory.gc.MemoryGcTaskQueryService;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin("*")
@RequestMapping("/agent/memory-gc")
@Slf4j
public class AgentMemoryGcController {

    @Resource
    private MemoryGcRetryService memoryGcRetryService;

    @Resource
    private MemoryGcTaskQueryService memoryGcTaskQueryService;

    @GetMapping("/tasks")
    public Response<List<Map<String, Object>>> listTasks(@RequestParam(value = "status", required = false) String status,
                                                         @RequestParam(value = "limit", defaultValue = "50") int limit) {
        try {
            return AgentResponseSupport.success(memoryGcTaskQueryService.listTasks(status, limit).stream()
                    .map(this::toTaskSummary)
                    .toList());
        } catch (Exception e) {
            log.error("[AutoAgent][memory-gc-list-error] status={}, limit={}", status, limit, e);
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    @PostMapping("/retry-failed")
    public Response<Map<String, Object>> retryFailed(@RequestParam(value = "maxAttempts", defaultValue = "3") int maxAttempts,
                                                     @RequestParam(value = "limit", defaultValue = "20") int limit) {
        try {
            int dispatched = memoryGcRetryService.retryFailedTasks(maxAttempts, limit);
            return AgentResponseSupport.success(Map.of(
                    "maxAttempts", maxAttempts,
                    "limit", limit,
                    "dispatched", dispatched
            ));
        } catch (Exception e) {
            log.error("[AutoAgent][memory-gc-retry-error] maxAttempts={}, limit={}", maxAttempts, limit, e);
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    private Map<String, Object> toTaskSummary(AgentMemoryTaskEntity task) {
        return Map.ofEntries(
                Map.entry("taskId", valueOrEmpty(task.getTaskId())),
                Map.entry("taskType", valueOrEmpty(task.getTaskType())),
                Map.entry("sessionId", valueOrEmpty(task.getSessionId())),
                Map.entry("runId", valueOrEmpty(task.getRunId())),
                Map.entry("turnId", valueOrEmpty(task.getTurnId())),
                Map.entry("status", valueOrEmpty(task.getStatus())),
                Map.entry("attemptCount", task.getAttemptCount() == null ? 0 : task.getAttemptCount()),
                Map.entry("failureCode", valueOrEmpty(task.getFailureCode())),
                Map.entry("failureMessage", valueOrEmpty(task.getFailureMessage())),
                Map.entry("inputRef", valueOrEmpty(task.getInputRef())),
                Map.entry("outputRef", valueOrEmpty(task.getOutputRef())),
                Map.entry("createdAt", task.getCreatedAt() == null ? "" : task.getCreatedAt().toString()),
                Map.entry("updatedAt", task.getUpdatedAt() == null ? "" : task.getUpdatedAt().toString()),
                Map.entry("completedAt", task.getCompletedAt() == null ? "" : task.getCompletedAt().toString())
        );
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
