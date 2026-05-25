package yhx.com.domain.agent.service.prompt;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptAssemblyCommand;
import yhx.com.domain.agent.model.valobj.prompt.PromptAssemblyResult;
import yhx.com.domain.agent.model.valobj.prompt.PromptEnvelope;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class PromptAssembler {

    private static final Map<PromptLayerTypeEnumVO, Integer> ORDER = new EnumMap<>(PromptLayerTypeEnumVO.class);

    static {
        int index = 0;
        ORDER.put(PromptLayerTypeEnumVO.ROLE_PROMPT, index++);
        ORDER.put(PromptLayerTypeEnumVO.STABLE_BEHAVIOR_RULES, index++);
        ORDER.put(PromptLayerTypeEnumVO.RUNTIME_BOUNDARY_RULES, index++);
        ORDER.put(PromptLayerTypeEnumVO.UNTRUSTED_CONTENT_RULES, index++);
        ORDER.put(PromptLayerTypeEnumVO.OPERATING_CONTEXT, index++);
        ORDER.put(PromptLayerTypeEnumVO.INPUT_FIELD_GUIDE, index++);
        ORDER.put(PromptLayerTypeEnumVO.TASK_PROCEDURE, index++);
        ORDER.put(PromptLayerTypeEnumVO.RESPONSE_STYLE, index++);
        ORDER.put(PromptLayerTypeEnumVO.DECISION_POLICY, index++);
        ORDER.put(PromptLayerTypeEnumVO.RISK_AND_PERMISSION_POLICY, index++);
        ORDER.put(PromptLayerTypeEnumVO.OUTPUT_CONTRACT, index++);
        ORDER.put(PromptLayerTypeEnumVO.FEW_SHOT_EXAMPLES, index++);
        ORDER.put(PromptLayerTypeEnumVO.ANTI_EXAMPLES, index++);
        ORDER.put(PromptLayerTypeEnumVO.CURRENT_STATE_VIEW, index++);
        ORDER.put(PromptLayerTypeEnumVO.OUTPUT_ONLY_INSTRUCTION, index);
    }

    private final PromptContentProvider promptContentProvider;
    private final SharedPromptFragments sharedPromptFragments;
    private final OutputContractPromptRenderer outputContractPromptRenderer;

    public PromptAssembler(PromptContentProvider promptContentProvider) {
        this(promptContentProvider, new SharedPromptFragments(), new OutputContractPromptRenderer());
    }

    public PromptAssembler(PromptContentProvider promptContentProvider,
                           SharedPromptFragments sharedPromptFragments,
                           OutputContractPromptRenderer outputContractPromptRenderer) {
        this.promptContentProvider = promptContentProvider;
        this.sharedPromptFragments = sharedPromptFragments;
        this.outputContractPromptRenderer = outputContractPromptRenderer;
    }

    public PromptAssemblyResult assemble(PromptAssemblyCommand command) {
        List<PromptLayer> layers = new ArrayList<>();
        addRolePrompts(layers, command);
        layers.add(layer(PromptLayerTypeEnumVO.STABLE_BEHAVIOR_RULES, "Stable Behavior Rules", sharedPromptFragments.stableBehaviorRules()));
        layers.add(new RuntimeBoundaryPromptBuilder(sharedPromptFragments).build());
        layers.add(new UntrustedContentPromptBuilder(sharedPromptFragments).build());
        layers.addAll(componentLayers(command.getComponentCode()));
        layers.add(layer(PromptLayerTypeEnumVO.OUTPUT_CONTRACT, "Output Contract",
                outputContractPromptRenderer.renderFor(command.getComponentCode(), command.getContractVersion())));
        layers.add(layer(PromptLayerTypeEnumVO.CURRENT_STATE_VIEW, "Current State View", renderInputView(command.getInputView())));
        layers.add(new OutputOnlyPromptBuilder(sharedPromptFragments).build());

        List<PromptLayer> ordered = layers.stream()
                .sorted(Comparator.comparingInt(layer -> ORDER.getOrDefault(layer.getLayerType(), 999)))
                .toList();
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setOrderNo(i + 1);
        }

        String assembledPrompt = assembleText(ordered);
        PromptEnvelope envelope = PromptEnvelope.builder()
                .componentCode(command.getComponentCode())
                .contractVersion(command.getContractVersion())
                .layers(ordered)
                .assembledPrompt(assembledPrompt)
                .build();
        return PromptAssemblyResult.builder().envelope(envelope).build();
    }

    private void addRolePrompts(List<PromptLayer> layers, PromptAssemblyCommand command) {
        List<String> rolePrompts = promptContentProvider.loadRolePrompts(command.getAgentId(), command.getComponentCode(), command.getPromptVersion());
        if (rolePrompts == null || rolePrompts.isEmpty()) {
            rolePrompts = new StaticPromptContentProvider().loadRolePrompts(command.getAgentId(), command.getComponentCode(), command.getPromptVersion());
        }
        String rolePrompt = String.join("\n\n", rolePrompts);
        layers.add(PromptLayer.builder()
                .layerType(PromptLayerTypeEnumVO.ROLE_PROMPT)
                .heading("Role Prompt")
                .content(rolePrompt)
                .javaOwned(false)
                .build());
    }

    private List<PromptLayer> componentLayers(String componentCode) {
        if (AgentComponentCodeEnumVO.CONTEXT_PLANNER.name().equals(componentCode)) {
            return new ContextPlannerPromptBuilder().build();
        }
        if (AgentComponentCodeEnumVO.MAIN_AGENT.name().equals(componentCode)) {
            return new MainAgentPromptBuilder().build();
        }
        if (AgentComponentCodeEnumVO.RAG_VERIFIER.name().equals(componentCode)) {
            return new RagVerifierPromptBuilder().build();
        }
        if (AgentComponentCodeEnumVO.FINAL_REPAIR.name().equals(componentCode)) {
            return new FinalRepairPromptBuilder().build();
        }
        if (AgentComponentCodeEnumVO.CONTRACT_REPAIR.name().equals(componentCode)) {
            return new ContractRepairPromptBuilder().build();
        }
        if (AgentComponentCodeEnumVO.TURN_SUMMARY.name().equals(componentCode)) {
            return new TurnSummaryPromptBuilder().build();
        }
        if (AgentComponentCodeEnumVO.MEMORY_EXTRACTOR.name().equals(componentCode)) {
            return new MemoryExtractionPromptBuilder().build();
        }
        return List.of();
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }

    private String renderInputView(Object inputView) {
        if (inputView == null) {
            return "{}";
        }
        if (inputView instanceof String text) {
            return text;
        }
        return JSON.toJSONString(inputView);
    }

    private String assembleText(List<PromptLayer> layers) {
        StringBuilder builder = new StringBuilder();
        for (PromptLayer layer : layers) {
            if (layer.getHeading() == null || layer.getHeading().isBlank()) {
                throw new IllegalArgumentException("Prompt layer heading is required.");
            }
            if (layer.getContent() == null || layer.getContent().isBlank()) {
                throw new IllegalArgumentException("Prompt layer content is required.");
            }
            builder.append("## ").append(layer.getHeading()).append("\n");
            builder.append(layer.getContent().trim()).append("\n\n");
        }
        return builder.toString().trim();
    }
}
