package yhx.com.domain.agent.model.valobj.invocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeClientRequest {

    private String runId;
    private String componentCode;
    private String modelCode;
    private String prompt;
    private Double temperature;
    private Integer maxOutputTokens;
    private Map<String, Object> metadata;
}
