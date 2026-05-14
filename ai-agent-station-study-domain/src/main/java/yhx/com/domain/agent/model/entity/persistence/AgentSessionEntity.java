package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionEntity {

    private String sessionId;
    private String userId;
    private String title;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
