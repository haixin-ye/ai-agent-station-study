package yhx.com.domain.agent.service.finalresponse.guard;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EvidenceReferenceGuard extends FinalGuardSupport {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(evidence-[A-Za-z0-9_-]+)]");

    @Override
    public FinalResponseGuardResultVO check(FinalResponseGuardInputVO input) {
        String content = content(input);
        if (content == null || content.isBlank()) {
            return passed(input);
        }
        List<String> allowed = input == null || input.getEvidenceRefs() == null ? List.of() : input.getEvidenceRefs();
        Matcher matcher = CITATION_PATTERN.matcher(content);
        while (matcher.find()) {
            String evidenceId = matcher.group(1);
            if (!allowed.contains(evidenceId)) {
                return failed(input, "FINAL_INVALID_CITATION", "Final answer cites unavailable evidence: " + evidenceId);
            }
        }
        return passed(input);
    }
}
