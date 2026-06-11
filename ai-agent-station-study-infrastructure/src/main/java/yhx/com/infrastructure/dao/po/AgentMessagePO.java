package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentMessagePO {

    private Long id;
    private String messageId;
    private String sessionId;
    private String runId;
    private String role;
    private String contentRef;
    private String metadataRef;
    private Integer visibleToUser;
    private Long seq;
    private LocalDateTime createdAt;
}
