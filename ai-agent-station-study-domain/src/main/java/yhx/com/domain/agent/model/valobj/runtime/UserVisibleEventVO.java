package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVisibleEventVO {

    private String runId;
    private String eventType;
    private String title;
    private String summary;
    private String pendingInputId;
    private String finalMessageId;
}
