package yhx.com.test.domain.agent.migration;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

class MigrationTestSupport {

    private MigrationTestSupport() {
    }

    static Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (Files.exists(current.resolve("ai-agent-station-study-trigger"))) {
            return current;
        }
        return current.getParent();
    }

    static boolean hasField(Class<?> type, String fieldName) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    static boolean containsAny(Path root, String... terms) throws Exception {
        if (!Files.exists(root)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(MigrationTestSupport::isJavaFile).toList()) {
                String content = Files.readString(path);
                for (String term : terms) {
                    if (content.contains(term)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isJavaFile(Path path) {
        return path.getFileName().toString().endsWith(".java");
    }
}

