package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallRagAttachmentItemVO {
    private String externalId;
    private String documentId;
    private String title;
    private String summary;
    private List<String> tags;
}
