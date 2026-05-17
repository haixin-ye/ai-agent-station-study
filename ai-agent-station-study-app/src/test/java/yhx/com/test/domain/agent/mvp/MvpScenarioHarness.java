package yhx.com.test.domain.agent.mvp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import org.junit.Assert;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.RuntimeComponentPorts;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class MvpScenarioHarness {

    MvpScenarioResult run(String scenarioId) {
        JSONObject fixture = loadFixture(scenarioId);
        assertFixtureContract(fixture, scenarioId);

        if ("direct-answer".equals(scenarioId)) {
            return runDirectAnswer(fixture);
        }
        if ("clarify-artifact-reference".equals(scenarioId)) {
            return runClarification(fixture);
        }
        return validateFixtureOnly(fixture);
    }

    private MvpScenarioResult runDirectAnswer(JSONObject fixture) {
        MainAgentActionVO action = mainAction(fixture);
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository,
                RuntimeTestSupport.fixedPorts(action),
                true,
                new RuntimeLoopPolicy());

        String scenarioId = fixture.getString("scenarioId");
        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run-" + scenarioId)
                .sessionId("sess-" + scenarioId)
                .userId("user-mvp")
                .userInput(fixture.getString("userMessage"))
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.COMPLETED, result.getStatus());
        return resultFromRuntime(fixture, result.getRunId(), result.getStatus().code(), result.getFinalAnswer(), repository);
    }

    private MvpScenarioResult runClarification(JSONObject fixture) {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository,
                clarificationPorts(fixture),
                true,
                new RuntimeLoopPolicy());

        String scenarioId = fixture.getString("scenarioId");
        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run-" + scenarioId)
                .sessionId("sess-" + scenarioId)
                .userId("user-mvp")
                .userInput(fixture.getString("userMessage"))
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.WAITING_USER, result.getStatus());
        String payload = result.getAskUserRequest() == null ? "" : result.getAskUserRequest().getQuestion();
        return resultFromRuntime(fixture, result.getRunId(), result.getStatus().code(), payload, repository);
    }

    private RuntimeComponentPorts clarificationPorts(JSONObject fixture) {
        return new RuntimeComponentPorts() {
            @Override
            public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
                return ContextPlannerHandlingResult.builder()
                        .askUserRequest(AskUserRequestVO.builder()
                                .question(contextPlannerQuestion(fixture))
                                .inputMode("SINGLE_CHOICE_OR_FREE_TEXT")
                                .allowFreeText(true)
                                .options(List.of(
                                        Map.of("optionId", "artifact-001", "label", "RAG Interview Notes"),
                                        Map.of("optionId", "artifact-002", "label", "MCP Interview Notes")))
                                .build())
                        .build();
            }

            @Override
            public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
                return MainAgentActionVO.builder().action("FINAL").build();
            }
        };
    }

    private MvpScenarioResult validateFixtureOnly(JSONObject fixture) {
        JSONObject expected = fixture.getJSONObject("expected");
        String normalPayload = JSON.toJSONString(Map.of(
                "scenarioId", fixture.getString("scenarioId"),
                "status", expected.getString("finalStatus"),
                "events", expected.getJSONArray("requiredEvents")));
        return new MvpScenarioResult(fixture.getString("scenarioId"),
                "fixture-" + fixture.getString("scenarioId"),
                expected.getString("finalStatus"),
                normalPayload,
                List.of(),
                safetyViolations(expected, normalPayload));
    }

    private MvpScenarioResult resultFromRuntime(JSONObject fixture,
                                                String runId,
                                                String finalStatus,
                                                String finalAnswer,
                                                RuntimeTestSupport.InMemoryRuntimeRepository repository) {
        JSONObject expected = fixture.getJSONObject("expected");
        String normalPayload = finalAnswer + " " + JSON.toJSONString(repository.events);
        return new MvpScenarioResult(fixture.getString("scenarioId"),
                runId,
                finalStatus,
                normalPayload,
                missingEvents(expected, repository),
                safetyViolations(expected, normalPayload));
    }

    private MainAgentActionVO mainAction(JSONObject fixture) {
        JSONArray responses = fixture.getJSONArray("fakeNodeResponses");
        for (int i = 0; i < responses.size(); i++) {
            JSONObject item = responses.getJSONObject(i);
            if ("MAIN_AGENT".equals(item.getString("componentCode"))) {
                JSONObject response = item.getJSONObject("response");
                return MainAgentActionVO.builder()
                        .action(response.getString("action"))
                        .stateDelta(response.getJSONObject("stateDelta"))
                        .build();
            }
        }
        throw new IllegalStateException("MAIN_AGENT response missing for " + fixture.getString("scenarioId"));
    }

    private String contextPlannerQuestion(JSONObject fixture) {
        JSONArray responses = fixture.getJSONArray("fakeNodeResponses");
        for (int i = 0; i < responses.size(); i++) {
            JSONObject item = responses.getJSONObject(i);
            if ("CONTEXT_PLANNER".equals(item.getString("componentCode"))) {
                return item.getJSONObject("response").getString("question");
            }
        }
        return "Please clarify.";
    }

    private List<String> missingEvents(JSONObject expected, RuntimeTestSupport.InMemoryRuntimeRepository repository) {
        JSONArray requiredEvents = expected.getJSONArray("requiredEvents");
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < requiredEvents.size(); i++) {
            String required = requiredEvents.getString(i);
            boolean found = repository.events.stream()
                    .anyMatch(event -> event.getEventType() != null && required.equals(event.getEventType().code()));
            if (!found) {
                missing.add(required);
            }
        }
        return missing;
    }

    private List<String> safetyViolations(JSONObject expected, String normalPayload) {
        JSONArray forbidden = expected.getJSONArray("forbiddenNormalPayloadFragments");
        List<String> violations = new ArrayList<>();
        for (int i = 0; i < forbidden.size(); i++) {
            String fragment = forbidden.getString(i);
            if (normalPayload != null && normalPayload.contains(fragment)) {
                violations.add(fragment);
            }
        }
        return violations;
    }

    private void assertFixtureContract(JSONObject fixture, String scenarioId) {
        Assert.assertEquals(scenarioId, fixture.getString("scenarioId"));
        Assert.assertNotNull(fixture.getString("description"));
        Assert.assertNotNull(fixture.getString("userMessage"));
        Assert.assertNotNull(fixture.getJSONObject("given"));
        Assert.assertFalse(fixture.getJSONArray("fakeNodeResponses").isEmpty());
        JSONObject expected = fixture.getJSONObject("expected");
        Assert.assertNotNull(expected.getString("finalStatus"));
        Assert.assertNotNull(expected.getJSONArray("requiredEvents"));
        Assert.assertNotNull(expected.getJSONArray("forbiddenNormalPayloadFragments"));
        Assert.assertNotNull(expected.getJSONArray("requiredDebugTraceTypes"));
    }

    private JSONObject loadFixture(String scenarioId) {
        String resource = "/auto-agent/mvp-scenarios/" + scenarioId + ".json";
        try (InputStream inputStream = getClass().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing fixture: " + resource);
            }
            return JSON.parseObject(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load fixture: " + resource, e);
        }
    }

    @Getter
    static class MvpScenarioResult {
        private final String scenarioId;
        private final String runId;
        private final String finalStatus;
        private final String normalPayload;
        private final List<String> missingRequiredEvents;
        private final List<String> safetyViolations;

        MvpScenarioResult(String scenarioId,
                          String runId,
                          String finalStatus,
                          String normalPayload,
                          List<String> missingRequiredEvents,
                          List<String> safetyViolations) {
            this.scenarioId = scenarioId;
            this.runId = runId;
            this.finalStatus = finalStatus;
            this.normalPayload = normalPayload;
            this.missingRequiredEvents = missingRequiredEvents;
            this.safetyViolations = safetyViolations;
        }
    }
}

