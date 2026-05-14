package yhx.com.infrastructure.adapter.port;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.rag.RagRetrievalCommandVO;

import java.util.Map;

public class SpringAiRagRetrieverAdapterTest {

    @Test
    public void knowledge_name_builds_legacy_knowledge_filter_expression() {
        SpringAiRagRetrieverAdapter adapter = new SpringAiRagRetrieverAdapter(null);

        String filterExpression = adapter.resolveFilterExpression(RagRetrievalCommandVO.builder()
                .knowledgeName("article-prompt-words")
                .build());

        Assert.assertEquals("knowledge == 'article-prompt-words'", filterExpression);
    }

    @Test
    public void explicit_filter_expression_overrides_knowledge_name() {
        SpringAiRagRetrieverAdapter adapter = new SpringAiRagRetrieverAdapter(null);

        String filterExpression = adapter.resolveFilterExpression(RagRetrievalCommandVO.builder()
                .knowledgeName("article-prompt-words")
                .runtimeFilters(Map.of("filterExpression", "knowledge == 'custom'"))
                .build());

        Assert.assertEquals("knowledge == 'custom'", filterExpression);
    }

    @Test
    public void knowledge_name_filter_escapes_quotes() {
        SpringAiRagRetrieverAdapter adapter = new SpringAiRagRetrieverAdapter(null);

        String filterExpression = adapter.resolveFilterExpression(RagRetrievalCommandVO.builder()
                .knowledgeName("owner's-notes")
                .build());

        Assert.assertEquals("knowledge == 'owner\\'s-notes'", filterExpression);
    }
}
