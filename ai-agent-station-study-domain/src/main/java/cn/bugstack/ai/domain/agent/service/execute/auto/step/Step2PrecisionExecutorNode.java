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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Node2：执行节点，只消费 Node1 的结构化计划，不基于用户原始输入重规划。
 */
@Slf4j
@Service
public class Step2PrecisionExecutorNode extends AbstractExecuteSupport {

    private static final String TOOL_POLICY_CACHE_PREFIX = "tool_policy_cache_";

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter,
                             DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("阶段2: 精准任务执行(Node2)");

        StepExecutionPlanVO plan = dynamicContext.getCurrentStepPlan();
        if (plan == null) {
            throw new IllegalStateException("Node2 缺少 currentStepPlan，无法执行");
        }

        String sanitizedGoal = dynamicContext.getSanitizedUserGoal();
        if (!StringUtils.hasText(sanitizedGoal)) {
            throw new IllegalStateException("Node2 缺少 sanitizedUserGoal，拒绝继续执行");
        }

        AiAgentClientFlowConfigVO flowConfig = dynamicContext.getAiAgentClientFlowConfigVOMap()
                .get(AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(flowConfig.getClientId());

        Map<String, AiClientToolMcpVO.ToolPolicy> toolPolicyMap = getToolPolicyMap(flowConfig.getClientId(), dynamicContext);
        String policyCheckError = validatePlanAgainstToolPolicy(plan, toolPolicyMap);
        if (StringUtils.hasText(policyCheckError)) {
            dynamicContext.setValue("lastToolError", policyCheckError);
            throw new IllegalStateException(policyCheckError);
        }

        // 中文注释：参数提示不完整只告警不阻断，避免“缺 query 直接失败”
        String planWarning = buildToolArgsHintWarning(plan, toolPolicyMap);
        if (StringUtils.hasText(planWarning)) {
            sendExecutionSubResult(dynamicContext, "execution_plan_warning",
                    planWarning, requestParameter.getSessionId());
        }

        sendExecutionSubResult(dynamicContext, "execution_plan_read",
                JSON.toJSONString(plan), requestParameter.getSessionId());
        sendExecutionSubResult(dynamicContext, "execution_understanding",
                buildExecutionUnderstanding(plan, sanitizedGoal), requestParameter.getSessionId());

        String executionPrompt = buildExecutionPrompt(plan, sanitizedGoal, toolPolicyMap.get(plan.getToolName()));
        String executionResult = callExecutor(chatClient, executionPrompt, requestParameter, dynamicContext, flowConfig, plan, sanitizedGoal);

        // 参数错误允许一次重试（只修参数，不改任务）
        if (isInvalidArgsError(executionResult)) {
            dynamicContext.setValue("lastToolError", executionResult);
            String retryPrompt = buildRetryPrompt(plan, sanitizedGoal, toolPolicyMap.get(plan.getToolName()), executionResult);
            executionResult = callExecutor(chatClient, retryPrompt, requestParameter, dynamicContext, flowConfig, plan, sanitizedGoal);
        }

        parseExecutionResult(dynamicContext, executionResult, requestParameter.getSessionId());
        dynamicContext.setValue("executionResult", executionResult);
        dynamicContext.setValue("lastToolError", "");

        dynamicContext.getExecutionHistory().append(String.format("""
                === 第%d步执行记录(Node2) ===
                【执行计划】%s
                【执行结果】%s
                """, dynamicContext.getStep(), JSON.toJSONString(plan), executionResult));

        return router(requestParameter, dynamicContext);
    }

