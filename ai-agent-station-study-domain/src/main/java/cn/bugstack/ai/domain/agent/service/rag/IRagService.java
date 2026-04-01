package cn.bugstack.ai.domain.agent.service.rag;

import cn.bugstack.ai.domain.agent.model.entity.RagFileIngestCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.RagGitIngestCommandEntity;

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
