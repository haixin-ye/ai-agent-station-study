package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingActionViewVO {

    private String pendingInputId;
    private String sourceComponent;
    private String question;
    private String status;
}
