package yhx.com.domain.agent.service.runtime.port;

import yhx.com.domain.agent.model.valobj.runtime.PlanStateVO;

public interface PlanStatePort {

    String savePlan(String runId, PlanStateVO plan);

    PlanStateVO findPlan(String runId);
}
