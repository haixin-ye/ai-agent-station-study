package yhx.com.test.domain.agent.finalresponse;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.service.finalresponse.FinalResponseGuard;

import java.util.List;

public class FinalResponseGuardTest {

    @Test
    public void empty_answer_is_blocked() {
        Assert.assertEquals("FINAL_EMPTY", check("").getFailureCode());
    }

    @Test
    public void internal_runtime_wording_is_blocked() {
        Assert.assertEquals("FINAL_INTERNAL_LEAK", check("Runtime verifier trace says ok").getFailureCode());
    }

    @Test
    public void raw_json_answer_is_blocked() {
        Assert.assertEquals("FINAL_FORMAT_VIOLATION", check("{\"answer\":\"ok\"}").getFailureCode());
    }

    @Test
    public void missing_citation_is_blocked() {
        FinalResponseGuardResultVO result = guard().check(input("answer [evidence-missing]", List.of("evidence-1"), List.of(), 1000));

        Assert.assertEquals("FINAL_INVALID_CITATION", result.getFailureCode());
    }

    @Test
    public void false_tool_success_claim_is_blocked() {
        Assert.assertEquals("FINAL_FALSE_TOOL_CLAIM", check("The article was published successfully.").getFailureCode());
    }

    @Test
    public void too_long_answer_is_blocked() {
        FinalResponseGuardResultVO result = guard().check(input("123456", List.of(), List.of(), 3));

        Assert.assertEquals("FINAL_TOO_LONG", result.getFailureCode());
    }

    @Test
    public void clean_answer_passes() {
        FinalResponseGuardResultVO result = guard().check(input("This is a clean answer.", List.of(), List.of(), 1000));

        Assert.assertEquals("PASSED", result.getStatus());
    }

    private FinalResponseGuardResultVO check(String content) {
        return guard().check(input(content, List.of(), List.of(), 1000));
    }

    private FinalResponseGuard guard() {
        return new FinalResponseGuard();
    }

    private FinalResponseGuardInputVO input(String content, List<String> evidenceRefs, List<String> toolRefs, int maxChars) {
        return FinalResponseGuardInputVO.builder()
                .candidate(FinalAnswerCandidateVO.builder().content(content).build())
                .evidenceRefs(evidenceRefs)
                .verifiedToolCallRefs(toolRefs)
                .userFormatRequirement("PLAIN_TEXT")
                .maxOutputChars(maxChars)
                .build();
    }
}
