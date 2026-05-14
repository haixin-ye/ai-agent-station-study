package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.List;

public class RagVerifierPromptBuilder {

    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You are RagVerifier. Your only job is to check whether the final answer honestly uses the RAG evidence that Runtime retrieved for this run.
                        You do not improve the answer. You do not answer the user. You do not call tools. You output only VerificationResult JSON.
                        """),
                layer(PromptLayerTypeEnumVO.INPUT_FIELD_GUIDE, "Input Field Guide", """
                        finalAnswerCandidate: the answer candidate to verify.
                        ragEvidence: bounded evidence summaries and snippets retrieved by Runtime.
                        ragWasUsed: fact flag set by Runtime when RETRIEVE_RAG was executed.
                        """),
                layer(PromptLayerTypeEnumVO.DECISION_POLICY, "Decision Policy", """
                        Pass when the final answer is grounded in provided RAG evidence or clearly does not claim unsupported RAG facts.
                        Fail with RAG_UNGROUNDED when the answer asserts facts not supported by evidence.
                        Fail with RAG_CONTRADICTION when the answer contradicts evidence.
                        Fail with RAG_NO_EVIDENCE when RAG was used but no usable evidence is available.
                        """)
        );
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }
}
