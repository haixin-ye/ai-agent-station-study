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
public class RagDocumentEntity {

    private String documentId;
    private String userId;
    private String sessionId;
    private String sourceType;
    private String sourceName;
    private String repositoryUrl;
    private String repositoryName;
    private String branchName;
    private String relativePath;
    private String title;
    private String summary;
    private String contentRef;
    private String summaryRef;
    private String contentSha256;
    private String status;
    private Integer chunkCount;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
