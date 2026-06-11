package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolEvidenceCreationResultVO {

    private List<String> evidenceIds;
    private List<MaterializedEvidenceVO> evidence;
}
