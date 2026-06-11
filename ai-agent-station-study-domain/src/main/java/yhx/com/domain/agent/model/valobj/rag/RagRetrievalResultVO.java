package yhx.com.domain.agent.model.valobj.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.runtime.RagRuntimeStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRetrievalResultVO {

    private String ragQueryId;
    private String runId;
    private RagRuntimeStatusEnumVO status;
    private List<RagHitVO> hits;
    private List<String> evidenceIds;
    private RuntimeFailureCodeEnumVO failureCode;
    private String failureMessage;
}
