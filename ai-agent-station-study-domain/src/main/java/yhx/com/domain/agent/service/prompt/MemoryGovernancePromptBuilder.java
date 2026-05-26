package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.List;

public class MemoryGovernancePromptBuilder {

    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You are MemoryGovernance, a bounded Memory GC component inside AutoAgent.
                        You inspect existing long-term memories and preferences for one session.
                        You do not answer the user, create new memories, or modify runtime state directly.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Review the provided memories.
                        Use KEEP when a memory is still useful and not conflicting.
                        Use DISABLE when a memory is wrong, obsolete, duplicate noise, or not actually long-term.
                        Use SUPERSEDE when one memory is replaced by a newer memory and targetMemoryId identifies the newer active memory.
                        Prefer NOOP/KEEP when evidence is weak.
                        """),
                layer(PromptLayerTypeEnumVO.DECISION_POLICY, "Decision Policy", """
                        Only reference memoryId values present in the input.
                        Do not invent ids.
                        Be conservative: disabling a useful memory is worse than leaving it for a later governance pass.
                        """),
                layer(PromptLayerTypeEnumVO.ANTI_EXAMPLES, "Anti Examples", """
                        Do not output actions for unknown memory ids.
                        Do not create user-facing explanations.
                        Do not merge unrelated memories just because they share keywords.
                        """)
        );
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }
}
