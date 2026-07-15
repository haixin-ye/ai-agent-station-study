package yhx.com.domain.agent.model.valobj.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInputConsumptionResultVO {

    private Boolean consumed;
    private String userAnswerRef;

    public boolean isConsumed() {
        return Boolean.TRUE.equals(consumed);
    }
}
