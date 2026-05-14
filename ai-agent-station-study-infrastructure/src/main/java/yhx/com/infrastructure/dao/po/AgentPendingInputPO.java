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
public class AgentPendingInputPO {

    private Long id;
    private String pendingId;
    private String runId;
    private String sourceComponent;
    private String pendingType;
    private String inputMode;
    private String status;
    private String question;
    private String optionsRef;
    private String answerSchemaRef;
    private String continuationRef;
    private String userAnswerRef;
    private LocalDateTime createdAt;
    private LocalDateTime answeredAt;
    private LocalDateTime expiresAt;
}
