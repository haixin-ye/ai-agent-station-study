package yhx.com.domain.agent.service.rag;

import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitIngestCommandEntity;

import java.util.Set;

/**
 * RAG domain service.
 *
 * @author yhx
 */
public interface IRagDomainService {

    Set<String> queryRagTagList();

    void ingestFiles(RagFileIngestCommandEntity commandEntity);

    void analyzeGitRepository(RagGitIngestCommandEntity commandEntity) throws Exception;

}


