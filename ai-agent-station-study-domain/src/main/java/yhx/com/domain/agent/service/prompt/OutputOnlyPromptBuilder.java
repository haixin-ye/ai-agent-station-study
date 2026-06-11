package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

public class OutputOnlyPromptBuilder {

    private final SharedPromptFragments sharedPromptFragments;

    public OutputOnlyPromptBuilder(SharedPromptFragments sharedPromptFragments) {
        this.sharedPromptFragments = sharedPromptFragments;
    }

    public PromptLayer build() {
        return PromptLayer.builder()
                .layerType(PromptLayerTypeEnumVO.OUTPUT_ONLY_INSTRUCTION)
                .heading("Output Only Instruction")
                .content(sharedPromptFragments.outputOnlyInstruction())
                .javaOwned(true)
                .build();
    }
}
