package yhx.com.domain.agent.model.valobj.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagAssetAnalysisResultVO {

    private String title;
    private String summary;
    private String retrievalText;
    private String language;
    private List<String> keySymbols;
}
