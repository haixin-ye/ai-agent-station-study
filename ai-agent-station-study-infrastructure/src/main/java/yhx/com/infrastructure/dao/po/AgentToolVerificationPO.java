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
public class AgentToolVerificationPO {
    private Long id;
    private String verificationId;
    private String runId;
    private String toolCallId;
    private String status;
    private String failureCode;
    private String detailRef;
    private LocalDateTime createdAt;
}
