package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDebugPayloadDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String payloadId;
    private String payloadType;
    private String preview;
    private Boolean previewTruncated;
    private Boolean rawContentIncluded;
    private String rawContent;
}

