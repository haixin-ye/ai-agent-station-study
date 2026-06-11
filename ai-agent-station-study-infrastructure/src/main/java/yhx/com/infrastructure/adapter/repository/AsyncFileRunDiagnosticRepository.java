package yhx.com.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.adapter.repository.IRunDiagnosticRepository;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class AsyncFileRunDiagnosticRepository implements IRunDiagnosticRepository, Closeable {

    private static final DiagnosticTask POISON = new DiagnosticTask("__poison__", Map.of());
    private static final DateTimeFormatter DIRECTORY_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path baseDirectory;
    private final BlockingQueue<DiagnosticTask> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final Map<String, DiagnosticFileTarget> fileTargetsByRunId = new ConcurrentHashMap<>();
    private final Thread worker;

    public AsyncFileRunDiagnosticRepository(Path baseDirectory, int queueCapacity) {
        this.baseDirectory = baseDirectory == null ? Path.of("data", "log", "agent-run-trace") : baseDirectory;
        this.queue = new LinkedBlockingQueue<>(queueCapacity <= 0 ? 8192 : queueCapacity);
        this.worker = new Thread(this::drainLoop, "auto-agent-diagnostic-writer");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public void append(String runId, Map<String, Object> entry) {
        if (runId == null || runId.isBlank() || entry == null) {
            return;
        }
        Map<String, Object> enriched = new LinkedHashMap<>(entry);
        enriched.putIfAbsent("recordedAt", LocalDateTime.now().toString());
        enriched.put("runId", runId);
        enriched.put("diagnosticSeq", sequence.incrementAndGet());
        DiagnosticTask task = new DiagnosticTask(runId, enriched);
        if (queue.offer(task)) {
            return;
        }
        if (isImportant(enriched)) {
            queue.poll();
            if (!queue.offer(task)) {
                dropped.incrementAndGet();
            }
        } else {
            dropped.incrementAndGet();
        }
    }

    @Override
    public void close() {
        running.set(false);
        queue.offer(POISON);
        try {
            worker.join(3000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void drainLoop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                DiagnosticTask task = queue.poll(1L, TimeUnit.SECONDS);
                if (task == null || task == POISON) {
                    continue;
                }
                write(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("[AutoAgent][diagnostic-write-failed] message={}", e.getMessage(), e);
            }
        }
    }

    private void write(DiagnosticTask task) throws IOException {
        DiagnosticFileTarget target = fileTargetsByRunId.computeIfAbsent(task.runId(), this::newFileTarget);
        Path directory = baseDirectory.resolve(target.directoryName());
        Files.createDirectories(directory);
        Path file = directory.resolve(target.fileName());
        Files.writeString(file,
                JSON.toJSONString(task.entry()) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
        long droppedCount = dropped.getAndSet(0L);
        if (droppedCount > 0) {
            Map<String, Object> droppedEntry = new LinkedHashMap<>();
            droppedEntry.put("recordedAt", LocalDateTime.now().toString());
            droppedEntry.put("runId", task.runId());
            droppedEntry.put("category", "DIAGNOSTIC");
            droppedEntry.put("event", "DROPPED_RECORDS");
            droppedEntry.put("level", "WARN");
            droppedEntry.put("droppedCount", droppedCount);
            Files.writeString(file,
                    JSON.toJSONString(droppedEntry) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        }
    }

    private DiagnosticFileTarget newFileTarget(String runId) {
        LocalDateTime now = LocalDateTime.now();
        String directoryName = DIRECTORY_DATE_FORMATTER.format(now.toLocalDate());
        String fileName = FILE_TIME_FORMATTER.format(now) + "_" + shortRunSuffix(runId) + ".jsonl";
        return new DiagnosticFileTarget(directoryName, fileName);
    }

    private boolean isImportant(Map<String, Object> entry) {
        String level = String.valueOf(entry.getOrDefault("level", ""));
        String event = String.valueOf(entry.getOrDefault("event", ""));
        String eventType = String.valueOf(entry.getOrDefault("eventType", ""));
        return "ERROR".equalsIgnoreCase(level)
                || event.contains("FAILED")
                || event.contains("ERROR")
                || "RUN_FAILED".equals(eventType)
                || "FINAL_READY".equals(eventType)
                || "ASK_USER".equals(eventType)
                || "USER_INPUT".equals(event);
    }

    private String safeFileName(String value) {
        String normalized = value == null || value.isBlank() ? "run" : value;
        return normalized.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String shortRunSuffix(String runId) {
        String normalized = safeFileName(runId).replaceAll("[^A-Za-z0-9]", "");
        if (normalized.isBlank()) {
            return "run";
        }
        return normalized.length() <= 12 ? normalized : normalized.substring(normalized.length() - 12);
    }

    private record DiagnosticFileTarget(String directoryName, String fileName) {
    }

    private record DiagnosticTask(String runId, Map<String, Object> entry) {
    }
}
