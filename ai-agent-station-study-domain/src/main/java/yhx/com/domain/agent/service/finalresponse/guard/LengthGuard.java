package yhx.com.domain.agent.service.finalresponse.guard;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;

public class LengthGuard extends FinalGuardSupport {

    @Override
    public FinalResponseGuardResultVO check(FinalResponseGuardInputVO input) {
        Integer maxOutputChars = input == null ? null : input.getMaxOutputChars();
        String content = content(input);
        if (maxOutputChars != null && maxOutputChars > 0 && content != null && content.length() > maxOutputChars) {
            return failed(input, "FINAL_TOO_LONG", "Final answer exceeds max output characters.");
        }
        return passed(input);
    }
}
