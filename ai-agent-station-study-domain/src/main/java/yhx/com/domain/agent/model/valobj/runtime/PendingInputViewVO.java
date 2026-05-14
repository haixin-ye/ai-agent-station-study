package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInputViewVO {

    private String pendingId;
    private String runId;
    private String question;
    private String inputMode;
    private Boolean allowFreeText;
    private List<Map<String, Object>> options;
}
