package yhx.com.domain.agent.service.invocation;

import com.alibaba.fastjson.JSONObject;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextPlannerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.memory.ConversationRollupOutputVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryExtractionOutputVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceOutputVO;
import yhx.com.domain.agent.model.valobj.memory.SessionTaskSummaryOutputVO;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryOutputVO;
import yhx.com.domain.agent.model.valobj.rag.RagAssetAnalysisResultVO;

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
        if (AgentComponentCodeEnumVO.TURN_SUMMARY.name().equals(componentCode)) {
            return jsonObject.toJavaObject(TurnSummaryOutputVO.class);
        }
        if (AgentComponentCodeEnumVO.MEMORY_EXTRACTOR.name().equals(componentCode)) {
            return jsonObject.toJavaObject(MemoryExtractionOutputVO.class);
        }
        if (AgentComponentCodeEnumVO.SESSION_TASK_SUMMARY.name().equals(componentCode)) {
            return jsonObject.toJavaObject(SessionTaskSummaryOutputVO.class);
        }
        if (AgentComponentCodeEnumVO.MEMORY_GOVERNANCE.name().equals(componentCode)) {
            return jsonObject.toJavaObject(MemoryGovernanceOutputVO.class);
        }
        if (AgentComponentCodeEnumVO.CONVERSATION_ROLLUP.name().equals(componentCode)) {
            return jsonObject.toJavaObject(ConversationRollupOutputVO.class);
        }
        if (AgentComponentCodeEnumVO.RAG_ASSET_ANALYZER.name().equals(componentCode)) {
            return jsonObject.toJavaObject(RagAssetAnalysisResultVO.class);
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
