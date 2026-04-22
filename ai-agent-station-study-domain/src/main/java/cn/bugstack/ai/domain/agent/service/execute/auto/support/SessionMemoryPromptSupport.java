package cn.bugstack.ai.domain.agent.service.execute.auto.support;

import cn.bugstack.ai.domain.agent.model.entity.SessionMemoryEntity;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SessionMemoryPromptSupport {

    public static final int DEFAULT_HISTORY_LIMIT = 5;

    private SessionMemoryPromptSupport() {
    }

    public static List<SessionMemoryEntity> sortChronologically(List<SessionMemoryEntity> sessionMemories) {
        if (sessionMemories == null || sessionMemories.isEmpty()) {
            return List.of();
        }

        List<SessionMemoryEntity> sorted = new ArrayList<>(sessionMemories);
        sorted.sort(Comparator.comparing(
                item -> item == null || item.getRoundNo() == null ? Integer.MAX_VALUE : item.getRoundNo()
        ));
        return sorted;
    }

    public static String buildSessionHistoryPrompt(List<SessionMemoryEntity> sessionMemories) {
        List<SessionMemoryEntity> sorted = sortChronologically(sessionMemories);
        if (sorted.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder("Session History:");
        for (SessionMemoryEntity item : sorted) {
            if (item == null) {
                continue;
            }

            String userMessage = safe(item.getUserMessage());
            String finalAnswer = safe(item.getFinalAnswer());
            if (!StringUtils.hasText(userMessage) && !StringUtils.hasText(finalAnswer)) {
                continue;
            }

            builder.append(System.lineSeparator())
                    .append("[Round ")
                    .append(item.getRoundNo() == null ? "?" : item.getRoundNo())
                    .append("]")
                    .append(System.lineSeparator())
                    .append("User: ")
                    .append(userMessage)
                    .append(System.lineSeparator())
                    .append("Assistant: ")
                    .append(finalAnswer)
                    .append(System.lineSeparator());
        }

        return builder.toString().trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
