package yhx.com.domain.agent.model.valobj.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInputResolveCommand {

    private String runId;
    private String pendingId;
    private String selectedOptionId;
    private String freeText;
    private Boolean cancelled;
    private Map<String, Object> requestMetadata;
}
