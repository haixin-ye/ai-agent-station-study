package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallCaseImportItemVO {
    private String externalId;
    private String query;
    private String sourceScope;
    private List<RecallExpectedItemVO> expected;
    private List<String> tags;
}
