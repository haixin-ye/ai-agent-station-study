package yhx.com.domain.agent.service.prompt;

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
