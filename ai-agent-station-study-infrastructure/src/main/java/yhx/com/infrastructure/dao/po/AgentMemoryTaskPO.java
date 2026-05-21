package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemoryTaskPO {

    private Long id;
    private String taskId;
    private String taskType;
    private String sessionId;
    private String runId;
    private String turnId;
    private String status;
    private Integer attemptCount;
    private String failureCode;
    private String failureMessage;
    private String inputRef;
    private String outputRef;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
