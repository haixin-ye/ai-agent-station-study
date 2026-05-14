package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRuntimeCommandVO {

    private String runId;
    private String sessionId;
    private String userId;
    private Integer loopIndex;
    private String query;
    private String knowledgeName;
    private Map<String, Object> options;
}
