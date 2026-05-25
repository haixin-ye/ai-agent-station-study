package yhx.com.domain.agent.model.valobj.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorRecallHitVO {

    private VectorCollectionTypeEnumVO collectionType;
    private VectorSourceTypeEnumVO sourceType;
    private String sourceId;
    private String vectorId;
    private Double score;
    private String summary;
    private String snippet;
    private Map<String, Object> metadata;
    private LocalDateTime occurredAt;
}
