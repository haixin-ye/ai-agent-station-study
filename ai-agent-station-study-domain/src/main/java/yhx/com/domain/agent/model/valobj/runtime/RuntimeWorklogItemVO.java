package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeWorklogItemVO {

    private String workId;
    private String runId;
    private Integer loopIndex;
    private Long sequence;
    private String actionType;
    private String status;
    private String stepId;
    private String sourceComponent;
    private String requestRef;
    private ActionRequestSnapshotVO request;
    private String resultRef;
    private ActionResultSnapshotVO result;
    private List<String> resultEvidenceIds;
    private String failureCode;
    private String failureMessage;
    private Boolean retryable;
    private String repeatGuardKey;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Map<String, Object> metadata;
}
