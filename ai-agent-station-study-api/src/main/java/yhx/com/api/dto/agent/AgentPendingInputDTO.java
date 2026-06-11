package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPendingInputDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pendingId;
    private String runId;
    private String pendingType;
    private String inputMode;
    private Boolean allowFreeText;
    private String question;
    private List<AgentPendingOptionDTO> options;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}

