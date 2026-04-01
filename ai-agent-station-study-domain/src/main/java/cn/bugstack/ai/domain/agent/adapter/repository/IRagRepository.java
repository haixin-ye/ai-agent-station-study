package cn.bugstack.ai.domain.agent.adapter.repository;

import cn.bugstack.ai.domain.agent.model.entity.RagFileIngestCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.RagGitIngestCommandEntity;

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
