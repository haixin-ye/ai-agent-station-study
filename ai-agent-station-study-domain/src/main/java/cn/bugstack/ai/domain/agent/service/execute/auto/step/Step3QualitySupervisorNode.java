package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.AcceptedResultVO;
import cn.bugstack.ai.domain.agent.model.entity.ExecutionOutcomeVO;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.entity.NextRoundDirectiveVO;
import cn.bugstack.ai.domain.agent.model.entity.RoundExecutionSummaryVO;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.entity.SupervisionDecisionVO;
import cn.bugstack.ai.domain.agent.model.entity.TaskBoardItemVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.NextRoundDirectiveTypeEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.OverallStateEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.StepStatusEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Node3 quality supervisor.
 * Only Node3 can decide whether to finish or continue the loop.
 */
@Slf4j
@Service
public class Step3QualitySupervisorNode extends AbstractExecuteSupport {

    private static final int MAX_ACCEPTED_CONTENT_LENGTH = 320;
    private static final int MAX_ACCEPTED_REASON_LENGTH = 220;
    private static final int MAX_PROMPT_TEXT_LENGTH = 800;
    private static final int MAX_PROMPT_EXECUTION_LENGTH = 1200;
    private static final int MAX_PROMPT_ROUNDS = 4;
    private static final int MAX_PROMPT_ACCEPTED_RESULTS = 6;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter,
                             DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Stage3: quality supervision");

        String executionResult = dynamicContext.getValue("executionResult");
        if (!StringUtils.hasText(executionResult)) {
            throw new IllegalStateException("Node3 missing executionResult");
        }

        StepExecutionPlanVO plan = dynamicContext.getCurrentStepPlan();
        if (plan == null) {
            throw new IllegalStateException("Node3 missing currentStepPlan");
        }

        String sanitizedGoal = dynamicContext.getSanitizedUserGoal();
        if (!StringUtils.hasText(sanitizedGoal)) {
            throw new IllegalStateException("Node3 missing sanitizedUserGoal");
        }

        String lastToolError = dynamicContext.getValue("lastToolError");
        ExecutionOutcomeVO executionOutcome = dynamicContext.getValue("executionOutcome");
        if (executionOutcome == null) {
            executionOutcome = Step2PrecisionExecutorNode.buildExecutionOutcome(dynamicContext, executionResult, plan);
        }
        String supervisionPrompt = buildSupervisionPrompt(dynamicContext, executionOutcome, lastToolError);
        sendSupervisionSubResult(dynamicContext, "supervision_round_ref", JSON.toJSONString(dynamicContext.getCurrentRound()), requestParameter.getSessionId());
        sendSupervisionSubResult(dynamicContext, "supervision_task_board", JSON.toJSONString(dynamicContext.getTaskBoard()), requestParameter.getSessionId());
        sendSupervisionSubResult(dynamicContext, "supervision_accepted_results", JSON.toJSONString(dynamicContext.getAcceptedResults()), requestParameter.getSessionId());
        sendSupervisionSubResult(dynamicContext, "supervision_overall_status", JSON.toJSONString(dynamicContext.getOverallStatus()), requestParameter.getSessionId());
        sendSupervisionSubResult(dynamicContext, "supervision_execution_ref", executionResult, requestParameter.getSessionId());

