package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentLongTermMemoryPO {
    private Long id;
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
