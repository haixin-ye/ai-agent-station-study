package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDispatchResultVO {

    private String parentRunId;
    private String waitMode;
    private List<String> childRunIds;
    private boolean parentReady;
}
