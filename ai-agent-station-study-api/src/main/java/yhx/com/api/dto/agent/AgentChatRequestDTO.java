package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String agentId;
    private String userId;
    private String content;
    private String inputType;
    private Map<String, Object> metadata;
}

