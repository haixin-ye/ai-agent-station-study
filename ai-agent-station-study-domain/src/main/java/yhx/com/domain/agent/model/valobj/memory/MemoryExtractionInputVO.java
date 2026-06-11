package yhx.com.domain.agent.model.valobj.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryExtractionInputVO {

    private String runId;
    private String sessionId;
    private String turnId;
    private String userInput;
    private String finalAnswer;
    private String turnSummary;
}
