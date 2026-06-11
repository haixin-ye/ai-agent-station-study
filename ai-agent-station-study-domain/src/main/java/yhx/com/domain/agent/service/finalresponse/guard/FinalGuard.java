package yhx.com.domain.agent.service.finalresponse.guard;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;

public interface FinalGuard {

    FinalResponseGuardResultVO check(FinalResponseGuardInputVO input);
}
