package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.service.runtime.RuntimeStateMachine;

public class RuntimeStateMachineTest {

    @Test
    public void created_can_enter_preparing_context() {
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();

        Assert.assertTrue(stateMachine.canEnter(RuntimePhaseEnumVO.CREATED, RuntimePhaseEnumVO.PREPARING_CONTEXT));
    }

    @Test
    public void waiting_user_must_resume_through_resolving_user_answer() {
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();

        Assert.assertTrue(stateMachine.canEnter(RuntimePhaseEnumVO.WAITING_USER, RuntimePhaseEnumVO.RESOLVING_USER_ANSWER));
        Assert.assertFalse(stateMachine.canEnter(RuntimePhaseEnumVO.WAITING_USER, RuntimePhaseEnumVO.CALLING_MAIN_NODE));
    }

    @Test
    public void illegal_transition_is_rejected() {
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();

        Assert.assertFalse(stateMachine.canEnter(RuntimePhaseEnumVO.CREATED, RuntimePhaseEnumVO.CALLING_MAIN_NODE));
    }

    @Test
    public void handling_action_can_continue_without_replanning_context() {
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();

        Assert.assertTrue(stateMachine.canEnter(RuntimePhaseEnumVO.HANDLING_ACTION, RuntimePhaseEnumVO.BUILDING_STATE_VIEW));
        Assert.assertTrue(stateMachine.canEnter(RuntimePhaseEnumVO.HANDLING_ACTION, RuntimePhaseEnumVO.CALLING_MAIN_NODE));
    }

    @Test
    public void preparing_tool_can_resume_directly_to_action_handler() {
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();

        Assert.assertTrue(stateMachine.canEnter(RuntimePhaseEnumVO.PREPARING_TOOL, RuntimePhaseEnumVO.HANDLING_ACTION));
    }

    @Test
    public void terminal_status_cannot_continue_loop() {
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();

        Assert.assertTrue(stateMachine.isTerminalRunStatus(RunStatusEnumVO.COMPLETED));
        Assert.assertTrue(stateMachine.isTerminalRunStatus(RunStatusEnumVO.FAILED));
        Assert.assertTrue(stateMachine.isTerminalRunStatus(RunStatusEnumVO.CANCELLED));
        Assert.assertFalse(stateMachine.isTerminalRunStatus(RunStatusEnumVO.RUNNING));
    }
}
