package yhx.com.domain.agent.service.runtime;

import java.util.Locale;

public class FinalResponseGuard {

    public boolean isSafe(String finalAnswer) {
        if (finalAnswer == null || finalAnswer.trim().isEmpty()) {
            return false;
        }
        String normalized = finalAnswer.toLowerCase(Locale.ROOT);
        return !normalized.contains("runtime")
                && !normalized.contains("node")
                && !normalized.contains("trace")
                && !normalized.contains("verifier")
                && !normalized.contains("prompt")
                && !normalized.contains("contract")
                && !normalized.contains("stateview")
                && !normalized.contains("statedelta")
                && !normalized.contains("tool receipt");
    }
}
