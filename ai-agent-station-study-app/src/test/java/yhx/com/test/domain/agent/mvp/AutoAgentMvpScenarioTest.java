package yhx.com.test.domain.agent.mvp;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class AutoAgentMvpScenarioTest {

    private final MvpScenarioHarness harness = new MvpScenarioHarness();

    @Test
    public void mvp_scenarios_should_satisfy_expected_flow_contracts() {
        for (String scenarioId : List.of(
                "direct-answer",
                "artifact-create-update",
                "rag-answer-verified",
                "tool-approval-execute",
                "tool-approval-reject",
                "clarify-artifact-reference",
                "final-guard-repair",
                "context-budget-compaction")) {
            MvpScenarioHarness.MvpScenarioResult result = harness.run(scenarioId);

            Assert.assertEquals(scenarioId, result.getScenarioId());
            Assert.assertTrue("missing required events: " + result.getMissingRequiredEvents(), result.getMissingRequiredEvents().isEmpty());
            Assert.assertTrue("normal payload leaks internals: " + result.getSafetyViolations(), result.getSafetyViolations().isEmpty());
        }
    }

    @Test
    public void direct_answer_fixture_runs_through_runtime_to_completed_final() {
        MvpScenarioHarness.MvpScenarioResult result = harness.run("direct-answer");

        Assert.assertEquals("COMPLETED", result.getFinalStatus());
        Assert.assertTrue(result.getNormalPayload().contains("RAG combines retrieval with generation."));
    }

    @Test
    public void clarify_artifact_reference_fixture_pauses_same_run_for_user_input() {
        MvpScenarioHarness.MvpScenarioResult result = harness.run("clarify-artifact-reference");

        Assert.assertEquals("WAITING_USER", result.getFinalStatus());
        Assert.assertEquals("run-clarify-artifact-reference", result.getRunId());
        Assert.assertTrue(result.getNormalPayload().contains("Which article should be used?"));
    }
}

