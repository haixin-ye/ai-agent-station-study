package yhx.com.domain.agent.model.valobj.agent;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Map;
import java.util.Set;
import java.util.List;
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericSubAgentRunCommandVO {

    private ParentChildRunRelationVO relation;
    private DelegateAgentTaskVO task;
    private AgentProfileVO profile;
    private Set<String> effectiveCapabilityCodes;
    private List<CapabilityCandidateVO> availableMcpTools;
    private Map<String, Object> initialContext;
    private String sessionId;
    private String userId;
    @JSONField(serialize = false, deserialize = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private RuntimeExecutionContext parentRuntimeContext;
}
