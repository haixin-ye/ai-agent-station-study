package yhx.com.domain.agent.model.valobj.finalresponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalResponseVO {

    private String runId;
    private String sessionId;
    private String messageId;
    private String content;
    private String contentRef;
    private String format;
    private LocalDateTime createdAt;
}
