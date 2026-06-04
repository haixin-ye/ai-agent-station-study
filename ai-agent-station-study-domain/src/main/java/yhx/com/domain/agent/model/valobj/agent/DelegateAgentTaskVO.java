package yhx.com.domain.agent.model.valobj.agent;

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
public class DelegateAgentTaskVO {

    private String taskId;
    private String name;
    private String objective;
    private String boundary;
    private String requiredOutput;
    private List<String> requestedCapabilities;
    private Map<String, Object> parentContext;
}
