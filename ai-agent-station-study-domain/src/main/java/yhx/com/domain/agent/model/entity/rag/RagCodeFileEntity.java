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
public class RagCodeFileEntity {

    private String codeFileId;
    private String documentId;
    private String repositoryUrl;
    private String branchName;
    private String relativePath;
    private String language;
    private String fileSummary;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
