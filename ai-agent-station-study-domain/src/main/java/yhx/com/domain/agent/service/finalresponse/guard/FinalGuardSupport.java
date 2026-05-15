package yhx.com.domain.agent.service.finalresponse.guard;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;

abstract class FinalGuardSupport implements FinalGuard {

    protected FinalResponseGuardResultVO passed(FinalResponseGuardInputVO input) {
        return FinalResponseGuardResultVO.builder()
                .status("PASSED")
                .finalContent(content(input))
                .build();
    }

    protected FinalResponseGuardResultVO failed(FinalResponseGuardInputVO input, String failureCode, String detail) {
        return FinalResponseGuardResultVO.builder()
                .status("FAILED")
                .finalContent(content(input))
                .failureCode(failureCode)
                .detail(detail)
                .build();
    }

    protected String content(FinalResponseGuardInputVO input) {
        return input == null || input.getCandidate() == null ? null : input.getCandidate().getContent();
    }
}
