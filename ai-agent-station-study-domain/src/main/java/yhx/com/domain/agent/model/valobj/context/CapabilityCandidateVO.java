package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityCandidateVO {

    private String capabilityCode;
    private String capabilityType;
    private String summary;
    private Boolean enabled;
}
