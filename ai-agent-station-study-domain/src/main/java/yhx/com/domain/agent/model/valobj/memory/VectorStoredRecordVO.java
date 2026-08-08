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
public class VectorStoredRecordVO {

    private VectorCollectionTypeEnumVO collectionType;
    private String vectorId;
    private VectorSourceTypeEnumVO sourceType;
    private String sourceId;
    private String userId;
    private String sessionId;
    private String content;
    private String summary;
    private Map<String, Object> metadata;
    private Integer embeddingDimensions;
    private LocalDateTime occurredAt;
}
