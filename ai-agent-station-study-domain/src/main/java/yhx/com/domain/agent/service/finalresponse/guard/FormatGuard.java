package yhx.com.domain.agent.service.finalresponse.guard;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;

public class FormatGuard extends FinalGuardSupport {

    @Override
    public FinalResponseGuardResultVO check(FinalResponseGuardInputVO input) {
        String content = content(input);
        if (content == null) {
            return passed(input);
        }
        String requirement = input == null ? null : input.getUserFormatRequirement();
        boolean plainText = requirement == null || "PLAIN_TEXT".equalsIgnoreCase(requirement);
        String trimmed = content.trim();
        if (plainText && (trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return failed(input, "FINAL_FORMAT_VIOLATION", "Plain final answer must not be raw JSON.");
        }
        if (plainText && trimmed.lines().anyMatch(line -> line.trim().startsWith("#"))) {
            return failed(input, "FINAL_FORMAT_VIOLATION", "Plain final answer must not contain markdown headings.");
        }
        return passed(input);
    }
}
