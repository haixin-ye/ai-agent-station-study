package yhx.com.domain.agent.service.memory;

import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public class MemoryCandidatePreselector {

    public List<MemoryCandidateVO> select(String userInput, List<AgentMemoryEntity> memories, int limit) {
        if (memories == null || memories.isEmpty()) {
            return List.of();
        }
        String query = userInput == null ? "" : userInput.toLowerCase();
        return memories.stream()
                .map(memory -> toCandidate(query, memory))
                .filter(candidate -> candidate.getRelevanceScore() > 0.0 || (candidate.getScore() != null && candidate.getScore().compareTo(BigDecimal.ZERO) > 0))
                .sorted(Comparator.comparing(MemoryCandidateVO::getRelevanceScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(limit, 0))
                .toList();
    }

    private MemoryCandidateVO toCandidate(String query, AgentMemoryEntity memory) {
        String summary = memory.getSummary() == null ? "" : memory.getSummary().toLowerCase();
        double relevance = overlap(query, summary);
        if (containsAny(query, "preference", "prefer", "project", "session", "喜欢", "偏好", "项目", "之前", "刚才")) {
            relevance += 2.0;
        }
        if (memory.getScore() != null) {
            relevance += Math.min(2.0, memory.getScore().doubleValue());
        }
        return MemoryCandidateVO.builder()
                .memoryId(memory.getMemoryId())
                .memoryType(memory.getMemoryType())
                .summary(memory.getSummary())
                .contentRef(memory.getContentRef())
                .score(memory.getScore())
                .relevanceScore(relevance)
                .build();
    }

    private double overlap(String query, String text) {
        if (query.isBlank() || text.isBlank()) {
            return 0.0;
        }
        double score = 0.0;
        for (String token : query.split("\\s+")) {
            if (!token.isBlank() && text.contains(token)) {
                score += 1.0;
            }
        }
        return score;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
