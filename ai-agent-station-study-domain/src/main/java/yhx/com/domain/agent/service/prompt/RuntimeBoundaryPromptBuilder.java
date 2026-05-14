package yhx.com.domain.agent.service.prompt;

public class RuntimeBoundaryPromptBuilder {

    private final SharedPromptFragments sharedPromptFragments;

    public RuntimeBoundaryPromptBuilder(SharedPromptFragments sharedPromptFragments) {
        this.sharedPromptFragments = sharedPromptFragments;
    }

    public PromptLayer build() {
        return PromptLayer.builder()
                .layerType(PromptLayerTypeEnumVO.RUNTIME_BOUNDARY_RULES)
                .heading("Runtime Boundary Rules")
                .content(sharedPromptFragments.runtimeBoundaryRules())
                .javaOwned(true)
                .build();
    }
}
