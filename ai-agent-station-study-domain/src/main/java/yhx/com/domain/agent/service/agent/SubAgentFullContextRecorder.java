package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.SubAgentFullContextEntryVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentFullContextVO;

import java.util.ArrayList;

public class SubAgentFullContextRecorder {

    public SubAgentFullContextVO start(String childRunId, String parentRunId, String taskId, String parentTask) {
        SubAgentFullContextVO context = SubAgentFullContextVO.builder()
                .childRunId(childRunId)
                .parentRunId(parentRunId)
                .taskId(taskId)
                .entries(new ArrayList<>())
                .build();
        append(context, "PARENT_TASK", parentTask);
        return context;
    }

    public void append(SubAgentFullContextVO context, String entryType, String content) {
        if (context == null) {
            throw new IllegalArgumentException("SubAgent full context is required.");
        }
        int nextSequenceNo = context.getEntries().size() + 1;
        context.appendEntry(SubAgentFullContextEntryVO.builder()
                .sequenceNo(nextSequenceNo)
                .entryType(entryType)
                .content(content)
                .build());
    }
}
