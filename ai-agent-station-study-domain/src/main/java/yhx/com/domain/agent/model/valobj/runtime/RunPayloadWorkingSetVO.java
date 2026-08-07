package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunPayloadWorkingSetVO {
    private Map<String, Object> payloadManifest;
    private Map<String, Object> activePayloads;
}
