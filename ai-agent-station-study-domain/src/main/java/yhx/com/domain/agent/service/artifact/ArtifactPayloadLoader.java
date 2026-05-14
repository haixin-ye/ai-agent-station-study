package yhx.com.domain.agent.service.artifact;

import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ArtifactChunkVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedArtifactContentVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.service.context.ContextTokenEstimator;

import java.util.ArrayList;
import java.util.List;

public class ArtifactPayloadLoader {

    private final IPayloadRepository payloadRepository;
    private final ContextTokenEstimator tokenEstimator;

    public ArtifactPayloadLoader(IPayloadRepository payloadRepository, ContextTokenEstimator tokenEstimator) {
        this.payloadRepository = payloadRepository;
        this.tokenEstimator = tokenEstimator;
    }

    public MaterializedArtifactContentVO load(ArtifactCandidateVO artifact, ContextLevelEnumVO level, int maxInlineChars) {
        if (artifact == null) {
            return null;
        }
        if (level == ContextLevelEnumVO.METADATA_ONLY || level == ContextLevelEnumVO.SUMMARY_ONLY) {
            return base(artifact, level, null, List.of(), false);
        }
        String content = payloadRepository.findContent(artifact.getContentRef()).orElse(null);
        if (content == null) {
            return base(artifact, ContextLevelEnumVO.SUMMARY_ONLY, null, List.of(), false);
        }
        if (level == ContextLevelEnumVO.FULL_TEXT && content.length() <= maxInlineChars) {
            return base(artifact, level, content, List.of(), false);
        }
        if (level == ContextLevelEnumVO.SUMMARY_PLUS_SNIPPET) {
            return base(artifact, level, content.substring(0, Math.min(maxInlineChars, content.length())), List.of(), content.length() > maxInlineChars);
        }
        List<ArtifactChunkVO> chunks = chunk(content, Math.max(200, maxInlineChars / 2), 3);
        return base(artifact, ContextLevelEnumVO.CHUNKED_CONTEXT, null, chunks, true);
    }

    private MaterializedArtifactContentVO base(ArtifactCandidateVO artifact,
                                              ContextLevelEnumVO level,
                                              String content,
                                              List<ArtifactChunkVO> chunks,
                                              boolean truncated) {
        return MaterializedArtifactContentVO.builder()
                .artifactId(artifact.getArtifactId())
                .contextLevel(level)
                .title(artifact.getTitle())
                .summary(artifact.getSummary())
                .contentRef(artifact.getContentRef())
                .content(content)
                .chunks(chunks)
                .tokenCount(tokenEstimator.estimateTextTokens(content == null ? artifact.getSummary() : content))
                .truncated(truncated)
                .build();
    }

    private List<ArtifactChunkVO> chunk(String content, int chunkChars, int maxChunks) {
        List<ArtifactChunkVO> chunks = new ArrayList<>();
        int index = 0;
        for (int offset = 0; offset < content.length() && chunks.size() < maxChunks; offset += chunkChars) {
            String chunk = content.substring(offset, Math.min(offset + chunkChars, content.length()));
            chunks.add(ArtifactChunkVO.builder()
                    .index(index++)
                    .content(chunk)
                    .tokenCount(tokenEstimator.estimateTextTokens(chunk))
                    .build());
        }
        return chunks;
    }
}