    private String callExecutor(ChatClient chatClient,
                                String prompt,
                                ExecuteCommandEntity requestParameter,
                                DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                AiAgentClientFlowConfigVO flowConfig,
                                StepExecutionPlanVO plan,
                                String sanitizedGoal) {
        try {
            return chatClient
                    .prompt(prompt)
                    .advisors(a -> {
                        a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, buildNodeConversationId(requestParameter.getSessionId(), "node2"))
                                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 30)
                                .param("qa_filter_expression", buildFilterExpression(requestParameter.getKnowledgeName()))
                                .param("qa_query", buildRagQuery(plan, sanitizedGoal))
                                // 中文注释：执行节点默认启用 RAG 注入，避免直答轮次出现“未检索即幻觉”
                                .param("qa_disable", false);
                        applyTokenStatParams(
                                a, dynamicContext, requestParameter,
                                flowConfig.getClientId(),
                                AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode()
                        );
                    })
                    .call()
                    .content();
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "node2 execute failed" : e.getMessage();
            return "执行结果: 调用失败\n质量检查: " + msg;
        }
    }

    private static String buildExecutionUnderstanding(StepExecutionPlanVO plan, String sanitizedGoal) {
        return String.format("""
                全局目标: %s
                本轮任务: %s
                是否需要工具: %s
                计划工具: %s
                工具目的: %s
                预期输出: %s
                """,
                safe(sanitizedGoal),
                safe(plan.getTaskGoal()),
                Boolean.TRUE.equals(plan.getToolRequired()),
                safe(plan.getToolName()),
                safe(plan.getToolPurpose()),
                safe(plan.getExpectedOutput()));
    }

    private static String buildFilterExpression(String knowledgeName) {
        if (!StringUtils.hasText(knowledgeName)) {
            return "";
        }
        return "knowledge == '" + knowledgeName.replace("'", "\\'") + "'";
    }

    private static String buildRagQuery(StepExecutionPlanVO plan, String sanitizedGoal) {
        String taskGoal = plan == null ? "" : plan.getTaskGoal();
        if (StringUtils.hasText(taskGoal)) {
            return taskGoal;
        }
        return sanitizedGoal;
    }

    @SuppressWarnings("unchecked")
    private Map<String, AiClientToolMcpVO.ToolPolicy> getToolPolicyMap(
            String clientId,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        String cacheKey = TOOL_POLICY_CACHE_PREFIX + clientId;
        Object cached = dynamicContext.getValue(cacheKey);
        if (cached instanceof Map<?, ?> map) {
            return (Map<String, AiClientToolMcpVO.ToolPolicy>) map;
        }

        List<AiClientToolMcpVO> tools = repository.AiClientToolMcpVOByClientIds(List.of(clientId));
        Map<String, AiClientToolMcpVO.ToolPolicy> result = new LinkedHashMap<>();
        for (AiClientToolMcpVO tool : tools) {
            result.put(tool.getMcpName(), tool.getToolPolicy());
        }
        dynamicContext.setValue(cacheKey, result);
        return result;
    }

    private String validatePlanAgainstToolPolicy(StepExecutionPlanVO plan,
                                                 Map<String, AiClientToolMcpVO.ToolPolicy> toolPolicyMap) {
        boolean toolRequired = Boolean.TRUE.equals(plan.getToolRequired());
        if (!toolRequired) {
            return "";
        }

        if (!StringUtils.hasText(plan.getToolName())) {
            return "Node2 计划要求调用工具，但未指定 toolName";
        }

        AiClientToolMcpVO.ToolPolicy policy = toolPolicyMap.get(plan.getToolName());
        if (policy == null) {
            return "Node2 指定工具未配置或不可用: " + plan.getToolName();
        }

        return "";
    }

    private String buildToolArgsHintWarning(StepExecutionPlanVO plan,
                                            Map<String, AiClientToolMcpVO.ToolPolicy> toolPolicyMap) {
        if (!Boolean.TRUE.equals(plan.getToolRequired()) || !StringUtils.hasText(plan.getToolName())) {
            return "";
        }

        AiClientToolMcpVO.ToolPolicy policy = toolPolicyMap.get(plan.getToolName());
        if (policy == null || policy.getRequiredArgs() == null || policy.getRequiredArgs().isEmpty()) {
            return "";
        }

        String hint = plan.getToolArgsHint() == null ? "" : plan.getToolArgsHint();
        List<String> missing = policy.getRequiredArgs().stream()
                .filter(arg -> !hasNamedArg(hint, arg))
                .collect(Collectors.toList());
        if (missing.isEmpty()) {
            return "";
        }
        return "计划参数提示未显式包含必填参数: " + String.join(",", missing) + "；Node2 将尝试按任务目标自动补齐。";
    }

    public static String buildExecutionPrompt(StepExecutionPlanVO plan,
                                              String sanitizedGoal,
                                              AiClientToolMcpVO.ToolPolicy toolPolicy) {
        String policyJson = toolPolicy == null ? "{}" : JSON.toJSONString(toolPolicy);
        return String.format("""
                你是 Node2 执行器，必须严格执行 Node1 计划，不得改写任务目标。
                【全局目标(sanitized)】
                %s

                【当前轮计划(JSON)】
                %s

                【工具策略(policy)】
                %s

                执行规则：
                1. 先确认你读取到的计划内容，再执行。
                2. 若 plan.toolRequired=false，不应调用工具。
                3. 若 plan.toolRequired=true，只能调用 plan.toolName。
                4. 工具参数不确定时，先给出文本结论，不传 undefined/null/空参数。
                输出格式（严格）：
                计划读取: ...
                工具决策: ...
                执行目标: ...
                执行过程: ...
                执行结果: ...
                证据与依据: ...
                质量检查: ...
                """, sanitizedGoal, JSON.toJSONString(plan), policyJson);
    }

    private static String buildRetryPrompt(StepExecutionPlanVO plan,
                                           String sanitizedGoal,
                                           AiClientToolMcpVO.ToolPolicy toolPolicy,
                                           String lastError) {
        String policyJson = toolPolicy == null ? "{}" : JSON.toJSONString(toolPolicy);
        return String.format("""
                你是 Node2 执行器。上一次调用因参数错误失败。
                本次仅允许修正参数，不允许变更任务目标与工具选择。
                【全局目标(sanitized)】
                %s

                【当前轮计划(JSON)】
                %s

                【工具策略(policy)】
                %s

                【上次错误】
                %s

                输出格式（严格）：
                计划读取: ...
                工具决策: ...
                执行目标: ...
                执行过程: ...
                执行结果: ...
                证据与依据: ...
                质量检查: ...
                """, sanitizedGoal, JSON.toJSONString(plan), policyJson, lastError);
    }

    private boolean isInvalidArgsError(String executionResult) {
        if (!StringUtils.hasText(executionResult)) {
            return false;
        }
        return executionResult.contains("Invalid arguments")
                || executionResult.contains("expected")
                || executionResult.contains("received undefined");
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity requestParameter,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step3QualitySupervisorNode");
    }

    private void parseExecutionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                      String executionResult, String sessionId) {
        int step = dynamicContext.getStep();
        log.info("=== 第{}步执行结果 ===", step);

        String[] lines = executionResult == null ? new String[0] : executionResult.split("\n");
        String currentSection = "";
        StringBuilder sectionContent = new StringBuilder();
        boolean hasStructuredHeader = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String section = detectExecutionSection(trimmed);
            if (StringUtils.hasText(section)) {
                hasStructuredHeader = true;
                sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = section;
                sectionContent.setLength(0);
                continue;
            }

            if (!currentSection.isEmpty()) {
                sectionContent.append(trimmed).append("\n");
            }
        }

        sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
        if (!hasStructuredHeader && StringUtils.hasText(executionResult)) {
            sendExecutionSubResult(dynamicContext, "execution_result", executionResult, sessionId);
        }
        sendExecutionSubResult(dynamicContext, "execution_raw_output", executionResult, sessionId);
    }

    private static String detectExecutionSection(String line) {
        if (line.startsWith("计划读取:") || line.startsWith("PlanRead:")) {
            return "execution_plan_read";
        }
        if (line.startsWith("工具决策:") || line.startsWith("ToolDecision:")) {
            return "execution_tool_decision";
        }
        if (line.startsWith("执行目标:") || line.startsWith("ExecutionTarget:")) {
            return "execution_target";
        }
        if (line.startsWith("执行过程:") || line.startsWith("ExecutionProcess:")) {
            return "execution_process";
        }
        if (line.startsWith("执行结果:") || line.startsWith("ExecutionResult:")) {
            return "execution_result";
        }
        if (line.startsWith("证据与依据:") || line.startsWith("Evidence:")) {
            return "execution_evidence";
        }
        if (line.startsWith("质量检查:") || line.startsWith("QualityCheck:")) {
            return "execution_quality";
        }
        return "";
    }

    private static String buildNodeConversationId(String sessionId, String nodeTag) {
        if (!StringUtils.hasText(sessionId)) {
            return nodeTag;
        }
        return sessionId + ":" + nodeTag;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasNamedArg(String hint, String argName) {
        if (!StringUtils.hasText(hint) || !StringUtils.hasText(argName)) {
            return false;
        }
        String normalized = hint.toLowerCase();
        String arg = argName.toLowerCase();
        return normalized.contains(arg + "=") || normalized.contains(arg + ":");
    }

    private void sendExecutionSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String subType, String content, String sessionId) {
        if (!StringUtils.hasText(subType) || !StringUtils.hasText(content)) {
            return;
        }
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createExecutionSubResult(
                dynamicContext.getStep(), subType, content, sessionId);
        sendSseResult(dynamicContext, result);
    }
}
