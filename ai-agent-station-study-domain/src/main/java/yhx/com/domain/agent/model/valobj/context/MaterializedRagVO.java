package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterializedRagVO {

    private String candidateId;
    private String sourceType;
    private String documentId;
    private String chunkId;
    private String title;
    private String summary;
    private String content;
    private String boundedSnippet;
    private String injectMode;
    private RagCodeCandidateMetaVO codeMeta;
    private ContextLevelEnumVO contextLevel;
}
