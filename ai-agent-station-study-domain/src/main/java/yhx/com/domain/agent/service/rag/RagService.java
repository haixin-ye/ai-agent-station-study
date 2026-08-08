package yhx.com.domain.agent.service.rag;

import yhx.com.domain.agent.adapter.repository.IRagRepository;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitIngestCommandEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * RAG domain service implementation.
 *
 * @author yhx
 */
@Service
public class RagService implements IRagDomainService {

    public static final String DEFAULT_KNOWLEDGE_TAG = "global";

    @Resource
    private IRagRepository ragRepository;

    @Resource
    private RagAssetIngestionService ragAssetIngestionService;

    public RagService() {
    }

    public RagService(IRagRepository ragRepository) {
        this.ragRepository = ragRepository;
    }

    public RagService(IRagRepository ragRepository, RagAssetIngestionService ragAssetIngestionService) {
        this.ragRepository = ragRepository;
        this.ragAssetIngestionService = ragAssetIngestionService;
    }

    @Override
    public Set<String> queryRagTagList() {
        return ragRepository.queryRagTagList();
    }

    @Override
    public List<RagDocumentEntity> ingestFiles(RagFileIngestCommandEntity commandEntity) {
        if (commandEntity == null) {
            throw new IllegalArgumentException("RAG file ingest command is required.");
        }
        if (commandEntity.getFiles() == null || commandEntity.getFiles().isEmpty()) {
            throw new IllegalArgumentException("files must not be empty.");
        }
        commandEntity.setKnowledgeTag(defaultTag(commandEntity.getKnowledgeTag()));
        if (ragAssetIngestionService != null) {
            return ragAssetIngestionService.ingestFiles(commandEntity);
        }
        ragRepository.ingestFiles(commandEntity);
        return List.of();
    }

    @Override
    public void analyzeGitRepository(RagGitIngestCommandEntity commandEntity) throws Exception {
        if (commandEntity == null || commandEntity.getRepoUrl() == null || commandEntity.getRepoUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("repoUrl must not be blank.");
        }
        ragRepository.ingestGitRepository(commandEntity);
    }

    private String defaultTag(String knowledgeTag) {
        if (knowledgeTag == null || knowledgeTag.trim().isEmpty()) {
            return DEFAULT_KNOWLEDGE_TAG;
        }
        return knowledgeTag.trim();
    }

}
