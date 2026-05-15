package yhx.com.domain.agent.service.finalresponse.guard;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;

public class EmptyAnswerGuard extends FinalGuardSupport {

    @Override
    public FinalResponseGuardResultVO check(FinalResponseGuardInputVO input) {
        String content = content(input);
        if (content == null || content.trim().isEmpty()) {
            return failed(input, "FINAL_EMPTY", "Final answer is empty.");
        }
        return passed(input);
    }
}
