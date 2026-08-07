package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

/**
 * Applies the semantic state change of an action after the action has passed
 * Runtime validation and is about to enter its deterministic handler.
 */
public class MainAgentActionAcceptanceService {

    private final TaskLedgerMergeService taskLedgerMergeService;

    public MainAgentActionAcceptanceService() {
        this(new TaskLedgerMergeService());
    }

    public MainAgentActionAcceptanceService(TaskLedgerMergeService taskLedgerMergeService) {
        this.taskLedgerMergeService = taskLedgerMergeService == null
                ? new TaskLedgerMergeService()
                : taskLedgerMergeService;
    }

    public void accept(RuntimeExecutionContext context, MainAgentActionVO action) {
        if (context == null || context.getRunContextState() == null || action == null) {
            return;
        }
        if (action.getTaskUpdate() != null) {
            context.getRunContextState().setTaskLedger(taskLedgerMergeService.merge(
                    context.getRunContextState().getTaskLedger(),
                    action.getTaskUpdate(),
                    context.getLoopIndex()));
        }
        MainAgentStageEnumVO stage = context.getRunContextState().getMainAgentStage();
        if (stage == MainAgentStageEnumVO.PLANNING
                && !MainAgentActionTypeEnumVO.FAIL.code().equals(action.getAction())) {
            context.getRunContextState().setMainAgentStage(MainAgentStageEnumVO.EXECUTING);
        }
    }
}
