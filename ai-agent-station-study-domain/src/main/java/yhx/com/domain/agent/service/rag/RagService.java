package yhx.com.domain.agent.service.rag;

import yhx.com.domain.agent.adapter.repository.IRagRepository;
import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitIngestCommandEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * RAG domain service implementation.
 *
 * @author yhx
 */
@Service
public class RagService implements IRagDomainService {

    @Resource
    private IRagRepository ragRepository;

    public RagService() {
    }

    public RagService(IRagRepository ragRepository) {
        this.ragRepository = ragRepository;
    }

    @Override
    public Set<String> queryRagTagList() {
        return ragRepository.queryRagTagList();
    }

    @Override
    public void ingestFiles(RagFileIngestCommandEntity commandEntity) {
        if (commandEntity == null || commandEntity.getKnowledgeTag() == null || commandEntity.getKnowledgeTag().trim().isEmpty()) {
            throw new IllegalArgumentException("knowledgeTag must not be blank.");
        }
        if (commandEntity.getFiles() == null || commandEntity.getFiles().isEmpty()) {
            throw new IllegalArgumentException("files must not be empty.");
        }
        ragRepository.ingestFiles(commandEntity);
    }

    @Override
    public void analyzeGitRepository(RagGitIngestCommandEntity commandEntity) throws Exception {
        if (commandEntity == null || commandEntity.getRepoUrl() == null || commandEntity.getRepoUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("repoUrl must not be blank.");
        }
        ragRepository.ingestGitRepository(commandEntity);
    }

}
