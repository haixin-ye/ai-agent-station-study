package yhx.com.domain.agent.model.entity.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG git ingestion command.
 *
 * @author yhx
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagGitIngestCommandEntity {

    private String userId;

    private String sessionId;

    private String repoUrl;

    private String branchName;

    private String userName;

    private String token;

}

