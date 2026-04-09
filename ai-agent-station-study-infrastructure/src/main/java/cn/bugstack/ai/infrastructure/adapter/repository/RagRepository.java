package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.agent.adapter.repository.IRagRepository;
import cn.bugstack.ai.domain.agent.model.entity.RagFileIngestCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.RagFilePayloadEntity;
import cn.bugstack.ai.domain.agent.model.entity.RagGitIngestCommandEntity;
import cn.bugstack.ai.infrastructure.rag.MyTokenTextSplitter;
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
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private static final Set<String> TARGET_EXTENSIONS = Set.of(
            ".py", ".java", ".go", ".cpp", ".c",
            ".js", ".ts", ".vue",
            ".yml", ".yaml", ".properties", ".toml", ".xml", ".conf",
            ".md", ".txt"
    );


    @Resource
    private PgVectorStore pgVectorStore;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Override
    public Set<String> queryRagTagList() {
        return new LinkedHashSet<>(getOrInitRagTagSet());
    }

    @Override
    public void ingestFiles(RagFileIngestCommandEntity commandEntity) {
        String knowledgeTag = commandEntity.getKnowledgeTag();
        log.info("RAG file ingest start, knowledgeTag: {}", knowledgeTag);

        for (RagFilePayloadEntity filePayloadEntity : commandEntity.getFiles()) {
            if (filePayloadEntity == null || filePayloadEntity.getContent() == null || filePayloadEntity.getContent().length == 0) {
                continue;
            }

            try {
                List<Document> documents = readDocumentsByFileName(filePayloadEntity.getFileName(), filePayloadEntity.getContent());
                List<Document> chunks = splitDocuments(documents);
                chunks.forEach(doc -> doc.getMetadata().put("knowledge", knowledgeTag));
                writeVectorStoreByBatch(chunks);
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

            Files.walkFileTree(localPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (name.startsWith(".") || "target".equals(name) || "node_modules".equals(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    if (!isTargetFile(fileName)) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        List<Document> documents = readDocumentsByPath(file);
                        List<Document> chunks = splitDocuments(documents);
                        chunks.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                        writeVectorStoreByBatch(chunks);
                    } catch (Exception e) {
                        log.error("RAG git file ingest failed, fileName: {}", fileName, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            appendTagIfAbsent(repoProjectName);
            log.info("RAG git ingest completed, repoUrl: {}", repoUrl);
        } finally {
            if (git != null) {
                git.getRepository().close();
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

    private List<Document> readDocumentsByPath(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".txt") || fileName.endsWith(".md")) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                return List.of(new Document(text));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
        return reader.get();
    }

    private List<Document> splitDocuments(List<Document> documents) {
        List<Document> chunks = new ArrayList<>();
        for (Document document : documents) {
            chunks.addAll(myTokenTextSplitter.split(document));
        }
        return chunks;
    }

    private void writeVectorStoreByBatch(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, chunks.size());
            pgVectorStore.accept(chunks.subList(i, end));
        }
    }

    private boolean isTargetFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : TARGET_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
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

}
