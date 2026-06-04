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
public class AskUserSnapshotVO {

    private String pendingInputId;
    private String question;
    private String inputMode;
    private Boolean answered;
    private String answerType;
    private Map<String, Object> value;
    private String freeText;
}
