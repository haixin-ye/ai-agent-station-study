package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserClarificationVO {

    private String sourceComponent;
    private String pendingId;
    private String question;
    private String answerType;
    private String selectedOptionId;
    private Object value;
    private String freeText;
    private Map<String, Object> metadata;
}
