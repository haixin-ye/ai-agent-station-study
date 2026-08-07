package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.RagCandidateVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailedRecallResultVO {
    private ContextCandidateBundleVO candidateBundle;
    private List<RagCandidateVO> ragCandidates;
    private List<VectorRecallHitVO> vectorHits;
    private List<VectorRecallHitVO> lexicalHits;
    private Long elapsedMs;
    private Map<String, Object> diagnostics;
}
