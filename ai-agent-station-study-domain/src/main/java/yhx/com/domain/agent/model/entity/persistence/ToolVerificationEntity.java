package yhx.com.domain.agent.model.entity.persistence;

import yhx.com.domain.agent.model.valobj.enums.persistence.VerificationStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolVerificationEntity {

    private String verificationId;
    private String runId;
    private String toolCallId;
    private VerificationStatusEnumVO status;
    private String failureCode;
    private String detailRef;
    private LocalDateTime createdAt;
}
