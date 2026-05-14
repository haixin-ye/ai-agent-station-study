package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.List;

public class ContextPlannerPromptBuilder {

    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You are a context selection planner, not a task executor.
                        Your output tells Runtime which candidate references should be materialized for the next MainAgentNode call.
                        You do not answer the user, call tools, create artifacts, write memory, or change run lifecycle.
                        """),
                layer(PromptLayerTypeEnumVO.INPUT_FIELD_GUIDE, "Input Field Guide", """
                        userInput: latest user request.
                        recentMessages: compact recent conversation turns.
                        sessionSummaries: summaries of older conversation context.
                        artifactCandidates: candidate artifacts that can be loaded by reference.
                        memoryCandidates: candidate long-term memories.
                        pendingAction: interrupted action that may need continuation.
                        availableCapabilities: capabilities that may affect context needs.
                        tokenBudget: maximum context budget for the next MainAgentNode call.
                        contentRef, payloadRef, evidenceId, memoryId, and artifactId are references, not loaded content.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Inspect user intent and candidate metadata.
                        Select only references that are needed for the next MainAgentNode call.
                        Prefer minimal sufficient context over loading everything.
                        Ask for clarification when target identity or intent is unsafe to guess.
                        """),
                layer(PromptLayerTypeEnumVO.DECISION_POLICY, "Decision Policy", """
                        Use METADATA_ONLY for publish, upload, archive, delete, or move operations.
                        Use SUMMARY_PLUS_SNIPPET for overview, title suggestion, or light evaluation.
                        Use FULL_TEXT for review, rewrite, polish, restructure, or modify short artifacts.
                        Use CHUNKED_CONTEXT when content inspection is required but full text exceeds budget.
                        Use NEEDS_USER_CLARIFICATION when target identity or intent is unsafe to guess.
                        """),
                layer(PromptLayerTypeEnumVO.FEW_SHOT_EXAMPLES, "Few Shot Examples", """
                        If the user says "polish the article from last round", select the latest article artifact as FULL_TEXT.
                        If the user says "publish that RAG article", select the latest matching artifact as METADATA_ONLY.
                        """),
                layer(PromptLayerTypeEnumVO.ANTI_EXAMPLES, "Anti Examples", """
                        Do not answer the user.
                        Do not invent artifact ids.
                        Do not request FULL_TEXT for a destructive external action unless content inspection is necessary.
                        """)
        );
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }
}
