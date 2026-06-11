package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.artifact.ArtifactContextPolicyInput;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.service.artifact.ArtifactContextPolicy;

public class ArtifactContextPolicyTest {

    private final ArtifactContextPolicy policy = new ArtifactContextPolicy();

    @Test
    public void publish_intent_uses_metadata_only() {
        Assert.assertEquals(ContextLevelEnumVO.METADATA_ONLY, policy.decideLevel(input("publish this article", 500)));
    }

    @Test
    public void rewrite_short_artifact_uses_full_text() {
        Assert.assertEquals(ContextLevelEnumVO.FULL_TEXT, policy.decideLevel(input("rewrite this article", 500)));
    }

    @Test
    public void rewrite_long_artifact_uses_chunked_context() {
        Assert.assertEquals(ContextLevelEnumVO.CHUNKED_CONTEXT, policy.decideLevel(input("rewrite this article", 5000)));
    }

    @Test
    public void summary_request_uses_summary_plus_snippet() {
        Assert.assertEquals(ContextLevelEnumVO.SUMMARY_PLUS_SNIPPET, policy.decideLevel(input("summarize this article", 500)));
    }

    private ArtifactContextPolicyInput input(String userInput, int tokenCount) {
        return ArtifactContextPolicyInput.builder()
                .userInput(userInput)
                .artifact(ArtifactCandidateVO.builder().tokenCount(tokenCount).build())
                .maxInlineTokens(1000)
                .build();
    }
}
