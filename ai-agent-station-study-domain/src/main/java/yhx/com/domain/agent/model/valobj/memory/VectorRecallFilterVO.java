package yhx.com.domain.agent.model.valobj.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorRecallFilterVO {

    private String userId;
    private String sessionId;
    private List<VectorCollectionTypeEnumVO> collectionTypes;
    private LocalDateTime from;
    private LocalDateTime to;
    private Map<String, Object> metadataFilters;
}
