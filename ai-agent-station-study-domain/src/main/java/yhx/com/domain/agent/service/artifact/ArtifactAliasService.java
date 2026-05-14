package yhx.com.domain.agent.service.artifact;

import java.util.ArrayList;
import java.util.List;

public class ArtifactAliasService {

    public List<String> aliasesFor(String title, String artifactType) {
        List<String> aliases = new ArrayList<>();
        if (title != null && !title.isBlank()) {
            aliases.add(title);
        }
        if (artifactType != null && !artifactType.isBlank()) {
            aliases.add(artifactType);
        }
        return aliases;
    }
}
