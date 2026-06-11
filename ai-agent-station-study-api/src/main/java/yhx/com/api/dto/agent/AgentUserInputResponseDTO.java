package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentUserInputResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String runId;
    private String pendingId;
    private String status;
}

