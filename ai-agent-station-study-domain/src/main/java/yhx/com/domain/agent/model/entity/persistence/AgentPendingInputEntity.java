package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPendingInputEntity {

    private String pendingId;
    private String runId;
    private String sourceComponent;
    private String pendingType;
    private String inputMode;
    private String status;
    private String question;
    private String optionsRef;
    private String continuationRef;
    private String userAnswerRef;
    private LocalDateTime createdAt;
    private LocalDateTime answeredAt;
}
