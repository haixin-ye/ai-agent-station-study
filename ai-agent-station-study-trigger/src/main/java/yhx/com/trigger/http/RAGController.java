package yhx.com.trigger.http;

import yhx.com.api.IRagApi;
import yhx.com.api.dto.RagGitAnalyzeRequestDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagFilePayloadEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitIngestCommandEntity;
import yhx.com.domain.agent.service.rag.IRagDomainService;
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
public class RAGController implements IRagApi {

    @Resource
    private IRagDomainService ragService;

    @RequestMapping(value = "/tags", method = RequestMethod.GET)
    @Override
    public Response<Set<String>> queryRagTagList() {
        return Response.<Set<String>>builder()
                .code("0000")
                .info("success")
                .data(ragService.queryRagTagList())
                .build();
    }

    @RequestMapping(value = "/knowledge/files", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam(value = "knowledgeTag", required = false) String knowledgeTag,
                                       @RequestParam("files") List<MultipartFile> files) {
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
                    .info("success")
                    .build();
        } catch (Exception e) {
            log.error("upload rag file failed, knowledgeTag: {}", knowledgeTag, e);
            return Response.<String>builder()
                    .code("0001")
                    .info("failed")
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
                    .info("success")
                    .build();
        } catch (Exception e) {
            log.error("analyze git repository failed, repo: {}", requestDTO.getRepoUrl(), e);
            return Response.<String>builder()
                    .code("0001")
                    .info("failed")
                    .data(e.getMessage())
                    .build();
        }
    }

}
