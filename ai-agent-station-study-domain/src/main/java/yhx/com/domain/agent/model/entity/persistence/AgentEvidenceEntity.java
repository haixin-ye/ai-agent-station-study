package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvidenceEntity {

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
    private Boolean usedByFinal;
    private LocalDateTime createdAt;
}
