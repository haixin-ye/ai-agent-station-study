package yhx.com.domain.agent.model.valobj.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptAssemblyResult {

    private PromptEnvelope envelope;

    public String assembledPrompt() {
        return envelope == null ? null : envelope.getAssembledPrompt();
    }
}
