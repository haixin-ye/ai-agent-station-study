package yhx.com.domain.agent.model.valobj.runtime;

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
public class LoopRuntimeOutcomeVO {
    private String status;
    private String code;
    private String summary;
    private String resultPayloadRef;
    private List<String> evidenceRefs;
    private List<String> artifactRefs;
    private Map<String, Object> details;
}
