package yhx.com.domain.agent.model.entity.persistence;

import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPayloadEntity {

    private String payloadId;
    private PayloadTypeEnumVO payloadType;
    private String content;
    private String contentSha256;
    private String preview;
    private LocalDateTime createdAt;
}
