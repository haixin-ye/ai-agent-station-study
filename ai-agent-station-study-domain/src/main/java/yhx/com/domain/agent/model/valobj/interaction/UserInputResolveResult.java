package yhx.com.domain.agent.model.valobj.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputResolutionStatusEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInputResolveResult {

    private String pendingInputId;
    private UserAnswerVO userAnswer;
    private RuntimeStepResult continuationResult;
    private Boolean resolved;
    private PendingInputResolutionStatusEnumVO resolutionStatus;
    private String failureMessage;
}
