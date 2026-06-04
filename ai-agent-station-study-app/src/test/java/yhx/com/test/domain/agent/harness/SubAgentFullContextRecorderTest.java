package yhx.com.test.domain.agent.harness;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.agent.SubAgentFullContextVO;
import yhx.com.domain.agent.service.agent.SubAgentFullContextRecorder;

public class SubAgentFullContextRecorderTest {

    @Test
    public void initial_context_starts_with_parent_task_entry() {
        SubAgentFullContextVO context = new SubAgentFullContextRecorder()
                .start("child-run-1", "parent-run-1", "task-1", "Read file A.");

        Assert.assertEquals("child-run-1", context.getChildRunId());
        Assert.assertEquals("parent-run-1", context.getParentRunId());
        Assert.assertEquals("task-1", context.getTaskId());
        Assert.assertEquals(1, context.getEntries().size());
        Assert.assertEquals(Integer.valueOf(1), context.getEntries().get(0).getSequenceNo());
        Assert.assertEquals("PARENT_TASK", context.getEntries().get(0).getEntryType());
        Assert.assertEquals("Read file A.", context.getEntries().get(0).getContent());
    }

    @Test
    public void append_preserves_entry_order() {
        SubAgentFullContextRecorder recorder = new SubAgentFullContextRecorder();
        SubAgentFullContextVO context = recorder.start("child-run-1", "parent-run-1", "task-1", "Read file A.");

        recorder.append(context, "ACTION_REQUEST", "CALL_TOOL read_file");
        recorder.append(context, "TOOL_RESULT", "file content");
        recorder.append(context, "COMMIT", "done");

        Assert.assertEquals(4, context.getEntries().size());
        Assert.assertEquals(Integer.valueOf(1), context.getEntries().get(0).getSequenceNo());
        Assert.assertEquals(Integer.valueOf(2), context.getEntries().get(1).getSequenceNo());
        Assert.assertEquals(Integer.valueOf(3), context.getEntries().get(2).getSequenceNo());
        Assert.assertEquals(Integer.valueOf(4), context.getEntries().get(3).getSequenceNo());
        Assert.assertEquals("COMMIT", context.getEntries().get(3).getEntryType());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void entries_are_immutable_to_callers() {
        SubAgentFullContextVO context = new SubAgentFullContextRecorder()
                .start("child-run-1", "parent-run-1", "task-1", "Read file A.");

        context.getEntries().clear();
    }
}
