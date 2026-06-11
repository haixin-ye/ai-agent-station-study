package yhx.com.domain.agent.model.valobj.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptLayer {

    private PromptLayerTypeEnumVO layerType;
    private String heading;
    private String content;
    private Integer orderNo;
    private Boolean javaOwned;
}
