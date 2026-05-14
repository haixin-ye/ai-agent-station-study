package yhx.com.domain.agent.model.valobj.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptAssemblyCommand {

    private String runId;
    private String agentId;
    private String componentCode;
    private String contractVersion;
    private String promptVersion;
    private Object inputView;
    private Map<String, Object> metadata;
}
