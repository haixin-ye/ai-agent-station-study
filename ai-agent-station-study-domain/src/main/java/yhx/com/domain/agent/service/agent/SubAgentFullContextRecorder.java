package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.SubAgentFullContextEntryVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentFullContextVO;

import java.util.ArrayList;
import java.util.Optional;

public class SubAgentFullContextRecorder {

    private final SubAgentFullContextStore store;

    public SubAgentFullContextRecorder() {
        this(null);
    }

    public SubAgentFullContextRecorder(SubAgentFullContextStore store) {
        this.store = store;
    }

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
        persist(context);
    }

    public Optional<SubAgentFullContextVO> load(String snapshotRef) {
        if (store == null || snapshotRef == null || snapshotRef.isBlank()) {
            return Optional.empty();
        }
        return store.load(snapshotRef);
    }

    private void persist(SubAgentFullContextVO context) {
        if (store != null) {
            store.save(context);
        }
    }
}
