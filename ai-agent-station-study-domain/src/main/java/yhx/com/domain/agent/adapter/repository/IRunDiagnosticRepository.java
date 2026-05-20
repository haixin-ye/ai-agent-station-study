package yhx.com.domain.agent.adapter.repository;

import java.util.Map;

public interface IRunDiagnosticRepository {

    void append(String runId, Map<String, Object> entry);
}
