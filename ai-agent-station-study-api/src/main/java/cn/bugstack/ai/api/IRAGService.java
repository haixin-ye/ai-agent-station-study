package cn.bugstack.ai.api;

import cn.bugstack.ai.api.dto.RagGitAnalyzeRequestDTO;
import cn.bugstack.ai.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * author yhx
 */
public interface IRAGService {

    Response<Set<String>> queryRagTagList();

    Response<String> uploadFile(String knowledgeTag, List<MultipartFile> files);

    Response<String> analyzeGitRepository(RagGitAnalyzeRequestDTO requestDTO) throws Exception;
}