        AiAgentClientFlowConfigVO flowConfig = dynamicContext.getAiAgentClientFlowConfigVOMap()
                .get(AiClientTypeEnumVO.QUALITY_SUPERVISOR_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(flowConfig.getClientId());

        String supervisionResult = chatClient
                .prompt(supervisionPrompt)
                .advisors(a -> {
                    a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, buildNodeConversationId(requestParameter.getSessionId(), "node3"))
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 8);
                    applyTokenStatParams(
                            a, dynamicContext, requestParameter,
                            flowConfig.getClientId(),
                            AiClientTypeEnumVO.QUALITY_SUPERVISOR_CLIENT.getCode()
                    );
                })
                .call()
                .content();

        dynamicContext.setValue("supervisionResult", supervisionResult);
        parseSupervisionResult(dynamicContext, supervisionResult, requestParameter.getSessionId());
        int supervisionStep = dynamicContext.getStep();
        SupervisionDecisionVO decision = resolveDecision(supervisionResult, executionOutcome, dynamicContext);
        applyDecisionToContext(dynamicContext, decision, executionResult);

        int currentRound = supervisionStep;
        dynamicContext.getExecutionHistory().append(String.format("""
                === Round %d Supervision(Node3) ===
                %s
                """, currentRound, supervisionResult));

        if (dynamicContext.isCompleted()) {
            sendSupervisionSubResultAtStep(dynamicContext, supervisionStep, "supervision_decision", "FINISH -> Step4", requestParameter.getSessionId());
            log.info("Node3 finish -> go Step4");
        } else {
            if (dynamicContext.getNextRoundDirective() != null
                    && NextRoundDirectiveTypeEnumVO.REPLAN_SAME_STEP.equals(dynamicContext.getNextRoundDirective().getDirectiveType())) {
                dynamicContext.setCurrentTask("re-plan based on supervision");
                sendSupervisionSubResultAtStep(dynamicContext, supervisionStep, "supervision_decision", "REPLAN_SAME_STEP -> back to Node1", requestParameter.getSessionId());
                log.info("Node3 replan same step -> loop back to Step1");
            } else {
                sendSupervisionSubResultAtStep(dynamicContext, supervisionStep, "supervision_decision", "ADVANCE_NEXT_STEP -> back to Node1", requestParameter.getSessionId());
                log.info("Node3 advance next step -> loop back to Step1");
            }
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity requestParameter,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return getBean("step4LogExecutionSummaryNode");
        }
        return getBean("step1AnalyzerNode");
    }

    static String buildSupervisionPrompt(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                         ExecutionOutcomeVO executionOutcome,
                                         String lastToolError) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("rawUserInput", dynamicContext == null || dynamicContext.getSessionGoal() == null
                ? safe(dynamicContext == null ? null : dynamicContext.getRawUserGoal())
                : safe(dynamicContext.getSessionGoal().getRawUserInput()));
        context.put("sanitizedGoal", dynamicContext == null || dynamicContext.getSessionGoal() == null
                ? safe(dynamicContext == null ? null : dynamicContext.getSanitizedUserGoal())
                : safe(dynamicContext.getSessionGoal().getSanitizedGoal()));
        context.put("currentRound", dynamicContext == null || dynamicContext.getCurrentRound() == null
                ? Map.of()
                : dynamicContext.getCurrentRound());
        context.put("masterPlan", dynamicContext == null || dynamicContext.getMasterPlan() == null
                ? Map.of()
                : dynamicContext.getMasterPlan());
        context.put("verificationPolicy", Map.of(
                "primaryTruthSources", List.of("currentRound", "taskBoard", "acceptedResults", "overallStatus", "roundArchive"),
                "toolReceiptPreferredForToolTasks", true,
                "filesystemPostconditionAcceptedForWriteTasks", true,
                "singleRoundDeliverableCanFinish", true,
                "roundPassDoesNotMeanOverallPass", true,
                "acceptRelaxedToolNarrativeWhenCoherent", false,
                "ignoreUnacceptedExecutionText", true,
                "outputMode", "single_json_object_only"
        ));
        context.put("taskBoard", dynamicContext == null || dynamicContext.getTaskBoard() == null
                ? Map.of()
                : buildTaskBoardPromptView(dynamicContext));
        context.put("acceptedResults", dynamicContext == null || dynamicContext.getAcceptedResults() == null
                ? List.of()
                : buildAcceptedResultsPromptView(dynamicContext.getAcceptedResults()));
        context.put("overallStatus", dynamicContext == null || dynamicContext.getOverallStatus() == null
                ? Map.of()
                : dynamicContext.getOverallStatus());
        context.put("roundArchive", dynamicContext == null || dynamicContext.getRoundArchive() == null
                ? Map.of()
                : buildRoundArchivePromptView(dynamicContext));
        context.put("nextRoundDirective", dynamicContext == null || dynamicContext.getNextRoundDirective() == null
                ? Map.of()
                : dynamicContext.getNextRoundDirective());
        context.put("executionOutcome", executionOutcome == null ? Map.of() : buildExecutionOutcomePromptView(executionOutcome));
        context.put("roundExecutionSummary", dynamicContext == null || dynamicContext.getRoundExecutionSummary() == null
                ? Map.of()
                : buildRoundExecutionSummaryPromptView(dynamicContext.getRoundExecutionSummary()));
        context.put("executionResult", executionOutcome == null ? "" : compactForPrompt(executionOutcome.getRawResult(), MAX_PROMPT_EXECUTION_LENGTH));
        context.put("lastToolError", safe(lastToolError));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", "verify_current_round_and_overall_progress");
        payload.put("context", context);
        payload.put("requirements", List.of(
                "Judge the current round and the overall task separately.",
                "Use currentRound, taskBoard, acceptedResults, overallStatus, and roundArchive as the primary verification state.",
                "Use roundExecutionSummary as the primary description of what Node2 actually did in this round.",
                "Treat currentRound as the authoritative source for the current round; compatPlan and old execution text are fallback only.",
                "If the task is a single-shot QA/RAG/explanation request and the accepted evidence already answers the raw user input, do not invent a post-answer confirmation round; mark overallDecision as OVERALL_PASS.",
                "Do not treat a round pass as overall success unless overallDecision is OVERALL_PASS.",
                "For tool tasks, compare currentRound requirements with roundExecutionSummary and real callback tool records before trusting the narrative answer.",
                "A coherent natural-language tool narrative alone is not sufficient evidence when the round required a tool.",
                "Treat trusted callback records, retained tool receipts, and verified postconditions as credible evidence; do not require a filesystem-only rule for every MCP tool.",
                "If the round requires a tool but roundExecutionSummary shows no real invocation, no success, or no credible evidence, force roundDecision to ROUND_RETRY and overallDecision to OVERALL_CONTINUE.",
                "When verification fails, write roundDecision to ROUND_RETRY instead of pretending the task is complete.",
                "Only promote results into acceptedResults when the evidence is sufficient and the accepted result can be stated as a concise verified summary.",
                "Return exactly one JSON object and nothing else."
        ));
        payload.put("outputSchema", List.of(
                "decision",
                "roundDecision",
                "overallDecision",
                "nextAction",
                "assessment",
                "issues",
                "suggestions",
                "score"
        ));
        return JSON.toJSONString(payload);
    }

    private static String buildSupervisionPrompt(String sanitizedGoal,
                                                 String planJson,
                                                 String executionResult,
                                                 String executionHistory,
                                                 String lastToolError) {
        return String.format("""
                You are Node3 quality supervisor. Evaluate whether this round meets goal.

                [sanitizedUserGoal]
                %s

                [currentStepPlan]
                %s

                [executionResult]
                %s

                [executionHistory]
                %s

                [lastToolError]
                %s

                Output format:
                Assessment: ...
                Issues: ...
                Suggestions: ...
                Score: [1-10]
                Pass: [PASS/FAIL/OPTIMIZE]
                Decision: [PASS/REPLAN]
                """, safe(sanitizedGoal), safe(planJson), safe(executionResult), safe(executionHistory), safe(lastToolError));
    }

    public static boolean containsPass(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        SupervisionDecisionVO decision = resolveDecision(text, null);
        if (decision == null) {
            return false;
        }
        return SupervisionDecisionVO.OVERALL_PASS.equals(decision.getOverallDecision());
    }

    public static SupervisionDecisionVO resolveDecision(String supervisionResult, ExecutionOutcomeVO executionOutcome) {
        return resolveDecision(supervisionResult, executionOutcome, null);
    }

    public static SupervisionDecisionVO resolveDecision(String supervisionResult,
                                                        ExecutionOutcomeVO executionOutcome,
                                                        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        RoundExecutionSummaryVO roundExecutionSummary = dynamicContext == null ? null : dynamicContext.getRoundExecutionSummary();
        if (requiresToolEvidence(dynamicContext, roundExecutionSummary) && !hasSufficientToolEvidence(roundExecutionSummary, dynamicContext)) {
            return SupervisionDecisionVO.builder()
                    .decision(SupervisionDecisionVO.REPLAN)
                    .roundDecision(SupervisionDecisionVO.ROUND_RETRY)
                    .overallDecision(SupervisionDecisionVO.OVERALL_CONTINUE)
                    .nextAction("NEXT_ROUND_REPLAN")
                    .assessment(roundExecutionSummary == null
                            ? "Missing round execution summary for tool-required task"
                            : defaultIfBlank(roundExecutionSummary.getBlockingReason(), "Missing credible tool execution evidence"))
                    .issues(roundExecutionSummary == null ? "MISSING_ROUND_EXECUTION_SUMMARY" : "INSUFFICIENT_TOOL_EVIDENCE")
                    .raw(supervisionResult)
                    .build();
        }
        if (executionOutcome != null && !ExecutionOutcomeVO.SUCCESS.equals(executionOutcome.getStatus())) {
            boolean relaxedEvidence = isRelaxedToolFailureThatCanContinue(executionOutcome, dynamicContext, roundExecutionSummary);
            if (!relaxedEvidence) {
                return SupervisionDecisionVO.builder()
                        .decision(SupervisionDecisionVO.REPLAN)
                        .roundDecision(SupervisionDecisionVO.ROUND_RETRY)
                        .overallDecision(SupervisionDecisionVO.OVERALL_CONTINUE)
                        .nextAction("NEXT_ROUND_REPLAN")
                        .assessment(executionOutcome.getErrorMessage())
                        .issues(executionOutcome.getErrorCode())
                        .raw(supervisionResult)
                        .build();
            }
        }

        if (!StringUtils.hasText(supervisionResult)) {
            return SupervisionDecisionVO.builder()
                    .decision(SupervisionDecisionVO.REPLAN)
                    .roundDecision(SupervisionDecisionVO.ROUND_RETRY)
                    .overallDecision(SupervisionDecisionVO.OVERALL_CONTINUE)
                    .nextAction("NEXT_ROUND_REPLAN")
                    .raw("")
                    .build();
        }

        String text = supervisionResult.trim();
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                JSONObject json = JSON.parseObject(text);
                String decision = json.getString("decision");
                String roundDecision = defaultIfBlank(json.getString("roundDecision"), SupervisionDecisionVO.ROUND_RETRY);
                String overallDecision = defaultIfBlank(json.getString("overallDecision"),
                        inferOverallDecision(decision, roundDecision));
                return SupervisionDecisionVO.builder()
                        .decision(decision)
                        .roundDecision(roundDecision)
                        .overallDecision(overallDecision)
                        .nextAction(defaultIfBlank(json.getString("nextAction"), "NEXT_ROUND_REPLAN"))
                        .assessment(json.getString("assessment"))
                        .issues(json.getString("issues"))
                        .suggestions(json.getString("suggestions"))
                        .score(json.getInteger("score"))
                        .raw(supervisionResult)
                        .build();
            } catch (Exception ignore) {
                // fall through
            }
        }

        String pass = extractField(text, "Pass:");
        String decisionText = extractField(text, "Decision:");
        String assessment = extractField(text, "Assessment:");
        String issues = extractField(text, "Issues:");
        String suggestions = extractField(text, "Suggestions:");
        String scoreText = extractField(text, "Score:");
        boolean passLike = "PASS".equalsIgnoreCase(pass) && !"REPLAN".equalsIgnoreCase(decisionText);

        return SupervisionDecisionVO.builder()
                .decision(passLike ? SupervisionDecisionVO.PASS : SupervisionDecisionVO.REPLAN)
                .roundDecision(passLike ? SupervisionDecisionVO.ROUND_PASS : SupervisionDecisionVO.ROUND_RETRY)
                .overallDecision(passLike ? SupervisionDecisionVO.OVERALL_PASS : SupervisionDecisionVO.OVERALL_CONTINUE)
                .nextAction(passLike ? "FINISH" : "NEXT_ROUND_REPLAN")
                .assessment(assessment)
                .issues(issues)
                .suggestions(suggestions)
                .score(parseInteger(scoreText))
                .raw(supervisionResult)
                .build();
    }

    private static String inferOverallDecision(String decision, String roundDecision) {
        if ("PASS".equalsIgnoreCase(decision) || SupervisionDecisionVO.ROUND_PASS.equalsIgnoreCase(roundDecision)) {
            return SupervisionDecisionVO.OVERALL_PASS;
        }
        return SupervisionDecisionVO.OVERALL_CONTINUE;
    }

    private static boolean isRelaxedToolFailureThatCanContinue(ExecutionOutcomeVO executionOutcome,
                                                               DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                               RoundExecutionSummaryVO roundExecutionSummary) {
        if (executionOutcome == null || ExecutionOutcomeVO.SUCCESS.equals(executionOutcome.getStatus())) {
            return false;
        }

        String errorCode = executionOutcome.getErrorCode();
        boolean receiptOnlyFailure = "MISSING_TOOL_RECEIPT".equalsIgnoreCase(errorCode)
                || "FILESYSTEM_POSTCONDITION_FAILED".equalsIgnoreCase(errorCode)
                || "MISSING_TOOL_EVIDENCE".equalsIgnoreCase(errorCode);
        if (!receiptOnlyFailure) {
            return false;
        }

        return hasSufficientToolEvidence(roundExecutionSummary, dynamicContext);
    }

    private static boolean requiresToolEvidence(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                RoundExecutionSummaryVO roundExecutionSummary) {
        if (roundExecutionSummary != null && roundExecutionSummary.getToolRequired() != null) {
            return roundExecutionSummary.getToolRequired();
        }
        if (dynamicContext == null) {
            return false;
        }
        CurrentRoundTaskVO currentRound = dynamicContext.getCurrentRound();
        if (currentRound != null && currentRound.getToolRequired() != null) {
            return currentRound.getToolRequired();
        }
        StepExecutionPlanVO plan = dynamicContext.getCurrentStepPlan();
        return plan != null && Boolean.TRUE.equals(plan.getToolRequired());
    }

    private static boolean hasSufficientToolEvidence(RoundExecutionSummaryVO roundExecutionSummary,
                                                     DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (roundExecutionSummary != null) {
            return Boolean.TRUE.equals(roundExecutionSummary.getToolInvoked())
                    && Boolean.TRUE.equals(roundExecutionSummary.getToolSuccess())
                    && Boolean.TRUE.equals(roundExecutionSummary.getEvidenceAvailable());
        }
        return hasVerifiedToolSuccess(dynamicContext);
    }

    private static boolean hasVerifiedToolSuccess(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (dynamicContext == null || dynamicContext.getToolExecutionLog() == null) {
            return false;
        }
        int round = dynamicContext.getStep();
        String currentStepId = resolveStepId(dynamicContext);
        return dynamicContext.getToolExecutionLog().stream()
                .anyMatch(record -> Boolean.TRUE.equals(record.getSuccess())
                        && ((record.getRoundIndex() != null && record.getRoundIndex().intValue() == round)
                        || (StringUtils.hasText(currentStepId) && currentStepId.equals(record.getStepId()))));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractField(String text, String fieldName) {
        int start = text.indexOf(fieldName);
        if (start < 0) {
            return "";
        }
        String remaining = text.substring(start + fieldName.length()).trim();
        int newline = remaining.indexOf('\n');
        return newline >= 0 ? remaining.substring(0, newline).trim() : remaining.trim();
    }

    private static String buildNodeConversationId(String sessionId, String nodeTag) {
        if (!StringUtils.hasText(sessionId)) {
            return nodeTag;
        }
        return sessionId + ":" + nodeTag;
    }

    private void parseSupervisionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String supervisionResult,
                                        String sessionId) {
        if (!StringUtils.hasText(supervisionResult)) {
            return;
        }

        String[] lines = supervisionResult.split("\n");
        String currentSection = "";
        StringBuilder sectionContent = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("Assessment:") || trimmed.startsWith("需求匹配度:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "assessment";
                sectionContent.setLength(0);
                continue;
            } else if (trimmed.startsWith("Issues:") || trimmed.startsWith("问题识别:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "issues";
                sectionContent.setLength(0);
                continue;
            } else if (trimmed.startsWith("Suggestions:") || trimmed.startsWith("改进建议:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "suggestions";
                sectionContent.setLength(0);
                continue;
            } else if (trimmed.startsWith("Score:") || trimmed.startsWith("质量评分:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "score";
                sectionContent.setLength(0);
                continue;
            } else if (trimmed.startsWith("Pass:") || trimmed.startsWith("是否通过:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "pass";
                sectionContent.setLength(0);
                continue;
            } else if (trimmed.startsWith("Decision:") || trimmed.startsWith("决策:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "decision";
                sectionContent.setLength(0);
                continue;
            }

            if (!currentSection.isEmpty()) {
                sectionContent.append(trimmed).append("\n");
            }
        }

        sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
        sendSupervisionResult(dynamicContext, supervisionResult, sessionId);
    }

    private void sendSupervisionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                       String supervisionResult,
                                       String sessionId) {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSupervisionResult(
                dynamicContext.getStep(), supervisionResult, sessionId);
        sendSseResult(dynamicContext, result);
    }

    private void sendSupervisionSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                          String section,
                                          String content,
                                          String sessionId) {
        if (!StringUtils.hasText(section) || !StringUtils.hasText(content)) {
            return;
        }
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSupervisionSubResult(
                dynamicContext.getStep(), section, content, sessionId);
        sendSseResult(dynamicContext, result);
    }

    private void sendSupervisionSubResultAtStep(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                Integer step,
                                                String section,
                                                String content,
                                                String sessionId) {
        if (!StringUtils.hasText(section) || !StringUtils.hasText(content)) {
            return;
        }
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSupervisionSubResult(
                step, section, content, sessionId);
        sendSseResult(dynamicContext, result);
    }

    static void applyDecisionToContext(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                       SupervisionDecisionVO decision,
                                       String executionResult) {
        if (dynamicContext == null || decision == null) {
            return;
        }

        int round = dynamicContext.getStep();
        String stepId = resolveStepId(dynamicContext);
        String roundTask = resolveRoundTask(dynamicContext);

        TaskBoardItemVO item = dynamicContext.getTaskBoard().computeIfAbsent(stepId, key -> TaskBoardItemVO.builder()
                .stepId(stepId)
                .attemptCount(0)
                .acceptedOutputs(new ArrayList<>())
                .status(StepStatusEnumVO.PENDING)
                .build());
        item.setAttemptCount(item.getAttemptCount() == null ? 1 : item.getAttemptCount() + 1);
        item.setLastRoundTask(roundTask);

        dynamicContext.getRoundArchive().computeIfAbsent(round, key ->
                cn.bugstack.ai.domain.agent.model.entity.RoundArchiveVO.builder().round(round).build())
                .setNode3VerificationSnapshot(compactForPrompt(decision.getRaw(), MAX_PROMPT_EXECUTION_LENGTH));

        if (SupervisionDecisionVO.ROUND_PASS.equals(decision.getRoundDecision())) {
            item.setStatus(StepStatusEnumVO.SUCCEEDED);
            item.setLastFailureReason("");

            String acceptedContent = normalizeAcceptedContent(executionResult, decision);
            AcceptedResultVO acceptedResult = AcceptedResultVO.builder()
                    .stepId(stepId)
                    .resultType("ROUND_RESULT")
                    .content(acceptedContent)
                    .evidenceRefs(List.of("round:" + round))
                    .acceptedByRound(round)
                    .acceptedReason(compactForPrompt(defaultIfBlank(decision.getAssessment(), "Node3 accepted the round output"),
                            MAX_ACCEPTED_REASON_LENGTH))
                    .build();
            dynamicContext.getAcceptedResults().add(acceptedResult);
            item.getAcceptedOutputs().add(acceptedContent);
            dynamicContext.getRoundArchive().get(round).getAcceptedResults().add(acceptedResult);

            dynamicContext.getOverallStatus().getCompletedSteps().add(stepId);
            dynamicContext.getOverallStatus().getRemainingSteps().remove(stepId);

            if (SupervisionDecisionVO.OVERALL_PASS.equals(decision.getOverallDecision())) {
                dynamicContext.setNextRoundDirective(NextRoundDirectiveVO.builder()
                        .directiveType(NextRoundDirectiveTypeEnumVO.FINISH_SUCCESS)
                        .targetStepId(stepId)
                        .reason(defaultIfBlank(decision.getAssessment(), "overall pass"))
                        .build());
                dynamicContext.getOverallStatus().setState(OverallStateEnumVO.COMPLETED);
                dynamicContext.getOverallStatus().setFinalDecision("FINISH_SUCCESS");
                dynamicContext.setCompleted(true);
            } else {
                dynamicContext.setNextRoundDirective(NextRoundDirectiveVO.builder()
                        .directiveType(NextRoundDirectiveTypeEnumVO.ADVANCE_NEXT_STEP)
                        .targetStepId(stepId)
                        .reason(defaultIfBlank(decision.getSuggestions(), "advance to next step"))
                        .build());
                dynamicContext.getOverallStatus().setState(OverallStateEnumVO.RUNNING);
                dynamicContext.getOverallStatus().setFinalDecision("ADVANCE_NEXT_STEP");
                dynamicContext.setCompleted(false);
                dynamicContext.setStep(round + 1);
            }
        } else {
            item.setStatus(StepStatusEnumVO.FAILED);
            item.setLastFailureReason(defaultIfBlank(decision.getIssues(), "round verification failed"));
            dynamicContext.getOverallStatus().setState(OverallStateEnumVO.RUNNING);
            dynamicContext.getOverallStatus().getBlockedReasons().add(item.getLastFailureReason());
            dynamicContext.getOverallStatus().setFinalDecision("REPLAN_SAME_STEP");
            dynamicContext.setNextRoundDirective(NextRoundDirectiveVO.builder()
                    .directiveType(NextRoundDirectiveTypeEnumVO.REPLAN_SAME_STEP)
                    .targetStepId(stepId)
                    .reason(item.getLastFailureReason())
                    .build());
            dynamicContext.setCompleted(false);
            dynamicContext.setStep(round + 1);
        }
    }

    private static String resolveStepId(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        CurrentRoundTaskVO currentRound = dynamicContext.getCurrentRound();
        if (currentRound != null && StringUtils.hasText(currentRound.getCurrentStepId())) {
            return currentRound.getCurrentStepId();
        }
        StepExecutionPlanVO plan = dynamicContext.getCurrentStepPlan();
        if (plan != null && StringUtils.hasText(plan.getPlanId())) {
            return plan.getPlanId();
        }
        return "round-" + dynamicContext.getStep();
    }

    private static String resolveRoundTask(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        CurrentRoundTaskVO currentRound = dynamicContext.getCurrentRound();
        if (currentRound != null && StringUtils.hasText(currentRound.getRoundTask())) {
            return currentRound.getRoundTask();
        }
        StepExecutionPlanVO plan = dynamicContext.getCurrentStepPlan();
        if (plan != null && StringUtils.hasText(plan.getTaskGoal())) {
            return plan.getTaskGoal();
        }
        return dynamicContext.getCurrentTask();
    }

    private static String normalizeAcceptedContent(String executionResult, SupervisionDecisionVO decision) {
        if (decision != null && StringUtils.hasText(decision.getAssessment())) {
            return compactForPrompt(decision.getAssessment(), MAX_ACCEPTED_CONTENT_LENGTH);
        }
        return compactForPrompt(stripToolReceipt(executionResult), MAX_ACCEPTED_CONTENT_LENGTH);
    }

    private static List<Map<String, Object>> buildAcceptedResultsPromptView(List<AcceptedResultVO> acceptedResults) {
        return acceptedResults.stream()
                .limit(MAX_PROMPT_ACCEPTED_RESULTS)
                .map(result -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("stepId", result.getStepId());
                    item.put("resultType", result.getResultType());
                    item.put("content", compactForPrompt(result.getContent(), MAX_ACCEPTED_CONTENT_LENGTH));
                    item.put("acceptedByRound", result.getAcceptedByRound());
                    item.put("acceptedReason", compactForPrompt(result.getAcceptedReason(), MAX_ACCEPTED_REASON_LENGTH));
                    item.put("evidenceRefs", result.getEvidenceRefs() == null ? List.of() : result.getEvidenceRefs());
                    return item;
                })
                .toList();
    }

    private static Map<Integer, Map<String, Object>> buildRoundArchivePromptView(
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        Map<Integer, Map<String, Object>> promptArchive = new LinkedHashMap<>();
        if (dynamicContext == null || dynamicContext.getRoundArchive() == null || dynamicContext.getRoundArchive().isEmpty()) {
            return promptArchive;
        }

        List<Integer> rounds = dynamicContext.getRoundArchive().keySet().stream()
                .sorted()
                .toList();
        int start = Math.max(0, rounds.size() - MAX_PROMPT_ROUNDS);
        for (Integer round : rounds.subList(start, rounds.size())) {
            var archive = dynamicContext.getRoundArchive().get(round);
            if (archive == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("round", archive.getRound());
            item.put("node1PlanSnapshot", compactForPrompt(archive.getNode1PlanSnapshot(), MAX_PROMPT_TEXT_LENGTH));
            item.put("node2ExecutionSnapshot", compactForPrompt(archive.getNode2ExecutionSnapshot(), MAX_PROMPT_EXECUTION_LENGTH));
            item.put("node3VerificationSnapshot", compactForPrompt(archive.getNode3VerificationSnapshot(), MAX_PROMPT_TEXT_LENGTH));
            item.put("acceptedResults", buildAcceptedResultsPromptView(archive.getAcceptedResults() == null ? List.of() : archive.getAcceptedResults()));
            promptArchive.put(round, item);
        }
        return promptArchive;
    }

    private static Map<String, Object> buildTaskBoardPromptView(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        Map<String, Object> compactBoard = new LinkedHashMap<>();
        dynamicContext.getTaskBoard().forEach((stepId, item) -> {
            Map<String, Object> compactItem = new LinkedHashMap<>();
            compactItem.put("stepId", item.getStepId());
            compactItem.put("status", item.getStatus());
            compactItem.put("attemptCount", item.getAttemptCount());
            compactItem.put("lastRoundTask", compactForPrompt(item.getLastRoundTask(), MAX_PROMPT_TEXT_LENGTH));
            compactItem.put("lastFailureReason", compactForPrompt(item.getLastFailureReason(), MAX_ACCEPTED_REASON_LENGTH));
            compactItem.put("acceptedOutputs", item.getAcceptedOutputs() == null ? List.of()
                    : item.getAcceptedOutputs().stream()
                    .limit(3)
                    .map(output -> compactForPrompt(output, MAX_ACCEPTED_CONTENT_LENGTH))
                    .toList());
            compactBoard.put(stepId, compactItem);
        });
        return compactBoard;
    }

    private static Map<String, Object> buildRoundExecutionSummaryPromptView(RoundExecutionSummaryVO summary) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("toolRequired", summary.getToolRequired());
        item.put("toolInvoked", summary.getToolInvoked());
        item.put("invokedTools", summary.getInvokedTools() == null ? List.of() : summary.getInvokedTools());
        item.put("toolSuccess", summary.getToolSuccess());
        item.put("evidenceAvailable", summary.getEvidenceAvailable());
        item.put("evidenceSummary", compactForPrompt(summary.getEvidenceSummary(), MAX_PROMPT_TEXT_LENGTH));
        item.put("blockingReason", compactForPrompt(summary.getBlockingReason(), MAX_ACCEPTED_REASON_LENGTH));
        item.put("rawExecutionResult", compactForPrompt(summary.getRawExecutionResult(), MAX_PROMPT_EXECUTION_LENGTH));
        return item;
    }

    private static Map<String, Object> buildExecutionOutcomePromptView(ExecutionOutcomeVO executionOutcome) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("status", executionOutcome.getStatus());
        item.put("errorCode", executionOutcome.getErrorCode());
        item.put("errorMessage", compactForPrompt(executionOutcome.getErrorMessage(), MAX_ACCEPTED_REASON_LENGTH));
        item.put("rawResult", compactForPrompt(executionOutcome.getRawResult(), MAX_PROMPT_EXECUTION_LENGTH));
        return item;
    }

    private static String stripToolReceipt(String executionResult) {
        String raw = safe(executionResult);
        int receiptIndex = raw.indexOf("ToolReceipt:");
        if (receiptIndex < 0) {
            return raw;
        }
        return raw.substring(0, receiptIndex).trim();
    }

    private static String compactForPrompt(String text, int maxLength) {
        String normalized = safe(text)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(normalized) || maxLength <= 0 || normalized.length() <= maxLength) {
            return normalized;
        }
        int headLength = Math.max(32, maxLength - 24);
        return normalized.substring(0, Math.min(headLength, normalized.length()))
                + "...[truncated " + (normalized.length() - Math.min(headLength, normalized.length())) + " chars]";
    }
}
