package yhx.com.infrastructure.adapter.port;

import org.springframework.stereotype.Component;
import yhx.com.domain.agent.model.valobj.rag.RagHitVO;
import yhx.com.domain.agent.model.valobj.rag.RagRetrievalCommandVO;
import yhx.com.domain.agent.service.rag.runtime.RagRetrieverPort;

import java.util.List;

@Component
public class SpringAiRagRetrieverAdapter implements RagRetrieverPort {

    @Override
    public List<RagHitVO> retrieve(RagRetrievalCommandVO command) {
        throw new UnsupportedOperationException("RAG retriever is not wired yet");
    }
}
