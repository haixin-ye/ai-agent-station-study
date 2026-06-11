package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitIngestCommandEntity;

import java.util.Set;

/**
 * RAG repository port.
 *
 * @author yhx
 */
public interface IRagRepository {

    Set<String> queryRagTagList();

    void ingestFiles(RagFileIngestCommandEntity commandEntity);

    void ingestGitRepository(RagGitIngestCommandEntity commandEntity) throws Exception;

}

