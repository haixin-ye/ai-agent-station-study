package yhx.com.domain.agent.service.rag.runtime;

import yhx.com.domain.agent.model.valobj.rag.RagHitVO;
import yhx.com.domain.agent.model.valobj.rag.RagRetrievalCommandVO;

import java.util.List;

public interface RagRetrieverPort {

    List<RagHitVO> retrieve(RagRetrievalCommandVO command);
}
