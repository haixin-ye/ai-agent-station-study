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
public class VectorIndexRecordVO {

    private VectorCollectionTypeEnumVO collectionType;
    private VectorSourceTypeEnumVO sourceType;
    private String sourceId;
    private String vectorId;
    private String userId;
    private String sessionId;
    private String text;
    private String summary;
    private String contentHash;
    private Map<String, Object> metadata;
    private LocalDateTime occurredAt;
}
