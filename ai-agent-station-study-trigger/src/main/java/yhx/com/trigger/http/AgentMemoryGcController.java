package yhx.com.trigger.http;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.service.memory.gc.MemoryGcRetryService;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.util.Map;

@RestController
@CrossOrigin("*")
@RequestMapping("/agent/memory-gc")
@Slf4j
public class AgentMemoryGcController {

    @Resource
    private MemoryGcRetryService memoryGcRetryService;

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
}
