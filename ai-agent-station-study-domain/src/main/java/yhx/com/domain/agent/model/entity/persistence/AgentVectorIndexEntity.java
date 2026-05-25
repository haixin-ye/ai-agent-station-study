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
public class AgentVectorIndexEntity {

    private String indexId;
    private String collectionType;
    private String sourceType;
    private String sourceId;
    private String vectorId;
    private String userId;
    private String sessionId;
    private String contentHash;
    private String status;
    private String failureMessage;
    private LocalDateTime indexedAt;
    private LocalDateTime disabledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
