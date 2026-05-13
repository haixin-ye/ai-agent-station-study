package yhx.com.domain.agent.service.rag;

import yhx.com.domain.agent.model.entity.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.RagGitIngestCommandEntity;

import java.util.Set;

/**
 * RAG domain service.
 *
 * @author yhx
 */
public interface IRagService {

    Set<String> queryRagTagList();

    void ingestFiles(RagFileIngestCommandEntity commandEntity);

    void analyzeGitRepository(RagGitIngestCommandEntity commandEntity) throws Exception;

}
