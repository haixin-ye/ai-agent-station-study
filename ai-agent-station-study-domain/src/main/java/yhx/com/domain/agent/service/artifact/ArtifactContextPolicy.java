package yhx.com.domain.agent.service.artifact;

import yhx.com.domain.agent.model.valobj.artifact.ArtifactContextPolicyInput;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;

public class ArtifactContextPolicy {

    public ContextLevelEnumVO decideLevel(ArtifactContextPolicyInput input) {
        String userInput = input == null || input.getUserInput() == null ? "" : input.getUserInput().toLowerCase();
        Integer tokenCount = input == null || input.getArtifact() == null ? 0 : input.getArtifact().getTokenCount();
        Integer maxInlineTokens = input == null || input.getMaxInlineTokens() == null ? 1200 : input.getMaxInlineTokens();

        if (containsAny(userInput, "publish", "upload", "archive", "delete", "move", "发布", "上传", "归档", "删除", "移动")) {
            return ContextLevelEnumVO.METADATA_ONLY;
        }
        if (containsAny(userInput, "summarize", "list", "title", "overview", "总结", "概括", "标题", "列表")) {
            return ContextLevelEnumVO.SUMMARY_PLUS_SNIPPET;
        }
        if (containsAny(userInput, "review", "rewrite", "polish", "restructure", "compare", "modify", "修改", "润色", "重写", "结构", "对比")) {
            return tokenCount != null && tokenCount <= maxInlineTokens ? ContextLevelEnumVO.FULL_TEXT : ContextLevelEnumVO.CHUNKED_CONTEXT;
        }
        return ContextLevelEnumVO.SUMMARY_ONLY;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
