package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.List;

public class RagAssetAnalyzerPromptBuilder {

    public List<PromptLayer> build() {
        return List.of(PromptLayer.builder()
                .layerType(PromptLayerTypeEnumVO.TASK_PROCEDURE)
                .heading("RAG Asset Analysis Procedure")
                .content("""
                        Analyze the supplied sourceName, sourceType, contentKind, and content for later retrieval.
                        Produce a concise factual title and summary, then write self-contained retrievalText containing
                        the important subject, entities, code symbols, and searchable aliases present in the source.
                        Detect the source language and list important symbols or domain terms in keySymbols. Base every
                        field on the supplied content.
                        """)
                .javaOwned(true)
                .build());
    }
}
