package yhx.com.domain.agent.service.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
