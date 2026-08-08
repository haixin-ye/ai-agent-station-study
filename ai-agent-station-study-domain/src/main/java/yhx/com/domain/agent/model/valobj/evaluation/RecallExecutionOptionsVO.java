package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallExecutionOptionsVO {
    private Integer topK;
    private Double minScore;
    private Boolean lexicalEnabled;
    private List<VectorCollectionTypeEnumVO> collectionTypes;
    private Map<String, Object> metadataFilters;
    private Long retrievalTimeoutMs;
}
