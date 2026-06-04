package yhx.com.domain.agent.model.valobj.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagAssetAnalysisInputVO {

    private String sourceName;
    private String sourceType;
    private String contentKind;
    private String content;
}
