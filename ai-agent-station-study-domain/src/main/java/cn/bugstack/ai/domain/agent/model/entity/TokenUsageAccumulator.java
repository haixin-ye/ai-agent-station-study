package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Session-scope token usage accumulator.
 */
public class TokenUsageAccumulator {

    private final AtomicLong inputTokens = new AtomicLong(0L);
    private final AtomicLong outputTokens = new AtomicLong(0L);
    private final AtomicLong totalTokens = new AtomicLong(0L);

    public void add(long input, long output, long total) {
        inputTokens.addAndGet(Math.max(input, 0L));
        outputTokens.addAndGet(Math.max(output, 0L));
        totalTokens.addAndGet(Math.max(total, 0L));
    }

    public TokenUsageSnapshot snapshot() {
        return new TokenUsageSnapshot(inputTokens.get(), outputTokens.get(), totalTokens.get());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsageSnapshot {
        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
    }
}
