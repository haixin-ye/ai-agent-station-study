package yhx.com.domain.agent.service.memory;

import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;

import java.util.List;

public class MemoryManager {

    private final IMemoryRepository memoryRepository;
    private final MemoryCandidatePreselector memoryCandidatePreselector;

    public MemoryManager(IMemoryRepository memoryRepository) {
        this(memoryRepository, new MemoryCandidatePreselector());
    }

    public MemoryManager(IMemoryRepository memoryRepository, MemoryCandidatePreselector memoryCandidatePreselector) {
        this.memoryRepository = memoryRepository;
        this.memoryCandidatePreselector = memoryCandidatePreselector;
    }

    public List<MemoryCandidateVO> selectMemoryCandidates(String userId, String sessionId, String userInput, int limit) {
        return memoryCandidatePreselector.select(userInput, memoryRepository.findMemoryCandidates(userId, sessionId, userInput, limit), limit);
    }

    public void saveConversationSummary(AgentConversationSummaryEntity summary) {
        memoryRepository.saveConversationSummary(summary);
    }

    public void saveLongTermMemory(AgentMemoryEntity memory) {
        memoryRepository.saveLongTermMemory(memory);
    }
}
