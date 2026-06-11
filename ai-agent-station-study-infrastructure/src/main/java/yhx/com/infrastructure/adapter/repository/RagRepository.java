package yhx.com.infrastructure.adapter.repository;

import yhx.com.domain.agent.adapter.repository.IRagRepository;
import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagFilePayloadEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitFileEntity;
import yhx.com.domain.agent.service.rag.RagAssetIngestionService;
import yhx.com.infrastructure.rag.MyTokenTextSplitter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RKeys;
import org.redisson.api.RList;
import org.redisson.api.RSet;
import org.redisson.api.RType;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RAG repository infrastructure implementation.
 *
 * @author yhx
 */
@Slf4j
@Repository
public class RagRepository implements IRagRepository {

    private static final int BATCH_SIZE = 50;
    private static final String RAG_TAG_KEY = "ragTag";
    private static final long MAX_GIT_FILE_BYTES = 1024L * 1024L;
    private static final Set<String> SKIPPED_GIT_DIRECTORIES = Set.of(
            ".git", ".idea", ".vscode", "node_modules", "target", "build", "dist", "out",
            ".next", ".nuxt", "coverage", ".gradle", "__pycache__", ".pytest_cache",
            "logs", "tmp", "temp"
    );
    private static final Set<String> EXCLUDED_GIT_FILE_NAMES = Set.of(
            "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "gradle.lockfile",
            "poetry.lock", "pipfile.lock"
    );
    private static final Set<String> EXCLUDED_GIT_EXTENSIONS = Set.of(
            ".class", ".jar", ".war", ".ear", ".zip", ".tar", ".gz", ".rar", ".7z",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".svg",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".ttf", ".otf", ".woff", ".woff2", ".eot",
            ".mp3", ".mp4", ".avi", ".mov", ".mkv", ".wav",
            ".map"
    );
    private static final Set<String> TARGET_GIT_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".groovy", ".py", ".js", ".jsx", ".ts", ".tsx", ".vue",
            ".go", ".rs", ".c", ".h", ".cpp", ".hpp", ".cs", ".php", ".rb", ".scala", ".swift",
            ".html", ".css", ".scss", ".less",
            ".json", ".jsonc", ".yml", ".yaml", ".properties", ".toml", ".xml", ".ini",
            ".conf", ".gradle", ".env", ".example",
            ".sh", ".bat", ".cmd", ".ps1", ".sql", ".md", ".txt", ".adoc"
    );
    private static final Set<String> IMPORTANT_GIT_FILE_NAMES = Set.of(
            "dockerfile", "makefile", "jenkinsfile", "procfile", "license", "notice",
            ".gitignore", ".dockerignore", ".editorconfig", ".gitattributes"
    );


    @Resource
    private RedissonClient redissonClient;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private PgVectorStore pgVectorStore;

    @Resource
    private RagAssetIngestionService ragAssetIngestionService;

    @Override
    public Set<String> queryRagTagList() {
        return new LinkedHashSet<>(getOrInitRagTagSet());
    }

    @Override
    public void ingestFiles(RagFileIngestCommandEntity commandEntity) {
        String knowledgeTag = commandEntity.getKnowledgeTag();
        log.info("RAG file ingest start, knowledgeTag: {}", knowledgeTag);

        if (ragAssetIngestionService != null) {
            ragAssetIngestionService.ingestFiles(commandEntity);
            appendTagIfAbsent(knowledgeTag);
            log.info("RAG file ingest completed by asset service, knowledgeTag: {}", knowledgeTag);
            return;
        }

        for (RagFilePayloadEntity filePayloadEntity : commandEntity.getFiles()) {
            if (filePayloadEntity == null || filePayloadEntity.getContent() == null || filePayloadEntity.getContent().length == 0) {
                continue;
            }

            try {
                List<Document> documents = readDocumentsByFileName(filePayloadEntity.getFileName(), filePayloadEntity.getContent());
                writeVectorStoreByBatch(splitDocuments(documents));
            } catch (Exception e) {
                log.error("RAG file ingest failed, fileName: {}", filePayloadEntity.getFileName(), e);
            }
        }

        appendTagIfAbsent(knowledgeTag);
        log.info("RAG file ingest completed, knowledgeTag: {}", knowledgeTag);
    }

    @Override
    public void ingestGitRepository(RagGitIngestCommandEntity commandEntity) throws Exception {
        String repoUrl = commandEntity.getRepoUrl();
        String repoProjectName = extractProjectName(repoUrl);
        Path localPath = Paths.get("./git-cloned-repo-" + System.currentTimeMillis());

        Git git = null;
        try {
            log.info("RAG git ingest start, repoUrl: {}, localPath: {}", repoUrl, localPath.toAbsolutePath());
            git = buildGitClone(commandEntity, localPath);
            List<RagGitFileEntity> files = new ArrayList<>();
            GitIngestStats stats = new GitIngestStats();

            Files.walkFileTree(localPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = lowerFileName(dir);
                    if (SKIPPED_GIT_DIRECTORIES.contains(name)) {
                        stats.skippedByDirectory++;
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    stats.scannedFiles++;
                    GitFileDecision decision = shouldIngestGitFile(file, attrs);
                    if (!decision.accepted()) {
                        stats.recordSkip(decision.reason());
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        String relativePath = localPath.relativize(file).toString().replace("\\", "/");
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        if (content.isBlank()) {
                            stats.skippedEmpty++;
                            return FileVisitResult.CONTINUE;
                        }
                        files.add(RagGitFileEntity.builder()
                                .repositoryName(repoProjectName)
                                .relativePath(relativePath)
                                .language(languageFor(fileName))
                                .content(content)
                                .build());
                        stats.acceptedFiles++;
                    } catch (Exception e) {
                        stats.readFailed++;
                        log.error("RAG git file ingest failed, fileName: {}", fileName, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            ragAssetIngestionService.ingestGitFiles(commandEntity.getUserId(),
                    commandEntity.getSessionId(),
                    repoUrl,
                    commandEntity.getBranchName(),
                    files);
            appendTagIfAbsent(repoProjectName);
            log.info("RAG git ingest completed, repoUrl: {}, scannedFiles: {}, acceptedFiles: {}, skippedByDirectory: {}, skippedByExtension: {}, skippedBySize: {}, skippedByName: {}, skippedGenerated: {}, skippedEmpty: {}, readFailed: {}",
                    repoUrl,
                    stats.scannedFiles,
                    stats.acceptedFiles,
                    stats.skippedByDirectory,
                    stats.skippedByExtension,
                    stats.skippedBySize,
                    stats.skippedByName,
                    stats.skippedGenerated,
                    stats.skippedEmpty,
                    stats.readFailed);
        } finally {
            if (git != null) {
                git.close();
            }
            deleteDirectoryQuietly(localPath);
        }
    }

    private Git buildGitClone(RagGitIngestCommandEntity commandEntity, Path localPath) throws Exception {
        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(commandEntity.getRepoUrl())
                .setDirectory(localPath.toFile());

        String userName = commandEntity.getUserName();
        String token = commandEntity.getToken();
        if (userName != null && !userName.isBlank() && token != null && !token.isBlank()) {
            cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token));
        }

        return cloneCommand.call();
    }

    private List<Document> readDocumentsByFileName(String fileName, byte[] content) {
        if (fileName != null) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                String text = new String(content, StandardCharsets.UTF_8);
                return List.of(new Document(text));
            }
        }

        TikaDocumentReader reader = new TikaDocumentReader(new ByteArrayResource(content));
        return reader.get();
    }

    private List<Document> splitDocuments(List<Document> documents) {
        return documents.stream()
                .flatMap(document -> myTokenTextSplitter.split(document).stream())
                .toList();
    }

    private GitFileDecision shouldIngestGitFile(Path file, BasicFileAttributes attrs) {
        if (file == null || attrs == null || !attrs.isRegularFile()) {
            return GitFileDecision.skip(GitFileSkipReason.EXTENSION);
        }

        String lowerName = lowerFileName(file);
        String extension = extensionOf(lowerName);
        if (EXCLUDED_GIT_FILE_NAMES.contains(lowerName)) {
            return GitFileDecision.skip(GitFileSkipReason.NAME);
        }
        if (lowerName.endsWith(".min.js") || EXCLUDED_GIT_EXTENSIONS.contains(extension)) {
            return GitFileDecision.skip(GitFileSkipReason.GENERATED);
        }
        if (attrs.size() > MAX_GIT_FILE_BYTES) {
            return GitFileDecision.skip(GitFileSkipReason.SIZE);
        }
        if (IMPORTANT_GIT_FILE_NAMES.contains(lowerName) || TARGET_GIT_EXTENSIONS.contains(extension)) {
            return GitFileDecision.accept();
        }
        return GitFileDecision.skip(GitFileSkipReason.EXTENSION);
    }

    private String lowerFileName(Path path) {
        return path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
    }

    private String extensionOf(String lowerFileName) {
        if (lowerFileName == null || lowerFileName.isBlank()) {
            return "";
        }
        int index = lowerFileName.lastIndexOf('.');
        if (index > 0) {
            return lowerFileName.substring(index);
        }
        if (index == 0 && lowerFileName.indexOf('.', 1) < 0) {
            return lowerFileName;
        }
        return "";
    }

    private void appendTagIfAbsent(String knowledgeTag) {
        RSet<String> tags = getOrInitRagTagSet();
        if (!tags.contains(knowledgeTag)) {
            tags.add(knowledgeTag);
        }
    }

    private RSet<String> getOrInitRagTagSet() {
        RKeys keys = redissonClient.getKeys();
        RType type = keys.getType(RAG_TAG_KEY);
        if (type == RType.LIST) {
            migrateLegacyTagListToSet(keys);
        } else if (type != null && type != RType.SET) {
            log.warn("RAG tag key type mismatch, key: {}, type: {}, reset to set", RAG_TAG_KEY, type);
            keys.delete(RAG_TAG_KEY);
        }
        return redissonClient.getSet(RAG_TAG_KEY);
    }

    private void migrateLegacyTagListToSet(RKeys keys) {
        RList<String> legacyTagList = redissonClient.getList(RAG_TAG_KEY);
        Set<String> legacyTags = new LinkedHashSet<>(legacyTagList.readAll());

        keys.delete(RAG_TAG_KEY);
        if (!legacyTags.isEmpty()) {
            redissonClient.getSet(RAG_TAG_KEY).addAll(legacyTags);
        }

        log.warn("RAG tag key migrated from LIST to SET, key: {}, size: {}", RAG_TAG_KEY, legacyTags.size());
    }

    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String languageFor(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) {
            return "kotlin";
        }
        if (lower.endsWith(".groovy") || lower.endsWith(".gradle")) {
            return "groovy";
        }
        if (lower.endsWith(".py")) {
            return "python";
        }
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) {
            return "javascript";
        }
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) {
            return "typescript";
        }
        if (lower.endsWith(".vue")) {
            return "vue";
        }
        if (lower.endsWith(".go")) {
            return "go";
        }
        if (lower.endsWith(".rs")) {
            return "rust";
        }
        if (lower.endsWith(".c") || lower.endsWith(".h")) {
            return "c";
        }
        if (lower.endsWith(".cpp") || lower.endsWith(".hpp")) {
            return "cpp";
        }
        if (lower.endsWith(".cs")) {
            return "csharp";
        }
        if (lower.endsWith(".php")) {
            return "php";
        }
        if (lower.endsWith(".rb")) {
            return "ruby";
        }
        if (lower.endsWith(".scala")) {
            return "scala";
        }
        if (lower.endsWith(".swift")) {
            return "swift";
        }
        if (lower.endsWith(".html")) {
            return "html";
        }
        if (lower.endsWith(".css") || lower.endsWith(".scss") || lower.endsWith(".less")) {
            return "css";
        }
        if (lower.endsWith(".json") || lower.endsWith(".jsonc")) {
            return "json";
        }
        if (lower.endsWith(".md")) {
            return "markdown";
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return "yaml";
        }
        if (lower.endsWith(".xml")) {
            return "xml";
        }
        if (lower.endsWith(".sql")) {
            return "sql";
        }
        if (lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".cmd") || lower.endsWith(".ps1")) {
            return "shell";
        }
        return "text";
    }

    private void writeVectorStoreByBatch(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (int start = 0; start < documents.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, documents.size());
            pgVectorStore.write(documents.subList(start, end));
        }
    }

    private void deleteDirectoryQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("cleanup cloned repo failed, path: {}, error: {}", path.toAbsolutePath(), e.getMessage());
        }
    }

    private enum GitFileSkipReason {
        EXTENSION,
        SIZE,
        NAME,
        GENERATED
    }

    private record GitFileDecision(boolean accepted, GitFileSkipReason reason) {

        static GitFileDecision accept() {
            return new GitFileDecision(true, null);
        }

        static GitFileDecision skip(GitFileSkipReason reason) {
            return new GitFileDecision(false, reason);
        }
    }

    private static class GitIngestStats {

        private int scannedFiles;
        private int acceptedFiles;
        private int skippedByDirectory;
        private int skippedByExtension;
        private int skippedBySize;
        private int skippedByName;
        private int skippedGenerated;
        private int skippedEmpty;
        private int readFailed;

        private void recordSkip(GitFileSkipReason reason) {
            if (reason == GitFileSkipReason.SIZE) {
                skippedBySize++;
            } else if (reason == GitFileSkipReason.NAME) {
                skippedByName++;
            } else if (reason == GitFileSkipReason.GENERATED) {
                skippedGenerated++;
            } else {
                skippedByExtension++;
            }
        }
    }

}

