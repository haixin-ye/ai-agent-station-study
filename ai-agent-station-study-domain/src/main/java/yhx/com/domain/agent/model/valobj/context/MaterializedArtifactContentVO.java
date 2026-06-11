package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterializedArtifactContentVO {

    private String artifactId;
    private ContextLevelEnumVO contextLevel;
    private String title;
    private String summary;
    private String contentRef;
    private String content;
    private List<ArtifactChunkVO> chunks;
    private Integer tokenCount;
    private Boolean truncated;
}
