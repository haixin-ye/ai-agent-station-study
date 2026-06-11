package yhx.com.domain.agent.model.valobj.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContinuationCheckpointVO {

    private String handler;
    private RuntimePhaseEnumVO resumePhase;
    private String sourceComponent;
    private String relatedRunId;
    private Integer relatedLoopIndex;
    private String expectedAnswerValueType;
    private Map<String, Object> payload;
}
