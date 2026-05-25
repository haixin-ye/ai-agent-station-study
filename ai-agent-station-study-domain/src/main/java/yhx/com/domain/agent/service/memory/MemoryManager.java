package yhx.com.domain.agent.service.memory;

import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;

import java.util.List;

public class MemoryManager {

    private final IMemoryRepository memoryRepository;
    private final MemoryCandidatePreselector memoryCandidatePreselector;
    private final MemoryVectorIndexingService vectorIndexingService;

    public MemoryManager(IMemoryRepository memoryRepository) {
        this(memoryRepository, new MemoryCandidatePreselector(), null);
    }

    public MemoryManager(IMemoryRepository memoryRepository, MemoryCandidatePreselector memoryCandidatePreselector) {
        this(memoryRepository, memoryCandidatePreselector, null);
    }

    public MemoryManager(IMemoryRepository memoryRepository,
                         MemoryCandidatePreselector memoryCandidatePreselector,
                         MemoryVectorIndexingService vectorIndexingService) {
        this.memoryRepository = memoryRepository;
        this.memoryCandidatePreselector = memoryCandidatePreselector;
        this.vectorIndexingService = vectorIndexingService;
    }

    public List<MemoryCandidateVO> selectMemoryCandidates(String userId, String sessionId, String userInput, int limit) {
        return memoryCandidatePreselector.select(userInput, memoryRepository.findMemoryCandidates(userId, sessionId, userInput, limit), limit);
    }

    public void saveConversationSummary(AgentConversationSummaryEntity summary) {
        memoryRepository.saveConversationSummary(summary);
        if (vectorIndexingService != null) {
            vectorIndexingService.indexConversationSummary(summary);
        }
    }

    public void saveLongTermMemory(AgentMemoryEntity memory) {
        memoryRepository.saveLongTermMemory(memory);
        if (vectorIndexingService != null) {
            vectorIndexingService.indexMemory(memory);
        }
    }
}
