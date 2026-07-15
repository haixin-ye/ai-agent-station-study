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
public class AgentEvidencePO {

    private Long id;
    private String evidenceId;
    private String runId;
    private String evidenceType;
    private String sourceRef;
    private String summary;
    private String contentRef;
    private String contentFormat;
    private String verificationStatus;
    private String failureCode;
    private BigDecimal confidence;
    private Integer usedByFinal;
    private LocalDateTime createdAt;
}
