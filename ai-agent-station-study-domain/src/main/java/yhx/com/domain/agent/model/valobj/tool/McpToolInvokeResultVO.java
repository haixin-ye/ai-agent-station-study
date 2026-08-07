package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolInvokeResultVO {

    private boolean called;
    private boolean success;
    private Map<String, Object> receipt;
    private String resultContent;
    private String errorCode;
    private String errorMessage;
    private Long latencyMs;
}
