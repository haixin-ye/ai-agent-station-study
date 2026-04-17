package cn.bugstack.ai.domain.agent.service.execute.auto.support;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Persist per-session node trace content so frontend-visible thought logs
 * can be inspected from backend files.
 */
public final class AutoAgentTraceLogSupport {

    private static final Path TRACE_DIR = Path.of("data", "log", "node-trace");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter LINE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AutoAgentTraceLogSupport() {
    }

    public static String initializeTraceLog(String sessionId, String question) {
        try {
            Files.createDirectories(TRACE_DIR);
            String safeQuestion = sanitizeFileName(question);
            if (safeQuestion.isBlank()) {
                safeQuestion = "auto-agent";
            }
            String safeSession = sanitizeFileName(sessionId);
            String fileName = FILE_TS.format(LocalDateTime.now())
                    + "_"
                    + safeQuestion
                    + (safeSession.isBlank() ? "" : "_" + safeSession)
                    + ".log";
            Path traceFile = TRACE_DIR.resolve(fileName);
            String header = """
                    # AutoAgent Node Trace
                    sessionId: %s
                    question: %s
                    createdAt: %s

                    """.formatted(
                    safeValue(sessionId),
                    safeValue(question),
                    LINE_TS.format(LocalDateTime.now())
            );
            Files.writeString(traceFile, header, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return traceFile.toAbsolutePath().toString();
        } catch (IOException e) {
            return "";
        }
    }

    public static void appendEvent(String traceLogPath, AutoAgentExecuteResultEntity result) {
        if (traceLogPath == null || traceLogPath.isBlank() || result == null) {
            return;
        }
        try {
            Path target = Path.of(traceLogPath);
            Files.createDirectories(target.getParent());
            String block = """
                    [%s] type=%s subType=%s step=%s completed=%s timestamp=%s sessionId=%s
                    %s

                    """.formatted(
                    LINE_TS.format(LocalDateTime.now()),
                    safeValue(result.getType()),
                    safeValue(result.getSubType()),
                    result.getStep() == null ? "" : result.getStep(),
                    result.getCompleted() == null ? "" : result.getCompleted(),
                    result.getTimestamp() == null ? "" : result.getTimestamp(),
                    safeValue(result.getSessionId()),
                    safeValue(result.getContent())
            );
            Files.writeString(target, block, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignore) {
        }
    }

    private static String sanitizeFileName(String value) {
        String raw = safeValue(value).trim();
        if (raw.length() > 48) {
            raw = raw.substring(0, 48);
        }
        return raw.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }
}
