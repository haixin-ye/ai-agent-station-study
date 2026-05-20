package yhx.com.domain.agent.service.finalresponse.guard;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;

import java.util.Locale;
import java.util.regex.Pattern;

public class InternalLeakGuard extends FinalGuardSupport {

    private static final Pattern INTERNAL_PROCESS_PATTERN = Pattern.compile(
            "(runtime\\s+(verifier|trace|validation|contract|state|phase|loop|guard))"
                    + "|((agent|main|context|rag|tool|final|repair)\\s+node)"
                    + "|(node\\s+(trace|contract|output|state|phase|loop))"
                    + "|(verifier\\s+(result|trace|node|detail))"
                    + "|(developer\\s+trace)"
                    + "|(tool\\s+receipt)"
                    + "|(raw\\s+output)"
                    + "|(repair\\s+process)"
                    + "|(validation\\s+result)"
                    + "|(stateview|statedelta)"
    );

    @Override
    public FinalResponseGuardResultVO check(FinalResponseGuardInputVO input) {
        if (Boolean.TRUE.equals(input == null ? null : input.getUserAskedForInternals())) {
            return passed(input);
        }
        String content = content(input);
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        if (INTERNAL_PROCESS_PATTERN.matcher(normalized).find()) {
            return failed(input, "FINAL_INTERNAL_LEAK", "Final answer contains internal process wording.");
        }
        return passed(input);
    }
}
