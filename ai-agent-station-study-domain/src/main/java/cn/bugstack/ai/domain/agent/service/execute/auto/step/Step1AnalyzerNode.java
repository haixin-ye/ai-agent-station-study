package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Node1：任务规划节点。
 * 职责：
 * 1. 基于全局目标和多轮历史，评估当前进度
 * 2. 给 Node2 下发本轮唯一执行计划（JSON）
 */
@Slf4j
@Service
public class Step1AnalyzerNode extends AbstractExecuteSupport {

    private static final Pattern LEGACY_NEXT_STEP_PATTERN =
            Pattern.compile("(?is)(?:下一步策略|下\\s*一\\s*步\\s*策\\s*略|执行目标|next\\s*step)\\s*[:：]\\s*(.+?)(?:\\n\\s*\\n|$)");
    private static final Pattern LEGACY_STATUS_PATTERN =
            Pattern.compile("(?is)(?:任务状态|是否通过|pass|status)\\s*[:：]\\s*(.+?)(?:\\n\\s*\\n|$)");

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
                executionHistory,
                currentTask,
                latestSupervision,
                latestExecution,
                planHistoryJson,
                allowedTools
        );

        String planningResult = chatClient
                .prompt(planningPrompt)
                .advisors(a -> {
                    a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, buildNodeConversationId(requestParameter.getSessionId(), "node1"))
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 30);
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

        dynamicContext.setCurrentStepPlan(plan);
        dynamicContext.getPlanHistory().put(round, plan);
        dynamicContext.setCurrentTask(plan.getTaskGoal());

        String planJson = JSON.toJSONString(plan);
        dynamicContext.getExecutionHistory().append(String.format("""
                === 第%d轮规划(Node1) ===
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

    private static String buildPlanningPrompt(int round,
                                              int maxStep,
                                              String rawUserGoal,
                                              String existingSanitizedGoal,
                                              String knowledgeName,
                                              String executionHistory,
                                              String currentTask,
                                              String latestSupervision,
                                              String latestExecution,
                                              String planHistoryJson,
                                              Set<String> allowedTools) {
        return String.format("""
                你是 Node1（任务规划器），职责是为 Node2 生成“本轮唯一可执行计划”。
                你必须同时考虑：
                1) 全局任务目标（rawUserGoal/sanitizedUserGoal）
                2) 历史计划与执行记录（planHistory/executionHistory）
                3) 上一轮监督结论（latestSupervision）
                你的目标不是写分析报告，而是安排下一步执行动作，推动总任务收敛。

                【硬性输出约束】
                - 只能输出 1 个 JSON 对象，不得输出解释、markdown、代码块。
                - 顶层字段必须且仅能包含：
                  planId, round, sanitizedUserGoal, taskGoal, toolRequired, toolName, toolPurpose, toolArgsHint, expectedOutput, completionHint
                - toolRequired=false 时，toolName 必须是空字符串。
                - toolRequired=true 时，toolName 必须从以下白名单选择：%s
                - 不得重复安排已经完成的动作；本轮只安排一个最关键动作。
                - 如果可以直接回答，toolRequired=false。
                - 如果必须使用工具，明确 toolPurpose；但 toolArgsHint 只给参数提示，不填具体参数值。
                - 若 knowledgeName 非空，且属于问答/总结/解释类任务，优先 toolRequired=false（让 Node2 走 RAG）。
                - 仅当必须外部检索、跨站搜索、发布写入时，才设置 toolRequired=true。
                - 若 toolName=baidu-search，toolArgsHint 必须显式包含 query=...

                【最小合法示例】
                {"planId":"plan-1-xxx","round":1,"sanitizedUserGoal":"...","taskGoal":"...","toolRequired":false,"toolName":"","toolPurpose":"","toolArgsHint":"","expectedOutput":"...","completionHint":"..."}

                【上下文】
                round=%d
                maxStep=%d
                rawUserGoal=%s
                existingSanitizedGoal=%s
                knowledgeName=%s
                currentTask=%s
                latestExecution=%s
                latestSupervision=%s
                planHistory=%s
                executionHistory=%s
                """,
                allowedTools,
                round, maxStep,
                safe(rawUserGoal),
                safe(existingSanitizedGoal),
                safe(knowledgeName),
                safe(currentTask),
                safe(latestExecution),
                safe(latestSupervision),
                safe(planHistoryJson),
                safe(executionHistory));
    }

    private StepExecutionPlanVO parsePlanOrFallback(String planningResult,
                                                    int round,
                                                    DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                    Set<String> allowedTools) {
        if (!StringUtils.hasText(planningResult)) {
            return buildFallbackPlan(round, dynamicContext, "Node1 返回为空");
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
            taskGoal = "直接回答用户问题（无需工具）";
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        boolean needToolByText = lowerText.contains("需要工具")
                || lowerText.contains("toolrequired: true")
                || lowerText.contains("toolrequired=true")
                || lowerText.contains("tool_required=true");
        String toolName = detectToolName(text, allowedTools);
        boolean toolRequired = needToolByText || StringUtils.hasText(toolName);
        if (!toolRequired) {
            toolName = "";
        }

        String completionHint = extractByPattern(text, LEGACY_STATUS_PATTERN);
        if (!StringUtils.hasText(completionHint)) {
            completionHint = "兼容旧格式输出并继续执行";
        }

        return StepExecutionPlanVO.builder()
                .planId("legacy-" + round + "-" + UUID.randomUUID())
                .round(round)
                .sanitizedUserGoal(sanitizedGoal)
                .taskGoal(taskGoal)
                .toolRequired(toolRequired)
                .toolName(toolName)
                .toolPurpose(toolRequired ? "按规划调用工具完成当前任务" : "")
                .toolArgsHint("")
                .expectedOutput("给出简洁准确答案")
                .completionHint(completionHint)
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
                .taskGoal("直接回答用户问题（无需工具）")
                .toolRequired(false)
                .toolName("")
                .toolPurpose("")
                .toolArgsHint("")
                .expectedOutput("给出简洁准确答案")
                .completionHint(reason)
                .build();
    }

    private void normalizePlan(StepExecutionPlanVO plan,
                               int round,
                               DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (!StringUtils.hasText(plan.getPlanId())) {
            plan.setPlanId("plan-" + round + "-" + UUID.randomUUID());
        }
        if (plan.getRound() == null) {
            plan.setRound(round);
        }

        if (!StringUtils.hasText(plan.getSanitizedUserGoal())) {
            String existing = dynamicContext.getSanitizedUserGoal();
            plan.setSanitizedUserGoal(StringUtils.hasText(existing) ? existing : dynamicContext.getRawUserGoal());
        }

        if (!StringUtils.hasText(dynamicContext.getSanitizedUserGoal())) {
            dynamicContext.setSanitizedUserGoal(plan.getSanitizedUserGoal());
        }

        if (!StringUtils.hasText(plan.getTaskGoal())) {
            plan.setTaskGoal("完成当前轮任务");
        }
        if (plan.getToolRequired() == null) {
            plan.setToolRequired(false);
        }
        if (!Boolean.TRUE.equals(plan.getToolRequired())) {
            plan.setToolName("");
            plan.setToolPurpose("");
            plan.setToolArgsHint("");
            return;
        }

        // 中文注释：为 baidu-search 自动补齐 query 提示，避免 Node2 因参数提示缺失而失败
        if ("baidu-search".equalsIgnoreCase(safe(plan.getToolName()))
                && !hasNamedArg(plan.getToolArgsHint(), "query")) {
            String seed = StringUtils.hasText(plan.getSanitizedUserGoal())
                    ? plan.getSanitizedUserGoal()
                    : plan.getTaskGoal();
            plan.setToolArgsHint("query=" + safe(seed));
        }
    }

    private void enforceToolNameWhitelist(StepExecutionPlanVO plan, Set<String> allowedTools) {
        if (!Boolean.TRUE.equals(plan.getToolRequired())) {
            return;
        }
        if (!StringUtils.hasText(plan.getToolName()) || !allowedTools.contains(plan.getToolName())) {
            plan.setToolRequired(false);
            plan.setToolName("");
            plan.setToolPurpose("工具名不在白名单，降级为直接回答");
            plan.setToolArgsHint("");
        }
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
                || normalized.contains("输入触发安全策略")
                || normalized.contains("已拒绝处理");
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
