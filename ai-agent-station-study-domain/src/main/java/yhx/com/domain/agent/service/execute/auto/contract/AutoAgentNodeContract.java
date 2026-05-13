package yhx.com.domain.agent.service.execute.auto.contract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract metadata shared by one auto-agent node.
 */
public interface AutoAgentNodeContract {

    String nodeId();

    String contractVersion();

    List<String> primaryTruthSources();

    default Map<String, Object> meta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("nodeId", nodeId());
        meta.put("contractVersion", contractVersion());
        meta.put("primaryTruthSources", primaryTruthSources());
        return meta;
    }
}
