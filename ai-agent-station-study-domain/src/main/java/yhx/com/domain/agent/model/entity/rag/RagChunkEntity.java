package yhx.com.domain.agent.model.entity.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChunkEntity {

    private String chunkId;
    private String documentId;
    private String userId;
    private String sessionId;
    private Integer chunkNo;
    private String chunkType;
    private String headingPath;
    private String summary;
    private String contentRef;
    private String retrievalTextRef;
    private String contentSha256;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
