package yhx.com.domain.agent.model.valobj.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerTypeEnumVO;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAnswerVO {

    private String pendingId;
    private String runId;
    private UserAnswerStatusEnumVO status;
    private UserAnswerTypeEnumVO answerType;
    private String selectedOptionId;
    private Object value;
    private String freeText;
    private String failureMessage;
    private Map<String, Object> metadata;
}
