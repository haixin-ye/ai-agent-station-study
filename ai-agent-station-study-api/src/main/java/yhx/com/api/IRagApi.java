package yhx.com.api;

import yhx.com.api.dto.RagGitAnalyzeRequestDTO;
import yhx.com.api.dto.RagFileUploadResultDTO;
import yhx.com.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * author yhx
 */
public interface IRagApi {

    Response<Set<String>> queryRagTagList();

    Response<RagFileUploadResultDTO> uploadFile(String knowledgeTag, List<MultipartFile> files);

    Response<String> analyzeGitRepository(RagGitAnalyzeRequestDTO requestDTO) throws Exception;
}

