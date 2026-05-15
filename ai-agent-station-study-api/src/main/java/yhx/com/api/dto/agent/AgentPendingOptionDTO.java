package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPendingOptionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String optionId;
    private String label;
    private String description;
    private Map<String, Object> value;
}
