package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceCandidateVO {

    private String evidenceId;
    private String evidenceType;
    private String sourceRef;
    private String summary;
    private LocalDateTime createdAt;
    private Double score;
}
