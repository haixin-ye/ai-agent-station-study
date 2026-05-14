package yhx.com.domain.agent.service.prompt;

public class UntrustedContentPromptBuilder {

    private final SharedPromptFragments sharedPromptFragments;

    public UntrustedContentPromptBuilder(SharedPromptFragments sharedPromptFragments) {
        this.sharedPromptFragments = sharedPromptFragments;
    }

    public PromptLayer build() {
        return PromptLayer.builder()
                .layerType(PromptLayerTypeEnumVO.UNTRUSTED_CONTENT_RULES)
                .heading("Untrusted Content Rules")
                .content(sharedPromptFragments.untrustedContentRules())
                .javaOwned(true)
                .build();
    }
}
