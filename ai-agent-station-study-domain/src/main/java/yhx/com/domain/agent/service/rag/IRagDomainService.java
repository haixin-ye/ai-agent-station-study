package yhx.com.domain.agent.service.rag;

import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;

import java.util.List;
import java.util.Set;

/**
 * RAG domain service.
 *
 * @author yhx
 */
public interface IRagDomainService {

    Set<String> queryRagTagList();

    List<RagDocumentEntity> ingestFiles(RagFileIngestCommandEntity commandEntity);

    void analyzeGitRepository(RagGitIngestCommandEntity commandEntity) throws Exception;

}


