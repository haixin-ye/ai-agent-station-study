package yhx.com.domain.agent.model.valobj.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptEnvelope {

    private String componentCode;
    private String contractVersion;
    private List<PromptLayer> layers;
    private String assembledPrompt;
}
