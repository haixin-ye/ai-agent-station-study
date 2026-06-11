package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.adapter.repository.IRunDiagnosticRepository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class RunDiagnosticRecorder {

    private final IRunDiagnosticRepository repository;

    public RunDiagnosticRecorder(IRunDiagnosticRepository repository) {
        this.repository = repository;
    }

    public void record(String runId, String category, String event, Map<String, Object> details) {
        if (repository == null || runId == null || runId.isBlank()) {
            return;
        }
        Map<String, Object> entry = base(category, event);
        if (details != null && !details.isEmpty()) {
            entry.putAll(details);
        }
        repository.append(runId, entry);
    }

    public void error(String runId, String category, String event, Throwable error, Map<String, Object> details) {
        Map<String, Object> entry = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
        entry.put("level", "ERROR");
        entry.put("errorType", error == null ? null : error.getClass().getName());
        entry.put("errorMessage", error == null ? null : error.getMessage());
        entry.put("stackTrace", stackTrace(error));
        record(runId, category, event, entry);
    }

    private Map<String, Object> base(String category, String event) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("recordedAt", LocalDateTime.now().toString());
        entry.put("category", category);
        entry.put("event", event);
        entry.put("level", "INFO");
        return entry;
    }

    private String stackTrace(Throwable error) {
        if (error == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
