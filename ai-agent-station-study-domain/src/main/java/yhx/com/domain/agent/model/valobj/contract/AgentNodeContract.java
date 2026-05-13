package yhx.com.domain.agent.model.valobj.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.AgentComponentCodeEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentNodeContract {

    private AgentComponentCodeEnumVO componentCode;
    private String name;
    private String version;
    private String description;
}
