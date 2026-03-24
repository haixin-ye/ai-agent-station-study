package cn.bugstack.ai.trigger.http;


import cn.bugstack.ai.api.IRAGService;
import cn.bugstack.ai.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {


    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Lazy
    @Autowired
    private PgVectorStore pgVectorStore;
    @Resource
    private RedissonClient redissonClient;

    @RequestMapping(value = "query_rag_tag_list", method = RequestMethod.GET)
    @Override
    public Response<List<String>> queryRagTagList() {
        List<String> elements = redissonClient.getList("ragTag");
        return Response.<List<String>>builder()
                .code("0000")
                .info("调用成功")
                .data(elements)
                .build();
    }

    @RequestMapping(value = "file/upload", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam("ragTag") String ragTag, @RequestParam("file") List<MultipartFile> files) {
        log.info("上传知识库开始 {}", ragTag);

        for (MultipartFile file : files) {
            try {
                String filename = file.getOriginalFilename();
                List<Document> documents;

                // 核心修复：针对 txt 和 md 等纯文本，绕过 Tika，原生强力读取
                if (filename != null && (filename.toLowerCase().endsWith(".txt") || filename.toLowerCase().endsWith(".md"))) {
                    // 1. 直接获取全部字节流转为 UTF-8 字符串，绝不截断
                    String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                    log.info("成功读取纯文本文件: {}，总字符长度: {}", filename, content.length());

                    // 2. 封装为 Spring AI 的 Document 对象
                    documents = List.of(new Document(content));
                } else {
                    // 其他富文本（PDF/Word 等）依然交给 Tika 处理
                    TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
                    documents = documentReader.get();
                }

                log.info("{} 切分前，原始文档块数量: {}", filename, documents.size());

                // 执行切分
                List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                log.info("{} 切分后，即将入库的向量片段(Chunk)数量: {}", filename, documentSplitterList.size());

                // 写入 metadata 标签
                documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
                documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));

                // 存入向量数据库
                pgVectorStore.accept(documentSplitterList);

                // 更新 Redis 标签列表
                RList<String> elements = redissonClient.getList("ragTag");
                if (!elements.contains(ragTag)) {
                    elements.add(ragTag);
                }
            } catch (Exception e) {
                log.error("处理上传文件失败: {}", file.getOriginalFilename(), e);
                // 建议这里可以根据需求决定是 continue 还是直接 return error
            }
        }

        log.info("上传知识库完成 {}", ragTag);
        return Response.<String>builder().code("0000").info("调用成功").build();
    }

    @RequestMapping(value = "analyze_git_repository", method = RequestMethod.POST)
    @Override
    public Response<String> analyzeGitRepository(@RequestParam("repoUrl") String repoUrl, @RequestParam("userName") String userName, @RequestParam("token") String token) throws Exception {
        String localPath = "./git-cloned-repo";
        String repoProjectName = extractProjectName(repoUrl);
        log.info("克隆路径：{}", new File(localPath).getAbsolutePath());

        FileUtils.deleteDirectory(new File(localPath));

        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                .call();

        Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<Path>() {

            // 1. 过滤掉不需要的隐藏目录（如 .git）
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName().toString().startsWith(".") || dir.getFileName().toString().equals("target") || dir.getFileName().toString().equals("node_modules")) {
                    log.info("跳过目录: {}", dir.getFileName());
                    return FileVisitResult.SKIP_SUBTREE; // 直接跳过整个目录，不往下遍历
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString().toLowerCase();

                // 核心修改：白名单模式！只允许我们关心的代码和配置文件通过
                boolean isTargetFile =
                        // 后端/算法代码
                        fileName.endsWith(".py") ||
                                fileName.endsWith(".java") ||
                                fileName.endsWith(".go") ||
                                fileName.endsWith(".cpp") ||
                                fileName.endsWith(".c") ||
                                // 前端代码
                                fileName.endsWith(".js") ||
                                fileName.endsWith(".ts") ||
                                fileName.endsWith(".vue") ||
                                // 配置文件
                                fileName.endsWith(".yml") ||
                                fileName.endsWith(".yaml") ||
                                fileName.endsWith(".properties") ||
                                fileName.endsWith(".toml") ||
                                fileName.endsWith(".xml") ||
                                fileName.endsWith(".conf") ||
                                // 文档说明
                                fileName.endsWith(".md") ||
                                fileName.endsWith(".txt");

                // 如果不是白名单里的文件，直接跳过（彻底屏蔽 JSON、图片、二进制包等）
                if (!isTargetFile) {
                    log.info("跳过非目标解析文件: {}", fileName);
                    return FileVisitResult.CONTINUE;
                }

                try {
                    log.info("{} 遍历解析路径，准备上传知识库: {}", repoProjectName, fileName);

                    // ... (下面保持原有的 Tika 读取、TokenSplitter 切分和分批存入数据库的逻辑)
                    TikaDocumentReader documentReader = new TikaDocumentReader(new PathResource(file));
                    List<Document> documents = documentReader.get();

                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);
                    log.info("TextSplitter - {} 被切分成了 {} 个 chunks.", fileName, documentSplitterList.size());

                    if (documentSplitterList == null || documentSplitterList.isEmpty()) {
                        return FileVisitResult.CONTINUE;
                    }

                    documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));

                    // 分批写入
                    int batchSize = 50;
                    for (int i = 0; i < documentSplitterList.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, documentSplitterList.size());
                        List<Document> batchList = documentSplitterList.subList(i, end);
                        pgVectorStore.accept(batchList);
                    }

                } catch (Exception e) {
                    log.error("遍历解析路径，上传知识库失败: {} - 原因: {}", file.getFileName(), e.getMessage());
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                log.info("Failed to access file: {} - {}", file.toString(), exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        // 1. 必须【先】关闭 git 客户端及其底层的 Repository，释放 Windows 文件锁
        if (git != null) {
            git.getRepository().close();
            git.close();
        }

        // 2. 建议加上主动 GC 提示（应对 Windows 下内存映射文件延迟释放的顽疾）
        System.gc();

        // 3. 【再】安全删除本地目录
        try {
            FileUtils.deleteDirectory(new File(localPath));
        } catch (Exception e) {
            log.warn("目录清理失败 (可忽略，不影响主流程): {}", e.getMessage());
            // 如果实在删不掉，可以标记为 JVM 退出时删除
            new File(localPath).deleteOnExit();
        }

        // 4. 更新 Redis 缓存标签
        RList<String> elements = redissonClient.getList("ragTag");
        if (!elements.contains(repoProjectName)) {
            elements.add(repoProjectName);
        }

        log.info("遍历解析路径，上传完成:{}", repoUrl);

        return Response.<String>builder().code("0000").info("调用成功").build();
    }

    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }

}
