package yhx.com.domain.agent.service.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentNodeContract {

    private AgentComponentCode componentCode;
    private String name;
    private String version;
    private String description;
}
