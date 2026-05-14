package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemoryEntity {

    private String memoryId;
    private String userId;
    private String sessionId;
    private String memoryType;
    private String summary;
    private String contentRef;
    private BigDecimal score;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
