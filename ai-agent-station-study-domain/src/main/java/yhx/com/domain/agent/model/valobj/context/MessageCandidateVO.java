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
public class MessageCandidateVO {

    private String messageId;
    private String turnId;
    private String role;
    private String contentRef;
    private String summary;
    private Long seq;
    private LocalDateTime createdAt;
}
