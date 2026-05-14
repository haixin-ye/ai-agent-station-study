package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.runtime.FinalDeliveryStatusEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalDeliveryResultVO {

    private FinalDeliveryStatusEnumVO status;
    private String finalMessageId;
    private String finalAnswerRef;
    private String deliveredContent;
    private RuntimeSafeFailureVO safeFailure;
    private String message;
}
