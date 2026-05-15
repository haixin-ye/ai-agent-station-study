package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String messageId;
    private String sessionId;
    private String runId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}

