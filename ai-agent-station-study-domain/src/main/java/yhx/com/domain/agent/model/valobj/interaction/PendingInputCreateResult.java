package yhx.com.domain.agent.model.valobj.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInputCreateResult {

    private String pendingInputId;
    private String runId;
    private Boolean created;
    private String failureMessage;
}
