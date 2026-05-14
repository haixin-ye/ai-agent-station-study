package yhx.com.domain.agent.model.valobj.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagHitVO {

    private String ragHitId;
    private String sourceType;
    private String sourceId;
    private String title;
    private String chunkText;
    private String chunkRef;
    private Double score;
    private Integer rankNo;
    private Map<String, Object> metadata;
}
