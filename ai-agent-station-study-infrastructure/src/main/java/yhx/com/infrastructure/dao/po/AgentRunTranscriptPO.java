package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentRunTranscriptPO {
    private Long id;
    private String blockId;
    private String runId;
    private Long seq;
    private String blockType;
    private String payloadRef;
    private Integer compactable;
    private LocalDateTime createdAt;
}
