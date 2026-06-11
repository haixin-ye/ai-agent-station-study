package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentFullContextVO {

    private String childRunId;
    private String parentRunId;
    private String taskId;
    private String snapshotRef;
    private List<SubAgentFullContextEntryVO> entries;

    public String getChildRunId() {
        return childRunId;
    }

    public void setChildRunId(String childRunId) {
        this.childRunId = childRunId;
    }

    public String getParentRunId() {
        return parentRunId;
    }

    public void setParentRunId(String parentRunId) {
        this.parentRunId = parentRunId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSnapshotRef() {
        return snapshotRef;
    }

    public void setSnapshotRef(String snapshotRef) {
        this.snapshotRef = snapshotRef;
    }

    public List<SubAgentFullContextEntryVO> getEntries() {
        if (entries == null) {
            return List.of();
        }
        return Collections.unmodifiableList(entries);
    }

    public void setEntries(List<SubAgentFullContextEntryVO> entries) {
        this.entries = entries;
    }

    public void appendEntry(SubAgentFullContextEntryVO entry) {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        entries.add(entry);
    }
}
