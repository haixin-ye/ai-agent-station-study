package cn.bugstack.ai.domain.agent.service.rag;

import cn.bugstack.ai.domain.agent.adapter.repository.IRagRepository;
import cn.bugstack.ai.domain.agent.model.entity.RagFileIngestCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.RagGitIngestCommandEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * RAG domain service implementation.
 *
 * @author yhx
 */
@Service
public class RagService implements IRagService {

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
            throw new IllegalArgumentException("知识库标签 不能为空.");
        }
        if (commandEntity.getFiles() == null || commandEntity.getFiles().isEmpty()) {
            throw new IllegalArgumentException("文件 不能为空.");
        }
        ragRepository.ingestFiles(commandEntity);
    }

    @Override
    public void analyzeGitRepository(RagGitIngestCommandEntity commandEntity) throws Exception {
        if (commandEntity == null || commandEntity.getRepoUrl() == null || commandEntity.getRepoUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("GitHub地址链接不能为空.");
        }
        ragRepository.ingestGitRepository(commandEntity);
    }

}
