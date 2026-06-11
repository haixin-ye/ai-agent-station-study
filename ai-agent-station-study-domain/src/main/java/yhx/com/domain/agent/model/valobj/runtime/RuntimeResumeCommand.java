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
public class RuntimeResumeCommand {

    private String runId;
    private String pendingId;
    private String selectedOptionId;
    private String freeText;
    private Boolean cancelled;
    private Map<String, Object> requestMetadata;
}
