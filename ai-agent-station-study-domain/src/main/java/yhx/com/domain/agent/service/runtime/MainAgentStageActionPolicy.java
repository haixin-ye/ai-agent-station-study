package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

import java.util.Map;
import java.util.Set;

public class MainAgentStageActionPolicy {

    private static final Set<MainAgentActionTypeEnumVO> WORK_ACTIONS = Set.of(
            MainAgentActionTypeEnumVO.RETRIEVE_RAG,
            MainAgentActionTypeEnumVO.CALL_TOOL,
            MainAgentActionTypeEnumVO.ASK_USER,
            MainAgentActionTypeEnumVO.DELEGATE_AGENTS,
            MainAgentActionTypeEnumVO.READY_TO_DELIVER,
            MainAgentActionTypeEnumVO.FAIL);

    private static final Map<MainAgentStageEnumVO, Set<MainAgentActionTypeEnumVO>> ALLOWED = Map.of(
            MainAgentStageEnumVO.PLANNING, WORK_ACTIONS,
            MainAgentStageEnumVO.EXECUTING, WORK_ACTIONS,
            MainAgentStageEnumVO.DELIVERING, Set.of(MainAgentActionTypeEnumVO.FINAL, MainAgentActionTypeEnumVO.FAIL));

    public String validate(RuntimeExecutionContext context, MainAgentActionTypeEnumVO actionType) {
        if (context == null || context.getRunContextState() == null
                || context.getRunContextState().getMainAgentStage() == null) {
            return "MainAgent action requires canonical RunContextState with a stage.";
        }
        MainAgentStageEnumVO stage = context.getRunContextState().getMainAgentStage();
        if (!ALLOWED.getOrDefault(stage, Set.of()).contains(actionType)) {
            return "Action " + actionType.code() + " is not allowed during MainAgent stage " + stage.name() + ".";
        }
        return null;
    }
}
