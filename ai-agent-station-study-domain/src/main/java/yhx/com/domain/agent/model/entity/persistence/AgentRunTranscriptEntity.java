package yhx.com.domain.agent.model.entity.persistence;

import yhx.com.domain.agent.model.valobj.enums.persistence.TranscriptBlockTypeEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunTranscriptEntity {

    private String blockId;
    private String runId;
    private Long seq;
    private TranscriptBlockTypeEnumVO blockType;
    private String payloadRef;
    private Boolean compactable;
    private LocalDateTime createdAt;
}
