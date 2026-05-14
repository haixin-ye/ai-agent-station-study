package yhx.com.domain.agent.service.invocation;

import com.alibaba.fastjson.JSONObject;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextPlannerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;

public class NodeOutputMapper {

    public Object map(String componentCode, String contractVersion, JSONObject jsonObject) {
        if (AgentComponentCodeEnumVO.CONTEXT_PLANNER.name().equals(componentCode)) {
            return mapContextPlannerOutput(jsonObject);
        }
        if (AgentComponentCodeEnumVO.MAIN_AGENT.name().equals(componentCode)
                || AgentComponentCodeEnumVO.FINAL_REPAIR.name().equals(componentCode)) {
            return mapMainAgentAction(jsonObject);
        }
        if (AgentComponentCodeEnumVO.RAG_VERIFIER.name().equals(componentCode)
                || AgentComponentCodeEnumVO.TOOL_VERIFIER.name().equals(componentCode)) {
            return mapVerificationResult(jsonObject);
        }
        if (AgentComponentCodeEnumVO.FINAL_RESPONSE_GUARD.name().equals(componentCode)) {
            return mapFinalResponseGuardResult(jsonObject);
        }
        if (AgentComponentCodeEnumVO.CONTRACT_REPAIR.name().equals(componentCode)) {
            return jsonObject;
        }
        throw new IllegalArgumentException("Unsupported component code: " + componentCode);
    }

    public ContextPlannerOutputVO mapContextPlannerOutput(JSONObject jsonObject) {
        String status = jsonObject.getString("status");
        if (ContextPlannerStatusEnumVO.ofCode(status).isEmpty()) {
            throw new IllegalArgumentException("Unknown ContextPlanner status: " + status);
        }
        return jsonObject.toJavaObject(ContextPlannerOutputVO.class);
    }

    public MainAgentActionVO mapMainAgentAction(JSONObject jsonObject) {
        String action = jsonObject.getString("action");
        if (MainAgentActionTypeEnumVO.ofCode(action).isEmpty()) {
            throw new IllegalArgumentException("Unknown MainAgent action: " + action);
        }
        return jsonObject.toJavaObject(MainAgentActionVO.class);
    }

    public VerificationResultVO mapVerificationResult(JSONObject jsonObject) {
        return jsonObject.toJavaObject(VerificationResultVO.class);
    }

    public FinalResponseGuardResultVO mapFinalResponseGuardResult(JSONObject jsonObject) {
        return jsonObject.toJavaObject(FinalResponseGuardResultVO.class);
    }
}
