package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagCodeCandidateMetaVO {

    private String repositoryUrl;
    private String repositoryName;
    private String branchName;
    private String relativePath;
    private String language;
    private String symbolName;
    private String symbolKind;
    private Integer startLine;
    private Integer endLine;
}
