package yhx.com.domain.agent.service.execute.auto.step;

import yhx.com.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import yhx.com.domain.agent.model.entity.CurrentRoundTaskVO;
import yhx.com.domain.agent.model.entity.ExecuteCommandEntity;
import yhx.com.domain.agent.model.entity.MasterPlanVO;
import yhx.com.domain.agent.model.entity.PlanStepVO;
import yhx.com.domain.agent.model.entity.SessionMemoryEntity;
import yhx.com.domain.agent.model.entity.StepExecutionPlanVO;
import yhx.com.domain.agent.model.entity.TaskBoardItemVO;
import yhx.com.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import yhx.com.domain.agent.model.valobj.AiClientToolMcpVO;
import yhx.com.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.AutoAgentParseModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.AutoAgentRecoveryLevelEnumVO;
import yhx.com.domain.agent.model.valobj.enums.NextRoundDirectiveTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.StepStatusEnumVO;
import yhx.com.domain.agent.service.execute.auto.contract.AutoAgentNodeContracts;
import yhx.com.domain.agent.service.execute.auto.contract.AutoAgentPromptContractSupport;
import yhx.com.domain.agent.service.execute.auto.support.SessionMemoryPromptSupport;
import yhx.com.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Step1：规划节点。
 *
 * <p>负责把当前轮任务整理成结构化计划，给后续执行节点使用。
 * 这里也是模型 JSON 输出的第一层容错点。
 */
@Slf4j
@Service
public class Step1AnalyzerNode extends AbstractExecuteSupport {

    private static final String NODE_ID = AutoAgentNodeContracts.STEP1.nodeId();

    private static final Pattern LEGACY_NEXT_STEP_PATTERN =
            Pattern.compile("(?is)(?:next\\s*step|taskgoal|task goal|下一步|当前任务|本轮任务|任务目标)\\s*[:：]\\s*(.+?)(?:\\n\\s*\\n|$)");
    private static final Pattern LEGACY_STATUS_PATTERN =
            Pattern.compile("(?is)(?:pass|status|completionhint|completion hint|完成状态|完成判断|状态|通过情况)\\s*[:：]\\s*(.+?)(?:\\n\\s*\\n|$)");

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter,
                             DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        int round = dynamicContext.getStep();
        log.info("=== Round {} planning(Node1) ===", round);

