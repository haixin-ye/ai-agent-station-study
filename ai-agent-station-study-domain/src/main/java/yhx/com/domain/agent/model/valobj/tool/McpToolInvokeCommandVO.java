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
public class McpToolInvokeCommandVO {

    private String mcpServerCode;
    private String toolName;
    private Map<String, Object> arguments;
    private Long timeoutMs;
}
