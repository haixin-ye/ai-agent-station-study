package yhx.com.domain.agent.service.finalresponse.guard;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;

import java.util.List;
import java.util.Locale;

public class InternalLeakGuard extends FinalGuardSupport {

    private static final List<String> BLOCKED_TERMS = List.of(
            "runtime", "node", "verifier", "trace", "contract", "prompt",
            "stateview", "statedelta", "tool receipt", "raw output",
            "repair process", "validation result", "developer trace"
    );

    @Override
    public FinalResponseGuardResultVO check(FinalResponseGuardInputVO input) {
        if (Boolean.TRUE.equals(input == null ? null : input.getUserAskedForInternals())) {
            return passed(input);
        }
        String content = content(input);
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        for (String blockedTerm : BLOCKED_TERMS) {
            if (normalized.contains(blockedTerm)) {
                return failed(input, "FINAL_INTERNAL_LEAK", "Final answer contains internal process wording: " + blockedTerm);
            }
        }
        return passed(input);
    }
}
