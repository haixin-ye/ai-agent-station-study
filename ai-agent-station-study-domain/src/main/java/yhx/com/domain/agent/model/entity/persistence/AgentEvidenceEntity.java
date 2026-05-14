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
public class AgentEvidenceEntity {

    private String evidenceId;
    private String runId;
    private String evidenceType;
    private String sourceRef;
    private String summary;
    private LocalDateTime createdAt;
}
