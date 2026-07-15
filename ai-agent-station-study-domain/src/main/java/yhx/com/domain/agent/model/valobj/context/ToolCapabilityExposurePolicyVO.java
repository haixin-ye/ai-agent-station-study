package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCapabilityExposurePolicyVO {

    @Builder.Default
    private int maxTools = 32;
    @Builder.Default
    private int maxDescriptionChars = 300;
    @Builder.Default
    private int maxSchemaDepth = 5;
    @Builder.Default
    private int maxSchemaPropertiesPerTool = 40;
    @Builder.Default
    private int maxSchemaCharsPerTool = 2400;
    @Builder.Default
    private int maxTotalSchemaChars = 12000;
    @Builder.Default
    private int maxRequiredArgumentsPerTool = 64;
    @Builder.Default
    private int maxCapabilityCharsPerTool = 3200;
    @Builder.Default
    private int maxTotalCapabilityChars = 10000;
}
