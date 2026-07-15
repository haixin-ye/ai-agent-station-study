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
    private String content;
    private String contentRef;
    private String contentFormat;
    private String verificationStatus;
    private String failureCode;
    private LocalDateTime createdAt;
    private Double score;
}
