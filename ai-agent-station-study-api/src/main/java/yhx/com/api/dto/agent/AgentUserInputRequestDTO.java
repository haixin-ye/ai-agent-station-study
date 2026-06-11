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
public class AgentUserInputRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pendingId;
    private String optionId;
    private String freeText;
    private Boolean cancelled;
    private Map<String, Object> metadata;
}

