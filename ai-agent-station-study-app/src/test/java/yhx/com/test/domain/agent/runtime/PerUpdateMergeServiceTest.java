package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.runtime.MainAgentNotebookVO;
import yhx.com.domain.agent.service.runtime.PerUpdateMergeService;

import java.util.List;
import java.util.Map;

public class PerUpdateMergeServiceTest {

    private final PerUpdateMergeService service = new PerUpdateMergeService();

    @Test
    public void merge_creates_notebook_from_per_update() {
        MainAgentNotebookVO notebook = service.merge(null, Map.of(
                "mode", "PER",
                "goal", "inspect domain folder",
                "stepUpdates", List.of(Map.of(
                        "stepId", "s1",
                        "title", "resolve target folder",
                        "status", "IN_PROGRESS",
                        "note", "Search before reading."
                )),
                "nextStepId", "s1",
                "lastDecision", "Resolve the folder first."
        ), 2, 10L);

        Assert.assertNotNull(notebook);
        Assert.assertEquals("PER", notebook.getMode());
        Assert.assertEquals("inspect domain folder", notebook.getGoal());
        Assert.assertEquals(Integer.valueOf(1), notebook.getNotebookVersion());
        Assert.assertEquals(Integer.valueOf(2), notebook.getLastUpdatedLoopIndex());
        Assert.assertEquals(Long.valueOf(10L), notebook.getLastUpdatedSequence());
        Assert.assertEquals("s1", notebook.getNextStepId());
        Assert.assertEquals("Resolve the folder first.", notebook.getLastDecision());
        Assert.assertEquals(1, notebook.getSteps().size());
        Assert.assertEquals("s1", notebook.getSteps().get(0).getStepId());
        Assert.assertEquals("IN_PROGRESS", notebook.getSteps().get(0).getStatus());
        Assert.assertEquals(Integer.valueOf(2), notebook.getSteps().get(0).getCreatedLoopIndex());
        Assert.assertEquals(Long.valueOf(10L), notebook.getSteps().get(0).getCreatedSequence());
    }

    @Test
    public void merge_updates_existing_step_by_step_id() {
        MainAgentNotebookVO first = service.merge(null, Map.of(
                "mode", "PER",
                "goal", "inspect domain folder",
                "stepUpdates", List.of(Map.of(
                        "stepId", "s1",
                        "title", "resolve target folder",
                        "status", "IN_PROGRESS"
                ))
        ), 1, 1L);

        MainAgentNotebookVO updated = service.merge(first, Map.of(
                "stepUpdates", List.of(Map.of(
                        "stepId", "s1",
                        "status", "DONE",
                        "note", "Resolved to ai-agent-station-study-domain."
                )),
                "nextStepId", "s2"
        ), 3, 8L);

        Assert.assertSame(first, updated);
        Assert.assertEquals(Integer.valueOf(2), updated.getNotebookVersion());
        Assert.assertEquals(1, updated.getSteps().size());
        Assert.assertEquals("DONE", updated.getSteps().get(0).getStatus());
        Assert.assertEquals("Resolved to ai-agent-station-study-domain.", updated.getSteps().get(0).getNote());
        Assert.assertEquals(Integer.valueOf(1), updated.getSteps().get(0).getCreatedLoopIndex());
        Assert.assertEquals(Long.valueOf(1L), updated.getSteps().get(0).getCreatedSequence());
        Assert.assertEquals(Integer.valueOf(3), updated.getSteps().get(0).getUpdatedLoopIndex());
        Assert.assertEquals(Long.valueOf(8L), updated.getSteps().get(0).getUpdatedSequence());
        Assert.assertEquals("s2", updated.getNextStepId());
    }

    @Test
    public void merge_accepts_failed_step_status() {
        MainAgentNotebookVO notebook = service.merge(null, Map.of(
                "mode", "PER",
                "goal", "write file to desktop",
                "stepUpdates", List.of(Map.of(
                        "stepId", "s1",
                        "title", "resolve desktop path",
                        "status", "FAILED",
                        "note", "The configured tool failed while resolving the target path."
                )),
                "nextStepId", "s2",
                "lastDecision", "step failed; choose a different route or explain the limitation"
        ), 2, 3L);

        Assert.assertEquals("FAILED", notebook.getSteps().get(0).getStatus());
        Assert.assertEquals("The configured tool failed while resolving the target path.",
                notebook.getSteps().get(0).getNote());
    }

    @Test
    public void merge_updates_existing_fact_question_and_risk_by_id() {
        MainAgentNotebookVO first = service.merge(null, Map.of(
                "factsLearned", List.of(Map.of(
                        "factId", "f1",
                        "content", "The first tool result found two domain folders.",
                        "sourceEvidenceIds", List.of("ev-1")
                )),
                "openQuestions", List.of(Map.of(
                        "id", "q1",
                        "content", "Which domain folder should be inspected?",
                        "status", "OPEN"
                )),
                "risks", List.of(Map.of(
                        "id", "r1",
                        "content", "The target path is ambiguous.",
                        "status", "OPEN"
                ))
        ), 1, 1L);

        MainAgentNotebookVO updated = service.merge(first, Map.of(
                "factsLearned", List.of(Map.of(
                        "factId", "f1",
                        "content", "The user selected ai-agent-station-study-domain.",
                        "sourceEvidenceIds", List.of("ev-2")
                )),
                "openQuestions", List.of(Map.of(
                        "id", "q1",
                        "content", "Which domain folder should be inspected?",
                        "status", "RESOLVED",
                        "sourceWorkIds", List.of("work-2")
                )),
                "risks", List.of(Map.of(
                        "id", "r1",
                        "content", "The target path ambiguity has been resolved.",
                        "status", "CLOSED",
                        "sourceWorkIds", List.of("work-2")
                ))
        ), 2, 5L);

        Assert.assertSame(first, updated);
        Assert.assertEquals(1, updated.getFacts().size());
        Assert.assertEquals("The user selected ai-agent-station-study-domain.", updated.getFacts().get(0).getContent());
        Assert.assertEquals(List.of("ev-2"), updated.getFacts().get(0).getSourceEvidenceIds());
        Assert.assertEquals(Integer.valueOf(2), updated.getFacts().get(0).getLoopIndex());
        Assert.assertEquals(Long.valueOf(5L), updated.getFacts().get(0).getSequence());

        Assert.assertEquals(1, updated.getOpenQuestions().size());
        Assert.assertEquals("RESOLVED", updated.getOpenQuestions().get(0).getStatus());
        Assert.assertEquals(List.of("work-2"), updated.getOpenQuestions().get(0).getSourceWorkIds());

        Assert.assertEquals(1, updated.getRisks().size());
        Assert.assertEquals("CLOSED", updated.getRisks().get(0).getStatus());
        Assert.assertEquals("The target path ambiguity has been resolved.", updated.getRisks().get(0).getContent());
    }

    @Test
    public void merge_rejects_invalid_step_status() {
        try {
            service.merge(null, Map.of(
                    "stepUpdates", List.of(Map.of(
                            "stepId", "s1",
                            "status", "MAYBE_DONE"
                    ))
            ), 1, 1L);
            Assert.fail("Expected invalid status to be rejected.");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid notebook step status"));
        }
    }
}
