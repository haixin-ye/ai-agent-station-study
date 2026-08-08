package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallCorpusImportItemVO {
    private String externalId;
    private String type;
    private String title;
    private String summary;
    private String content;
    private BigDecimal score;
    private List<String> tags;
}
