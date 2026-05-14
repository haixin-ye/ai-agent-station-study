package yhx.com.domain.agent.model.valobj.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRetrievalCommandVO {

    private String runId;
    private String sessionId;
    private Integer loopIndex;
    private String query;
    private String knowledgeName;
    private Integer topK;
    private Integer maxHitChars;
    private Map<String, Object> runtimeFilters;
}
