package yhx.com.domain.agent.service.finalresponse;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;
import yhx.com.domain.agent.service.finalresponse.guard.FinalGuardChain;

public class FinalResponseGuard {

    private final FinalGuardChain guardChain;

    public FinalResponseGuard() {
        this(new FinalGuardChain());
    }

    public FinalResponseGuard(FinalGuardChain guardChain) {
        this.guardChain = guardChain;
    }

    public FinalResponseGuardResultVO check(FinalResponseGuardInputVO input) {
        return guardChain.check(input);
    }
}
