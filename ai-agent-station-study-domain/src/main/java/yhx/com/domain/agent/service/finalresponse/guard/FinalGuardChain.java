package yhx.com.domain.agent.service.finalresponse.guard;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;

import java.util.List;

public class FinalGuardChain {

    private final List<FinalGuard> guards;

    public FinalGuardChain() {
        this(List.of(
                new EmptyAnswerGuard(),
                new InternalLeakGuard(),
                new FormatGuard(),
                new EvidenceReferenceGuard(),
                new ToolClaimGuard(),
                new LengthGuard()
        ));
    }

    public FinalGuardChain(List<FinalGuard> guards) {
        this.guards = guards;
    }

    public FinalResponseGuardResultVO check(FinalResponseGuardInputVO input) {
        for (FinalGuard guard : guards) {
            FinalResponseGuardResultVO result = guard.check(input);
            if (result != null && "FAILED".equals(result.getStatus())) {
                return result;
            }
        }
        return FinalResponseGuardResultVO.builder()
                .status("PASSED")
                .finalContent(input == null || input.getCandidate() == null ? null : input.getCandidate().getContent())
                .detail("Final response guard passed.")
                .build();
    }
}
