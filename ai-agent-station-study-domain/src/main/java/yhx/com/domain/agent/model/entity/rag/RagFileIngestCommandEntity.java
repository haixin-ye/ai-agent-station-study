package yhx.com.domain.agent.model.entity.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RAG file ingestion command.
 *
 * @author yhx
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagFileIngestCommandEntity {

    private String userId;

    private String sessionId;

    private String knowledgeTag;

    private List<RagFilePayloadEntity> files;

    private Map<String, Object> indexingMetadata;

}

