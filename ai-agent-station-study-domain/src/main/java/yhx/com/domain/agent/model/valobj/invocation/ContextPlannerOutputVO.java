package yhx.com.domain.agent.model.valobj.invocation;

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
public class ContextPlannerOutputVO {

    private String status;
    private List<Map<String, Object>> selectedContext;
    private Map<String, Object> clarificationRequest;
    private String reason;
}
