package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentRagHitPO {
    private Long id;
    private String ragHitId;
    private String ragQueryId;
    private String runId;
    private String chunkRef;
    private BigDecimal score;
    private String sourceTitle;
    private String sourceUri;
    private LocalDateTime createdAt;
}
