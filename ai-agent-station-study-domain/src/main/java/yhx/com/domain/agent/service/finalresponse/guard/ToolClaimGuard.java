package yhx.com.domain.agent.service.finalresponse.guard;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;

import java.util.List;
import java.util.Locale;

public class ToolClaimGuard extends FinalGuardSupport {

    private static final List<String> TOOL_SUCCESS_CLAIMS = List.of(
            "published", "uploaded", "deleted", "sent", "file changed", "tool succeeded",
            "已发布", "已上传", "已删除", "已发送", "文件已修改", "发布成功"
    );

    @Override
    public FinalResponseGuardResultVO check(FinalResponseGuardInputVO input) {
        String content = content(input);
        if (content == null || content.isBlank()) {
            return passed(input);
        }
        boolean hasVerifiedTool = input != null && input.getVerifiedToolCallRefs() != null && !input.getVerifiedToolCallRefs().isEmpty();
        if (hasVerifiedTool) {
            return passed(input);
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        for (String claim : TOOL_SUCCESS_CLAIMS) {
            if (normalized.contains(claim.toLowerCase(Locale.ROOT))) {
                return failed(input, "FINAL_FALSE_TOOL_CLAIM", "Final answer claims tool success without verified tool evidence.");
            }
        }
        return passed(input);
    }
}