        AiAgentClientFlowConfigVO flowConfig = dynamicContext.getAiAgentClientFlowConfigVOMap()
                .get(AiClientTypeEnumVO.TASK_ANALYZER_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(flowConfig.getClientId());

        String rawUserGoal = dynamicContext.getRawUserGoal();
        String existingSanitizedGoal = dynamicContext.getSanitizedUserGoal();
        String executionHistory = dynamicContext.getExecutionHistory() == null
                ? ""
                : dynamicContext.getExecutionHistory().toString();
        String currentTask = dynamicContext.getCurrentTask();
        String latestSupervision = dynamicContext.getValue("supervisionResult");
        String latestExecution = dynamicContext.getValue("executionResult");
        String planHistoryJson = JSON.toJSONString(safePlanHistory(dynamicContext.getPlanHistory()));

        Set<String> allowedTools = loadAllowedToolNames(flowConfig.getClientId());
        String planningPrompt = buildPlanningPrompt(
                round,
                dynamicContext.getMaxStep(),
                rawUserGoal,
                existingSanitizedGoal,
                requestParameter.getKnowledgeName(),
                currentTask,
                latestSupervision,
                latestExecution,
                planHistoryJson,
                allowedTools,
                dynamicContext
        );

        String planningResult = chatClient
                .prompt(buildPlanningRequestPrompt(planningPrompt))
                .advisors(a -> {
                    a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, buildNodeConversationId(requestParameter.getSessionId(), "node1"))
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 8);
                    applyTokenStatParams(
                            a, dynamicContext, requestParameter,
                            flowConfig.getClientId(),
                            AiClientTypeEnumVO.TASK_ANALYZER_CLIENT.getCode()
                    );
                })
                .call()
                .content();

        StepExecutionPlanVO plan = parsePlanOrFallback(planningResult, round, dynamicContext, allowedTools);
        normalizePlan(plan, round, dynamicContext);
        enforceToolNameWhitelist(plan, allowedTools);
        enrichPlanWithSessionMemory(dynamicContext, plan);

        dynamicContext.setCurrentStepPlan(plan);
        dynamicContext.getPlanHistory().put(round, plan);
        dynamicContext.setCurrentTask(plan.getTaskGoal());
        syncStructuredPlanningState(dynamicContext, plan);
        AutoAgentPromptContractSupport.recordTrace(
                dynamicContext,
                AutoAgentNodeContracts.STEP1,
                safe(plan.getParseMode()),
                safe(plan.getRecoveryLevel()),
                Boolean.TRUE.equals(plan.getLowConfidence()),
                safe(plan.getCompletionHint()),
                AutoAgentNodeContracts.STEP1.primaryTruthSources()
        );

        String planJson = JSON.toJSONString(plan);
        dynamicContext.getExecutionHistory().append(String.format("""
                === Round %d Planning Result (Node1) ===
                %s
                """, round, planJson));

        sendAnalysisSubResult(dynamicContext, "analysis_round",
                "round=" + round + ", maxStep=" + dynamicContext.getMaxStep(),
                requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_current_task",
                safe(dynamicContext.getCurrentTask()),
                requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_last_supervision",
                safe(latestSupervision),
                requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_last_execution",
                safe(latestExecution),
                requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_sanitized_goal", plan.getSanitizedUserGoal(), requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_step_plan", planJson, requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_todo_list", buildTodoListText(dynamicContext), requestParameter.getSessionId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity requestParameter,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step2PrecisionExecutorNode");
    }

    private Set<String> loadAllowedToolNames(String clientId) {
        List<AiClientToolMcpVO> tools = repository.AiClientToolMcpVOByClientIds(List.of(clientId));
        return tools.stream()
                .map(AiClientToolMcpVO::getMcpName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private static Prompt buildPlanningRequestPrompt(String planningPrompt) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.NONE)
                .build();
        return new Prompt(planningPrompt, options);
    }

    private static String buildPlanningPrompt(int round,
                                              int maxStep,
                                              String rawUserGoal,
                                              String existingSanitizedGoal,
                                              String knowledgeName,
                                              String currentTask,
                                              String latestSupervision,
                                              String latestExecution,
                                              String planHistoryJson,
                                              Set<String> allowedTools,
                                              DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("planId", "plan-1-xxx");
        example.put("round", 1);
        example.put("sanitizedUserGoal", "...");
        example.put("taskGoal", "...");
        example.put("toolRequired", false);
        example.put("toolName", "");
        example.put("toolPurpose", "");
        example.put("toolArgsHint", "");
        example.put("expectedOutput", "...");
        example.put("sourceContent", "");
        example.put("completionHint", "...");

        Map<String, Object> planningContext = new LinkedHashMap<>();
        planningContext.put("round", round);
        planningContext.put("maxStep", maxStep);
        planningContext.put("rawUserGoal", safe(rawUserGoal));
        planningContext.put("existingSanitizedGoal", safe(existingSanitizedGoal));
        planningContext.put("knowledgeName", safe(knowledgeName));
        planningContext.put("sessionHistory", dynamicContext == null
                ? ""
                : safe(dynamicContext.getValue(SESSION_HISTORY_PROMPT_KEY)));
        planningContext.put("planningDigest", buildPlanningDigest(
                dynamicContext,
                currentTask,
                latestSupervision,
                latestExecution,
                planHistoryJson
        ));
        planningContext.put("currentRound", dynamicContext.getCurrentRound());
        planningContext.put("masterPlan", dynamicContext.getMasterPlan());
        planningContext.put("taskBoard", dynamicContext.getTaskBoard());
        planningContext.put("roundArchive", dynamicContext.getRoundArchive());
        planningContext.put("nextRoundDirective", dynamicContext.getNextRoundDirective());
        planningContext.put("overallStatus", dynamicContext.getOverallStatus());
        planningContext.put("allowedTools", allowedTools);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", "generate_current_round_plan");
        payload.put("nodeId", NODE_ID);
        payload.put("outputSchema", List.of(
                "planId",
                "round",
                "sanitizedUserGoal",
                "taskGoal",
                "toolRequired",
                "toolName",
                "toolPurpose",
                "toolArgsHint",
                "expectedOutput",
                "sourceContent",
                "completionHint"
        ));
        payload.put("constraints", List.of(
                "Return exactly one JSON object and nothing else.",
                "Only plan the current round, not the final answer.",
                "If toolRequired is false, toolName must be empty.",
                "If toolRequired is true, toolName must be chosen from allowedTools.",
                "toolArgsHint should contain argument hints only, not fabricated concrete results.",
                "If knowledgeName is present and this is mainly QA or explanation, prefer toolRequired=false so Node2 can rely on RAG.",
                "Only require a tool when external retrieval or side-effect operation is necessary.",
                "If the request is a single-shot QA/RAG/explanation task and the current round already satisfies the user's raw input, do not invent a post-answer confirmation round; keep the current round as the deliverable.",
                "If sessionHistory is present, use it only to preserve cross-session user intent continuity and not as proof that the current round is already completed.",
                "If the current round depends on prior content that Node2 cannot obtain by itself, put the exact reusable content into sourceContent instead of assuming Node2 can recover it.",
                "Use planningDigest, currentRound, masterPlan, taskBoard, roundArchive, nextRoundDirective, and overallStatus as the main planning state.",
                "If toolName is baidu-search, toolArgsHint should include query=..."
        ));
        payload.put("example", example);
        payload.put("context", planningContext);
        return JSON.toJSONString(AutoAgentPromptContractSupport.wrapPromptPayload(AutoAgentNodeContracts.STEP1, payload));
    }

    static Map<String, Object> buildPlanningDigest(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                   String currentTask,
                                                   String latestSupervision,
                                                   String latestExecution,
                                                   String planHistoryJson) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("currentTask", safe(currentTask));
        digest.put("currentRound", dynamicContext == null || dynamicContext.getCurrentRound() == null
                ? Map.of()
                : dynamicContext.getCurrentRound());
        digest.put("nextRoundDirective", dynamicContext == null || dynamicContext.getNextRoundDirective() == null
                ? Map.of()
                : dynamicContext.getNextRoundDirective());
        digest.put("overallStatus", dynamicContext == null || dynamicContext.getOverallStatus() == null
                ? Map.of()
                : dynamicContext.getOverallStatus());
        digest.put("recentPlanHistory", buildRecentPlanHistory(dynamicContext, 2));
        digest.put("taskBoardSummary", buildTaskBoardSummary(dynamicContext));
        digest.put("latestSupervision", trimForPrompt(latestSupervision, 1200));
        digest.put("latestExecution", trimForPrompt(latestExecution, 1200));
        digest.put("planHistoryDigest", trimForPrompt(planHistoryJson, 1200));
        digest.put("executionHistoryTail", tailPromptText(dynamicContext == null ? null : dynamicContext.getExecutionHistory(), 2200, 30));
        return digest;
    }

    private static List<Map<String, Object>> buildRecentPlanHistory(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                                    int maxItems) {
        if (dynamicContext == null || dynamicContext.getPlanHistory() == null || dynamicContext.getPlanHistory().isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        List<Map.Entry<Integer, StepExecutionPlanVO>> entries = new ArrayList<>(dynamicContext.getPlanHistory().entrySet());
        for (int i = Math.max(0, entries.size() - maxItems); i < entries.size(); i++) {
            StepExecutionPlanVO plan = entries.get(i).getValue();
            if (plan == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("round", plan.getRound());
            item.put("planId", plan.getPlanId());
            item.put("taskGoal", plan.getTaskGoal());
            item.put("toolRequired", plan.getToolRequired());
            item.put("toolName", plan.getToolName());
            item.put("completionHint", trimForPrompt(plan.getCompletionHint(), 300));
            recent.add(item);
        }
        return recent;
    }

    private static Map<String, Object> buildTaskBoardSummary(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (dynamicContext == null || dynamicContext.getTaskBoard() == null || dynamicContext.getTaskBoard().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        dynamicContext.getTaskBoard().forEach((stepId, item) -> {
            if (item == null) {
                return;
            }
            Map<String, Object> compact = new LinkedHashMap<>();
            compact.put("status", item.getStatus());
            compact.put("attemptCount", item.getAttemptCount());
            compact.put("lastRoundTask", trimForPrompt(item.getLastRoundTask(), 300));
            compact.put("lastFailureReason", trimForPrompt(item.getLastFailureReason(), 300));
            compact.put("acceptedOutputsSize", item.getAcceptedOutputs() == null ? 0 : item.getAcceptedOutputs().size());
            summary.put(stepId, compact);
        });
        return summary;
    }

    static String buildTodoListText(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("本轮规划清单\n");

        if (dynamicContext == null) {
            sb.append("\n- 暂无规划上下文\n");
            return sb.toString().trim();
        }

        if (dynamicContext.getMasterPlan() != null
                && dynamicContext.getMasterPlan().getMainSteps() != null
                && !dynamicContext.getMasterPlan().getMainSteps().isEmpty()) {
            int index = 1;
            for (PlanStepVO step : dynamicContext.getMasterPlan().getMainSteps()) {
                if (step == null) {
                    continue;
                }
                sb.append("\n").append(index++).append(". ");
                sb.append(StringUtils.hasText(step.getTitle()) ? trimForPrompt(step.getTitle(), 120) : safe(step.getStepId()));
                sb.append("\n");
                sb.append("   - 任务：").append(trimForPrompt(step.getGoal(), 240)).append("\n");
                sb.append("   - 完成标准：").append(trimForPrompt(step.getCompletionCriteria(), 240)).append("\n");
                sb.append("   - 状态：").append(formatStepStatus(step.getStatus())).append("\n");
            }
        } else if (dynamicContext.getCurrentRound() != null) {
            sb.append("\n1. 当前轮任务\n");
            sb.append("   - 任务：").append(trimForPrompt(dynamicContext.getCurrentRound().getRoundTask(), 300)).append('\n');
            sb.append("   - 完成标准：").append(trimForPrompt(dynamicContext.getCurrentRound().getExpectedEvidence(), 300)).append('\n');
            sb.append("   - 状态：").append(formatStepStatus(dynamicContext.getCurrentRound().getStatus())).append('\n');
        }

        if (dynamicContext.getNextRoundDirective() != null) {
            sb.append("\n下一步指令：")
                    .append(dynamicContext.getNextRoundDirective().getDirectiveType());
            if (StringUtils.hasText(dynamicContext.getNextRoundDirective().getTargetStepId())) {
                sb.append(" -> ").append(dynamicContext.getNextRoundDirective().getTargetStepId());
            }
            sb.append('\n');
        }
        if (dynamicContext.getOverallStatus() != null) {
            sb.append("总体状态：").append(dynamicContext.getOverallStatus().getState());
            if (StringUtils.hasText(dynamicContext.getOverallStatus().getFinalDecision())) {
                sb.append("（").append(dynamicContext.getOverallStatus().getFinalDecision()).append("）");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static String formatStepStatus(Object status) {
        if (status == null) {
            return "待开始";
        }
        String value = String.valueOf(status).toUpperCase(Locale.ROOT);
        return switch (value) {
            case "COMPLETED" -> "已完成";
            case "IN_PROGRESS" -> "进行中";
            case "FAILED" -> "失败";
            default -> "待开始";
        };
    }

    private static String tailPromptText(StringBuilder text, int maxChars, int maxLines) {
        if (text == null || text.length() == 0 || maxChars <= 0) {
            return "";
        }
        String raw = text.toString();
        if (raw.length() > maxChars) {
            raw = raw.substring(Math.max(0, raw.length() - maxChars));
        }
        if (maxLines <= 0) {
            return raw;
        }
        String[] lines = raw.split("\\R");
        if (lines.length <= maxLines) {
            return raw;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lines.length - maxLines); i < lines.length; i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private static String trimForPrompt(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return "";
        }
        String value = text.trim();
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(Math.max(0, value.length() - maxChars));
    }

    private StepExecutionPlanVO parsePlanOrFallback(String planningResult,
                                                    int round,
                                                    DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                    Set<String> allowedTools) {
        if (!StringUtils.hasText(planningResult)) {
            return buildFallbackPlan(round, dynamicContext, "Node1 returned empty content");
        }

        String text = sanitizeModelOutput(planningResult);
        if (isSecurityRejectedResponse(text)) {
            throw new IllegalStateException(text);
        }

        String jsonText = extractJson(text);
        try {
            StepExecutionPlanVO plan = JSON.parseObject(jsonText, StepExecutionPlanVO.class);
            if (plan == null) {
                return parseLegacyTextPlan(text, round, dynamicContext, allowedTools);
            }
            plan.setContractVersion(AutoAgentNodeContracts.STEP1.contractVersion());
            plan.setParseMode(text.trim().equals(jsonText.trim())
                    ? AutoAgentParseModeEnumVO.JSON.name()
                    : AutoAgentParseModeEnumVO.EXTRACTED_JSON.name());
            if (!text.trim().equals(jsonText.trim())) {
                plan.setRecoveryLevel(AutoAgentRecoveryLevelEnumVO.FORMAT_NOISE.name());
            }
            if (plan.getLowConfidence() == null) {
                plan.setLowConfidence(false);
            }
            return plan;
        } catch (Exception e) {
            log.warn("Node1 JSON parse failed, fallback to legacy parser. raw={}", text);
            return parseLegacyTextPlan(text, round, dynamicContext, allowedTools);
        }
    }

    private StepExecutionPlanVO parseLegacyTextPlan(String rawText,
                                                    int round,
                                                    DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                    Set<String> allowedTools) {
        String text = rawText == null ? "" : rawText;
        String sanitizedGoal = dynamicContext.getSanitizedUserGoal();
        if (!StringUtils.hasText(sanitizedGoal)) {
            sanitizedGoal = dynamicContext.getRawUserGoal();
        }

        String taskGoal = extractByPattern(text, LEGACY_NEXT_STEP_PATTERN);
        if (!StringUtils.hasText(taskGoal)) {
            taskGoal = "answer the user directly without tools";
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        boolean needToolByText = lowerText.contains("need tool")
                || lowerText.contains("need tools")
                || lowerText.contains("toolrequired: true")
                || lowerText.contains("toolrequired=true")
                || lowerText.contains("tool_required=true")
                || text.contains("需要工具")
                || text.contains("调用工具")
                || text.contains("使用工具")
                || text.contains("需要调用")
                || text.contains("工具必需")
                || text.contains("工具必须");
        String toolName = detectToolName(text, allowedTools);
        boolean toolRequired = needToolByText || StringUtils.hasText(toolName);
        if (!toolRequired) {
            toolName = "";
        }

        String completionHint = extractByPattern(text, LEGACY_STATUS_PATTERN);
        if (!StringUtils.hasText(completionHint)) {
            completionHint = "legacy text output parsed and continued";
        }

        return StepExecutionPlanVO.builder()
                .planId("legacy-" + round + "-" + UUID.randomUUID())
                .round(round)
                .sanitizedUserGoal(sanitizedGoal)
                .taskGoal(taskGoal)
                .toolRequired(toolRequired)
                .toolName(toolName)
                .toolPurpose(toolRequired ? "use tool for current task" : "")
                .toolArgsHint("")
                .expectedOutput("provide a concise and accurate answer")
                .completionHint(completionHint)
                .lowConfidence(true)
                .recoveryLevel(AutoAgentRecoveryLevelEnumVO.SEMANTIC_UNCERTAIN.name())
                .parseMode(AutoAgentParseModeEnumVO.LEGACY.name())
                .contractVersion(AutoAgentNodeContracts.STEP1.contractVersion())
                .build();
    }

    private StepExecutionPlanVO buildFallbackPlan(int round,
                                                  DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                  String reason) {
        String sanitizedGoal = dynamicContext.getSanitizedUserGoal();
        if (!StringUtils.hasText(sanitizedGoal)) {
            sanitizedGoal = dynamicContext.getRawUserGoal();
        }

        return StepExecutionPlanVO.builder()
                .planId("fallback-" + round + "-" + UUID.randomUUID())
                .round(round)
                .sanitizedUserGoal(sanitizedGoal)
                .taskGoal("answer the user directly without tools")
                .toolRequired(false)
                .toolName("")
                .toolPurpose("")
                .toolArgsHint("")
                .expectedOutput("provide a concise and accurate answer")
                .completionHint(reason)
                .lowConfidence(true)
                .recoveryLevel(AutoAgentRecoveryLevelEnumVO.CONTRACT_VIOLATION.name())
                .parseMode(AutoAgentParseModeEnumVO.FALLBACK.name())
                .contractVersion(AutoAgentNodeContracts.STEP1.contractVersion())
                .build();
    }

    private void normalizePlan(StepExecutionPlanVO plan,
                               int round,
                               DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (!StringUtils.hasText(plan.getPlanId())) {
            plan.setPlanId("plan-" + round + "-" + UUID.randomUUID());
        }
        if (!StringUtils.hasText(plan.getContractVersion())) {
            plan.setContractVersion(AutoAgentNodeContracts.STEP1.contractVersion());
        }
        if (!StringUtils.hasText(plan.getParseMode())) {
            plan.setParseMode(AutoAgentParseModeEnumVO.JSON.name());
        }
        if (plan.getLowConfidence() == null) {
            plan.setLowConfidence(false);
        }
        if (plan.getRound() == null) {
            plan.setRound(round);
        }

        if (!StringUtils.hasText(plan.getSanitizedUserGoal())) {
            String existing = dynamicContext.getSanitizedUserGoal();
            plan.setSanitizedUserGoal(StringUtils.hasText(existing) ? existing : dynamicContext.getRawUserGoal());
            markStructuredRecovery(plan);
        }

        if (!StringUtils.hasText(dynamicContext.getSanitizedUserGoal())) {
            dynamicContext.setSanitizedUserGoal(plan.getSanitizedUserGoal());
        }

        if (!StringUtils.hasText(plan.getTaskGoal())) {
            plan.setTaskGoal("complete the current round task");
            markStructuredRecovery(plan);
        }
        if (plan.getToolRequired() == null) {
            plan.setToolRequired(false);
            markStructuredRecovery(plan);
        }
        if (!Boolean.TRUE.equals(plan.getToolRequired())) {
            plan.setToolName("");
            plan.setToolPurpose("");
            plan.setToolArgsHint("");
            return;
        }

        // For baidu-search, fill a minimal query hint when planner omitted it.
        if ("baidu-search".equalsIgnoreCase(safe(plan.getToolName()))
                && !hasNamedArg(plan.getToolArgsHint(), "query")) {
            String seed = StringUtils.hasText(plan.getSanitizedUserGoal())
                    ? plan.getSanitizedUserGoal()
                    : plan.getTaskGoal();
            plan.setToolArgsHint("query=" + safe(seed));
        }
    }

    public static void enrichPlanWithSessionMemory(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                   StepExecutionPlanVO plan) {
        if (dynamicContext == null || plan == null) {
            return;
        }
        if (!shouldCarrySourceContent(dynamicContext, plan)) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<SessionMemoryEntity> sessionHistory = dynamicContext.getValue(SESSION_HISTORY_KEY);
        String latestAnswer = SessionMemoryPromptSupport.extractLatestFinalAnswer(sessionHistory);
        if (!StringUtils.hasText(latestAnswer)) {
            return;
        }

        plan.setSourceContent(latestAnswer);
        if (!safe(plan.getTaskGoal()).toLowerCase(Locale.ROOT).contains("sourcecontent")) {
            plan.setTaskGoal(plan.getTaskGoal() + " Use sourceContent as the exact content payload.");
        }
        if (!StringUtils.hasText(plan.getExpectedOutput())) {
            plan.setExpectedOutput("Use sourceContent exactly when the task requires prior generated content.");
        }
        if (!safe(plan.getCompletionHint()).toLowerCase(Locale.ROOT).contains("sourcecontent")) {
            String prefix = StringUtils.hasText(plan.getCompletionHint()) ? plan.getCompletionHint() + " " : "";
            plan.setCompletionHint(prefix + "Do not continue unless sourceContent is actually used.");
        }
    }

    private static boolean shouldCarrySourceContent(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                    StepExecutionPlanVO plan) {
        String rawUserGoal = safe(dynamicContext.getRawUserGoal()).toLowerCase(Locale.ROOT);
        String taskGoal = safe(plan.getTaskGoal()).toLowerCase(Locale.ROOT);
        String combined = rawUserGoal + "\n" + taskGoal;
        boolean referencesPriorContent = combined.contains("previous")
                || combined.contains("上一次")
                || combined.contains("上一轮")
                || combined.contains("上一篇")
                || combined.contains("刚才")
                || combined.contains("刚刚")
                || combined.contains("这篇")
                || combined.contains("这段内容")
                || combined.contains("这段");
        boolean needsReuseAction = combined.contains("publish")
                || combined.contains("发布")
                || combined.contains("改写")
                || combined.contains("润色")
                || combined.contains("翻译")
                || combined.contains("续写");
        return referencesPriorContent && needsReuseAction;
    }

    private void enforceToolNameWhitelist(StepExecutionPlanVO plan, Set<String> allowedTools) {
        if (!Boolean.TRUE.equals(plan.getToolRequired())) {
            return;
        }
        if (!StringUtils.hasText(plan.getToolName()) || !allowedTools.contains(plan.getToolName())) {
            plan.setToolRequired(false);
            plan.setToolName("");
            plan.setToolPurpose("tool name not in whitelist, downgrade to direct answer");
            plan.setToolArgsHint("");
            markStructuredRecovery(plan);
        }
    }

    private void markStructuredRecovery(StepExecutionPlanVO plan) {
        if (plan == null) {
            return;
        }
        plan.setLowConfidence(true);
        if (!StringUtils.hasText(plan.getRecoveryLevel())) {
            plan.setRecoveryLevel(AutoAgentRecoveryLevelEnumVO.STRUCTURE_RECOVERABLE.name());
        }
    }

    static void syncStructuredPlanningState(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                            StepExecutionPlanVO plan) {
        if (dynamicContext == null || plan == null) {
            return;
        }

        int round = plan.getRound() == null ? dynamicContext.getStep() : plan.getRound();
        String stepId = resolvePlanningStepId(dynamicContext, round);

        if (dynamicContext.getMasterPlan() == null) {
            dynamicContext.setMasterPlan(MasterPlanVO.builder()
                    .planVersion(1)
                    .mainSteps(new ArrayList<>())
                    .sessionGoal(dynamicContext.getSessionGoal())
                    .build());
        }

        PlanStepVO planStep = dynamicContext.getMasterPlan().getMainSteps().stream()
                .filter(item -> stepId.equals(item.getStepId()))
                .findFirst()
                .orElseGet(() -> {
                    PlanStepVO created = PlanStepVO.builder()
                            .stepId(stepId)
                            .status(StepStatusEnumVO.PENDING)
                            .dependencies(new ArrayList<>())
                            .build();
                    dynamicContext.getMasterPlan().getMainSteps().add(created);
                    return created;
                });
        planStep.setTitle("Round " + round);
        planStep.setGoal(plan.getTaskGoal());
        planStep.setCompletionCriteria(plan.getCompletionHint());
        planStep.setStatus(StepStatusEnumVO.PENDING);

        CurrentRoundTaskVO currentRound = CurrentRoundTaskVO.builder()
                .roundIndex(round)
                .currentStepId(stepId)
                .roundTask(plan.getTaskGoal())
                .suggestedTools(Boolean.TRUE.equals(plan.getToolRequired()) && StringUtils.hasText(plan.getToolName())
                        ? java.util.List.of(plan.getToolName()) : new ArrayList<>())
                .plannerNotes(plan.getToolPurpose())
                .expectedEvidence(plan.getExpectedOutput())
                .sourceContent(plan.getSourceContent())
                .toolRequired(Boolean.TRUE.equals(plan.getToolRequired()))
                .status(StepStatusEnumVO.PENDING)
                .build();
        dynamicContext.setCurrentRound(currentRound);

        TaskBoardItemVO item = dynamicContext.getTaskBoard().computeIfAbsent(stepId, key -> TaskBoardItemVO.builder()
                .stepId(stepId)
                .attemptCount(0)
                .acceptedOutputs(new ArrayList<>())
                .status(StepStatusEnumVO.PENDING)
                .build());
        item.setLastRoundTask(plan.getTaskGoal());
        item.setStatus(StepStatusEnumVO.PENDING);

        dynamicContext.getRoundArchive().computeIfAbsent(round,
                        key -> yhx.com.domain.agent.model.entity.RoundArchiveVO.builder().round(round).build())
                .setNode1PlanSnapshot(JSON.toJSONString(plan));
    }

    private static String resolvePlanningStepId(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                int round) {
        if (dynamicContext.getNextRoundDirective() != null
                && dynamicContext.getNextRoundDirective().getDirectiveType() == NextRoundDirectiveTypeEnumVO.REPLAN_SAME_STEP
                && StringUtils.hasText(dynamicContext.getNextRoundDirective().getTargetStepId())) {
            return dynamicContext.getNextRoundDirective().getTargetStepId();
        }
        return "step-" + round;
    }

    private static String extractJson(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int firstBrace = text.indexOf('{');
        if (firstBrace < 0) {
            return text;
        }
        int depth = 0;
        for (int i = firstBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(firstBrace, i + 1);
                }
            }
        }
        return text;
    }

    private static String sanitizeModelOutput(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 32 || c == '\n' || c == '\r' || c == '\t') && c != 0x7F) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private static String extractByPattern(String text, Pattern pattern) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String detectToolName(String text, Set<String> allowedTools) {
        if (!StringUtils.hasText(text) || allowedTools == null || allowedTools.isEmpty()) {
            return "";
        }
        for (String toolName : allowedTools) {
            if (StringUtils.hasText(toolName) && text.contains(toolName)) {
                return toolName;
            }
        }
        return "";
    }

    private static boolean isSecurityRejectedResponse(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("security_rejected")
                || normalized.contains("rejected by security guardrail")
                || normalized.contains("input rejected by security policy")
                || normalized.contains("request rejected");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasNamedArg(String hint, String argName) {
        if (!StringUtils.hasText(hint) || !StringUtils.hasText(argName)) {
            return false;
        }
        String normalized = hint.toLowerCase(Locale.ROOT);
        String arg = argName.toLowerCase(Locale.ROOT);
        return normalized.contains(arg + "=") || normalized.contains(arg + ":");
    }

    private static Map<Integer, StepExecutionPlanVO> safePlanHistory(Map<Integer, StepExecutionPlanVO> planHistory) {
        return planHistory == null ? Map.of() : planHistory;
    }

    private static String buildNodeConversationId(String sessionId, String nodeTag) {
        if (!StringUtils.hasText(sessionId)) {
            return nodeTag;
        }
        return sessionId + ":" + nodeTag;
    }

    private void sendAnalysisSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                       String subType, String content, String sessionId) {
        if (!StringUtils.hasText(subType) || !StringUtils.hasText(content)) {
            return;
        }
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createAnalysisSubResult(
                dynamicContext.getStep(), subType, content, sessionId);
        sendSseResult(dynamicContext, result);
    }
}
