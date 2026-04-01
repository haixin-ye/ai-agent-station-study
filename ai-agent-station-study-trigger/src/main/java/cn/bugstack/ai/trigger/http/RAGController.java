package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IRAGService;
import cn.bugstack.ai.api.dto.RagGitAnalyzeRequestDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.entity.RagFileIngestCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.RagFilePayloadEntity;
import cn.bugstack.ai.domain.agent.model.entity.RagGitIngestCommandEntity;
import cn.bugstack.ai.domain.agent.service.rag.IRagService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * RAG controller.
 *
 * @author yhx
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/rag")
public class RAGController implements IRAGService {

    @Resource
    private IRagService ragService;

    @RequestMapping(value = "/tags", method = RequestMethod.GET)
    @Override
    public Response<Set<String>> queryRagTagList() {
        return Response.<Set<String>>builder()
                .code("0000")
                .info("调用成功")
                .data(ragService.queryRagTagList())
                .build();
    }

    @RequestMapping(value = "/knowledge/files", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam("knowledgeTag") String knowledgeTag, @RequestParam("files") List<MultipartFile> files) {
        try {
            List<RagFilePayloadEntity> payloads = new ArrayList<>(files.size());
            for (MultipartFile file : files) {
                payloads.add(RagFilePayloadEntity.builder()
                        .fileName(file.getOriginalFilename())
                        .content(file.getBytes())
                        .build());
            }

            ragService.ingestFiles(RagFileIngestCommandEntity.builder()
                    .knowledgeTag(knowledgeTag)
                    .files(payloads)
                    .build());

            return Response.<String>builder()
                    .code("0000")
                    .info("调用成功")
                    .build();
        } catch (Exception e) {
            log.error("upload rag file failed, knowledgeTag: {}", knowledgeTag, e);
            return Response.<String>builder()
                    .code("0001")
                    .info("调用失败")
                    .data(e.getMessage())
                    .build();
        }
    }

    @RequestMapping(value = "/knowledge/git", method = RequestMethod.POST)
    @Override
    public Response<String> analyzeGitRepository(@RequestBody RagGitAnalyzeRequestDTO requestDTO) throws Exception {
        try {
            ragService.analyzeGitRepository(RagGitIngestCommandEntity.builder()
                    .repoUrl(requestDTO.getRepoUrl())
                    .userName(requestDTO.getUserName())
                    .token(requestDTO.getToken())
                    .build());

            return Response.<String>builder()
                    .code("0000")
                    .info("调用成功")
                    .build();
        } catch (Exception e) {
            log.error("analyze git repository failed, repo: {}", requestDTO.getRepoUrl(), e);
            return Response.<String>builder()
                    .code("0001")
                    .info("调用失败")
                    .data(e.getMessage())
                    .build();
        }
    }

}
