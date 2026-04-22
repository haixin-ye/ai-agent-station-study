package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.entity.ExecutionOutcomeVO;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.RoundExecutionSummaryVO;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.entity.ToolExecutionRecordVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.armory.support.ToolCallCaptureHolder;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Node2闂佹寧绋掔喊宥嗘櫠閻ｅ本鍋樼€光偓閸愩剮婵嬫煟閹邦喗顏熺紒杈ㄧ箞瀹曪綁顢旈崨顖呫儵鎮?Node1 闂佹眹鍔岀€氼喚鍒掗妸鈺佸嚑闁告洦鍋勯褔鎮规担娴嬪亾閸愯尙浜伴梺鎸庣☉婵傛梻绮径鎰槬閺夌偞澹嗛懝楣冩煟椤剙濡介柛鈺傜洴瀹曘垽鎮㈡總澶嬬稄闁哄鐗婇幐鎼佸矗閸℃稒鐓傜€广儱鐗滃鎰版煕閹烘挸绔鹃柍?
 */
@Slf4j
@Service
public class Step2PrecisionExecutorNode extends AbstractExecuteSupport {

    private static final String TOOL_POLICY_CACHE_PREFIX = "tool_policy_cache_";
    private static final int MAX_EXECUTION_SNAPSHOT_LENGTH = 1200;
    private static final int MAX_EVIDENCE_SUMMARY_LENGTH = 900;
    private static final int MAX_TOOL_REQUEST_PREVIEW_LENGTH = 160;
    private static final int MAX_TOOL_RESPONSE_PREVIEW_LENGTH = 280;
    private static final int MAX_RECEIPT_PREVIEW_LENGTH = 600;
    private static final int MAX_TOOL_RECORDS_IN_SUMMARY = 3;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter,
                             DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Stage2: precision executor (Node2)");

        String sanitizedGoal = dynamicContext.getSanitizedUserGoal();
        if (!StringUtils.hasText(sanitizedGoal)) {
            throw new IllegalStateException("Node2 missing sanitizedUserGoal");
        }
        StepExecutionPlanVO plan = resolveExecutionPlan(dynamicContext);
        dynamicContext.setCurrentStepPlan(plan);

        AiAgentClientFlowConfigVO flowConfig = dynamicContext.getAiAgentClientFlowConfigVOMap()
                .get(AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(flowConfig.getClientId());

        Map<String, AiClientToolMcpVO.ToolPolicy> toolPolicyMap = getToolPolicyMap(flowConfig.getClientId(), dynamicContext);
        String toolName = resolvePrimaryToolName(dynamicContext, plan);
        String policyCheckError = validatePlanAgainstToolPolicy(dynamicContext, plan, toolPolicyMap);
        if (StringUtils.hasText(policyCheckError)) {
            dynamicContext.setValue("lastToolError", policyCheckError);
            throw new IllegalStateException(policyCheckError);
        }

        String planWarning = buildToolArgsHintWarning(plan, toolPolicyMap);
        if (StringUtils.hasText(planWarning)) {
            sendExecutionSubResult(dynamicContext, "execution_plan_warning",
                    planWarning, requestParameter.getSessionId());
        }

        sendExecutionSubResult(dynamicContext, "execution_plan_read",
                JSON.toJSONString(dynamicContext.getCurrentRound()), requestParameter.getSessionId());
        sendExecutionSubResult(dynamicContext, "execution_understanding",
                buildExecutionUnderstanding(dynamicContext, plan, sanitizedGoal), requestParameter.getSessionId());

        String executionPrompt = buildExecutionPrompt(dynamicContext, plan, sanitizedGoal, toolPolicyMap.get(toolName));
        log.info("Node2 tool visibility | sessionId={} | round={} | expectedTool={} | currentRoundTools={} | policyTools={}",
                requestParameter.getSessionId(),
                dynamicContext.getStep(),
                toolName,
                dynamicContext.getCurrentRound() == null ? List.of() : dynamicContext.getCurrentRound().getSuggestedTools(),
                toolPolicyMap.keySet());
        String missingSourceResult = validateRequiredSourceContent(dynamicContext, plan);
        if (StringUtils.hasText(missingSourceResult)) {
            syncExecutionState(dynamicContext, missingSourceResult);
            dynamicContext.setRoundExecutionSummary(buildRoundExecutionSummary(dynamicContext, missingSourceResult, plan));
            ExecutionOutcomeVO executionOutcome = buildExecutionOutcome(dynamicContext, missingSourceResult, plan);
            parseExecutionResult(dynamicContext, missingSourceResult, requestParameter.getSessionId(), executionOutcome);
            dynamicContext.setValue("executionResult", missingSourceResult);
            dynamicContext.setValue("executionOutcome", executionOutcome);
            dynamicContext.setValue("lastToolError", missingSourceResult);
            return router(requestParameter, dynamicContext);
        }
        String executionResult = callExecutor(chatClient, executionPrompt, requestParameter, dynamicContext, flowConfig, plan, sanitizedGoal);

        if (resolveToolRequired(dynamicContext, plan)
                && looksLikeToolIntentOnly(executionResult)
                && !hasCapturedToolRecord(dynamicContext)) {
            String toolIntentError = "model returned tool intent JSON instead of making a real tool call";
            dynamicContext.setValue("lastToolError", toolIntentError);
            String retryPrompt = buildRetryPrompt(dynamicContext, plan, sanitizedGoal, toolPolicyMap.get(toolName), toolIntentError);
            executionResult = callExecutor(chatClient, retryPrompt, requestParameter, dynamicContext, flowConfig, plan, sanitizedGoal);
            if (looksLikeToolIntentOnly(executionResult) && !hasCapturedToolRecord(dynamicContext)) {
                executionResult = """
                        ExecutionResult: tool was not actually invoked
                        QualityCheck: model returned tool intent JSON instead of performing a real tool call
                        """;
            }
        }

        if (isInvalidArgsError(executionResult)) {
            dynamicContext.setValue("lastToolError", executionResult);
            String retryPrompt = buildRetryPrompt(dynamicContext, plan, sanitizedGoal, toolPolicyMap.get(toolName), executionResult);
            executionResult = callExecutor(chatClient, retryPrompt, requestParameter, dynamicContext, flowConfig, plan, sanitizedGoal);
        }

        syncExecutionState(dynamicContext, executionResult);
        dynamicContext.setRoundExecutionSummary(buildRoundExecutionSummary(dynamicContext, executionResult, plan));
        ExecutionOutcomeVO executionOutcome = buildExecutionOutcome(dynamicContext, executionResult, plan);
        parseExecutionResult(dynamicContext, executionResult, requestParameter.getSessionId(), executionOutcome);
        dynamicContext.setValue("executionResult", executionResult);
        dynamicContext.setValue("executionOutcome", executionOutcome);
        dynamicContext.setValue("lastToolError", "");

        dynamicContext.getExecutionHistory().append(String.format("""
                === 缂?d濠殿喗绺块崕鍙夋櫠閻ｅ本鍋樼€光偓閸愭儳鏁归悷?Node2) ===
                闂侀潧妫欓崝鏍ㄦ櫠閻ｅ本鍋樼€光偓閸愭儳鎮侀梺鍛婂笚鐢洭鍩€?s
                闂侀潧妫欓崝鏍ㄦ櫠閻ｅ本鍋樼€光偓閳ь剛鍒掗妸鈺佸嚑婵犙冪氨閸?s
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
        ToolCallCaptureHolder.start();
        try {
            String content = chatClient
                    .prompt(prompt)
                    .advisors(a -> {
                        a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, buildNodeConversationId(requestParameter.getSessionId(), "node2"))
                                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 8)
                                .param("qa_filter_expression", buildFilterExpression(requestParameter.getKnowledgeName()))
                                .param("qa_query", buildRagQuery(plan, sanitizedGoal))
                                .param("qa_disable", false);
                        applyTokenStatParams(
                                a, dynamicContext, requestParameter,
                                flowConfig.getClientId(),
                                AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode()
                        );
                    })
                    .call()
                    .content();
            List<ToolExecutionRecordVO> callbackRecords = ToolCallCaptureHolder.finish();
            dynamicContext.setValue("callbackToolRecords", callbackRecords);
            log.info("Node2 callback capture result | sessionId={} | round={} | callbackCount={} | callbackTools={}",
                    requestParameter.getSessionId(),
                    dynamicContext.getStep(),
                    callbackRecords == null ? 0 : callbackRecords.size(),
                    callbackRecords == null ? List.of() : callbackRecords.stream().map(ToolExecutionRecordVO::getToolName).collect(Collectors.toList()));
            return appendCapturedReceipt(content, dynamicContext);
        }
        catch (Exception e) {
            List<ToolExecutionRecordVO> callbackRecords = ToolCallCaptureHolder.finish();
            dynamicContext.setValue("callbackToolRecords", callbackRecords);
            log.error("Node2 executor failed before valid completion | sessionId={} | round={} | callbackCount={} | error={}",
                    requestParameter.getSessionId(),
                    dynamicContext.getStep(),
                    callbackRecords == null ? 0 : callbackRecords.size(),
                    e.getMessage(),
                    e);
            String msg = e.getMessage() == null ? "node2 execute failed" : e.getMessage();
            return "ExecutionResult: tool call failed\nQualityCheck: " + msg;
        }
    }

    public static String buildExecutionUnderstanding(StepExecutionPlanVO plan, String sanitizedGoal) {
        return buildExecutionUnderstanding(null, plan, sanitizedGoal);
    }

    public static String buildExecutionUnderstanding(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                     StepExecutionPlanVO plan,
                                                     String sanitizedGoal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sanitizedGoal", safe(sanitizedGoal));
        payload.put("currentRound", dynamicContext == null || dynamicContext.getCurrentRound() == null
                ? Map.of()
                : dynamicContext.getCurrentRound());
        payload.put("taskGoal", resolveTaskGoal(dynamicContext, plan));
        payload.put("toolRequired", resolveToolRequired(dynamicContext, plan));
        payload.put("toolName", resolvePrimaryToolName(dynamicContext, plan));
        payload.put("toolPurpose", resolveToolPurpose(dynamicContext, plan));
        payload.put("expectedOutput", resolveExpectedOutput(dynamicContext, plan));
        payload.put("sourceContent", resolveSourceContent(dynamicContext, plan));
        return JSON.toJSONString(payload);
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

    private String validatePlanAgainstToolPolicy(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                 StepExecutionPlanVO plan,
                                                 Map<String, AiClientToolMcpVO.ToolPolicy> toolPolicyMap) {
        boolean toolRequired = resolveToolRequired(dynamicContext, plan);
        if (!toolRequired) {
            return "";
        }

        String toolName = resolvePrimaryToolName(dynamicContext, plan);
        if (!StringUtils.hasText(toolName)) {
            return "Node2 闁荤姳璁查埀顒€鍟块悘濠囨偡閺囨氨鍔嶉柣顏呭閹奉偊宕橀鍛閻庤鎮堕崕閬嶅矗閸ф鏅悘鐐跺亹缁嬪鏌￠崼顐＄凹閻庡灚姘ㄩ埀?toolName";
        }

        AiClientToolMcpVO.ToolPolicy policy = toolPolicyMap.get(toolName);
        if (policy == null) {
            return "Node2 闂佸湱顭堝ú銈夋偩閹屽晠闁靛鍎卞鏃堟煛閸偂閭柛妯稿€楃槐鏃堫敊閽樺浠愭繛鎴炴尭缁夌銇愰弻銉﹀仺? " + toolName;
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
        return "tool args hint is missing required params: " + String.join(",", missing) + "; Node2 will try to infer them from the task goal.";
    }

    public static String buildExecutionPrompt(StepExecutionPlanVO plan,
                                              String sanitizedGoal,
                                              AiClientToolMcpVO.ToolPolicy toolPolicy) {
        return buildExecutionPrompt(null, plan, sanitizedGoal, toolPolicy);
    }

    public static String buildExecutionPrompt(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                              StepExecutionPlanVO plan,
                                              String sanitizedGoal,
                                              AiClientToolMcpVO.ToolPolicy toolPolicy) {
        String policyJson = toolPolicy == null ? "{}" : JSON.toJSONString(toolPolicy);
        return String.format("""
                TASK: execute_current_round_task

                You are Node2. Actually complete the current round task.
                Do not produce a fake execution report.

                RawUserInput:
                %s

                SanitizedGoal:
                %s

                CurrentRound:
                %s

                SourceContent:
                %s

                ExecutionIntent:
                %s

                ToolPolicy:
                %s

                Rules:
                1. Treat currentRound as the primary task contract.
                2. If toolRequired is false, answer directly and briefly.
                3. If toolRequired is true, make a real Spring AI tool call before writing any conclusion.
                4. Use the real tool schema exposed by Spring AI; do not output a tool-call JSON for the user.
                5. Never send undefined, null, or empty placeholders for required args.
                6. Never invent ToolReceipt, side effects, file paths, URLs, or final success.
                7. If the task depends on sourceContent and sourceContent is empty, stop and report MISSING_REQUIRED_SOURCE_CONTENT.
                8. Never replace missing sourceContent with templates, placeholders, or guessed body text.

                Response:
                - Return a concise execution summary only.
                - If blocked or failed, state the real blocking reason only.
                - Do not output a standalone JSON object that merely describes a tool call.
                """,
                safe(dynamicContext == null ? "" : dynamicContext.getRawUserGoal()),
                safe(sanitizedGoal),
                JSON.toJSONString(dynamicContext == null || dynamicContext.getCurrentRound() == null ? Map.of() : dynamicContext.getCurrentRound()),
                compactForContext(resolveSourceContent(dynamicContext, plan), MAX_EXECUTION_SNAPSHOT_LENGTH),
                JSON.toJSONString(Map.of(
                        "taskGoal", resolveTaskGoal(dynamicContext, plan),
                        "toolRequired", resolveToolRequired(dynamicContext, plan),
                        "toolName", resolvePrimaryToolName(dynamicContext, plan),
                        "toolPurpose", resolveToolPurpose(dynamicContext, plan),
                        "expectedOutput", resolveExpectedOutput(dynamicContext, plan),
                        "sourceContent", compactForContext(resolveSourceContent(dynamicContext, plan), MAX_EXECUTION_SNAPSHOT_LENGTH)
                )),
                policyJson
        );
    }

    private static String buildRetryPrompt(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                           StepExecutionPlanVO plan,
                                           String sanitizedGoal,
                                           AiClientToolMcpVO.ToolPolicy toolPolicy,
                                           String lastError) {
        String policyJson = toolPolicy == null ? "{}" : JSON.toJSONString(toolPolicy);
        return String.format("""
                TASK: retry_execute_current_round_task

                Retry the same current round task.
                Your previous attempt failed or returned invalid output.

                RawUserInput:
                %s

                SanitizedGoal:
                %s

                CurrentRound:
                %s

                SourceContent:
                %s

                ToolPolicy:
                %s

                LastError:
                %s

                Rules:
                1. Only repair the tool invocation or execution details.
                2. Do not change the task goal.
                3. Make a real tool call before writing any conclusion.
                4. Do not output a tool-intent JSON object.
                5. Do not fabricate ToolReceipt or final success.
                6. If sourceContent is required but empty, stop and report MISSING_REQUIRED_SOURCE_CONTENT.
                """,
                safe(dynamicContext == null ? "" : dynamicContext.getRawUserGoal()),
                safe(sanitizedGoal),
                JSON.toJSONString(dynamicContext == null || dynamicContext.getCurrentRound() == null ? Map.of() : dynamicContext.getCurrentRound()),
                compactForContext(resolveSourceContent(dynamicContext, plan), MAX_EXECUTION_SNAPSHOT_LENGTH),
                policyJson,
                safe(lastError)
        );
    }

    private boolean isInvalidArgsError(String executionResult) {
        if (!StringUtils.hasText(executionResult)) {
            return false;
        }
        return executionResult.contains("Invalid arguments")
                || executionResult.contains("expected")
                || executionResult.contains("received undefined")
                || executionResult.contains("missing required");
    }

    private static boolean hasCapturedToolRecord(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (dynamicContext == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        List<ToolExecutionRecordVO> callbackRecords = dynamicContext.getValue("callbackToolRecords");
        return callbackRecords != null && !callbackRecords.isEmpty();
    }

    static StepExecutionPlanVO resolveExecutionPlan(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        CurrentRoundTaskVO currentRound = dynamicContext == null ? null : dynamicContext.getCurrentRound();
        if (currentRound != null && StringUtils.hasText(currentRound.getRoundTask())) {
            return StepExecutionPlanVO.builder()
                    .planId(StringUtils.hasText(currentRound.getCurrentStepId()) ? currentRound.getCurrentStepId() : "round-" + currentRound.getRoundIndex())
                    .round(currentRound.getRoundIndex() == null ? 1 : currentRound.getRoundIndex())
                    .taskGoal(currentRound.getRoundTask())
                    .toolRequired(Boolean.TRUE.equals(currentRound.getToolRequired()))
                    .toolName(currentRound.getSuggestedTools() == null || currentRound.getSuggestedTools().isEmpty()
                            ? ""
                            : safe(currentRound.getSuggestedTools().get(0)))
                    .toolPurpose(safe(currentRound.getPlannerNotes()))
                    .expectedOutput(safe(currentRound.getExpectedEvidence()))
                    .sourceContent(safe(currentRound.getSourceContent()))
                    .build();
        }
        StepExecutionPlanVO legacyPlan = dynamicContext == null ? null : dynamicContext.getCurrentStepPlan();
        if (legacyPlan != null) {
            return legacyPlan;
        }
        throw new IllegalStateException("Node2 missing currentRound/currentStepPlan");
    }

    public static ExecutionOutcomeVO buildExecutionOutcome(String executionResult) {
        return buildExecutionOutcome(executionResult, null);
    }
    public static ExecutionOutcomeVO buildExecutionOutcome(String executionResult, StepExecutionPlanVO plan) {
        return buildExecutionOutcome(null, executionResult, plan);
    }

    public static ExecutionOutcomeVO buildExecutionOutcome(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                           String executionResult,
                                                           StepExecutionPlanVO plan) {
        String raw = safe(executionResult);
        String lower = raw.toLowerCase(Locale.ROOT);
        boolean missingSourceContent = lower.contains("missing_required_source_content");
        boolean blocked = lower.contains("blocked") || lower.contains("policy violation");
        boolean failed = lower.contains("failed") || lower.contains("璋冪敤澶辫触");
        boolean explicitMissingReceipt = lower.contains("no structured tool receipt")
                || lower.contains("missing tool receipt")
                || lower.contains("鏈敹鍒扮粨鏋勫寲宸ュ叿鎵ц鍑瘉");
        boolean toolRequired = resolveToolRequired(dynamicContext, plan);
        RoundExecutionSummaryVO summary = dynamicContext == null ? null : dynamicContext.getRoundExecutionSummary();
        if (missingSourceContent) {
            return ExecutionOutcomeVO.builder()
                    .status(ExecutionOutcomeVO.FAILED)
                    .errorCode("MISSING_REQUIRED_SOURCE_CONTENT")
                    .errorMessage("Missing required source content")
                    .rawResult(raw)
                    .build();
        }
        if (toolRequired && summary != null) {
            if (!Boolean.TRUE.equals(summary.getToolInvoked())) {
                return ExecutionOutcomeVO.builder()
                        .status(ExecutionOutcomeVO.FAILED)
                        .errorCode("MISSING_TOOL_INVOCATION")
                        .errorMessage(defaultIfBlank(summary.getBlockingReason(), "Tool required but not actually invoked"))
                        .rawResult(raw)
                        .build();
            }
            if (!Boolean.TRUE.equals(summary.getToolSuccess())) {
                return ExecutionOutcomeVO.builder()
                        .status(ExecutionOutcomeVO.FAILED)
                        .errorCode("TOOL_EXECUTION_FAILED")
                        .errorMessage(defaultIfBlank(summary.getBlockingReason(), "Tool execution failed"))
                        .rawResult(raw)
                        .build();
            }
            if (!Boolean.TRUE.equals(summary.getEvidenceAvailable())) {
                return ExecutionOutcomeVO.builder()
                        .status(ExecutionOutcomeVO.FAILED)
                        .errorCode("MISSING_TOOL_EVIDENCE")
                        .errorMessage(defaultIfBlank(summary.getBlockingReason(), "Tool execution lacks credible evidence"))
                        .rawResult(raw)
                        .build();
            }
            return ExecutionOutcomeVO.builder()
                    .status(ExecutionOutcomeVO.SUCCESS)
                    .rawResult(raw)
                    .build();
        }
        boolean hasReceipt = StringUtils.hasText(extractToolReceipt(raw));
        boolean verifiedToolRecord = hasSuccessfulToolRecord(dynamicContext);
        boolean verifiedPostcondition = hasVerifiedPostcondition(dynamicContext, raw);

        if (toolRequired && !(verifiedToolRecord || verifiedPostcondition || hasReceipt)) {
            return ExecutionOutcomeVO.builder()
                    .status(ExecutionOutcomeVO.FAILED)
                    .errorCode("MISSING_TOOL_RECEIPT")
                    .errorMessage("Missing verified tool execution evidence")
                    .rawResult(raw)
                    .build();
        }
        if (dynamicContext != null
                && toolRequired
                && isPostconditionRequired(dynamicContext, plan)
                && !verifiedPostcondition) {
            return ExecutionOutcomeVO.builder()
                    .status(ExecutionOutcomeVO.FAILED)
                    .errorCode("POSTCONDITION_NOT_VERIFIED")
                    .errorMessage("Side-effect task is missing verified postcondition")
                    .rawResult(raw)
                    .build();
        }
        if (explicitMissingReceipt && !(verifiedToolRecord || verifiedPostcondition)) {
            return ExecutionOutcomeVO.builder()
                    .status(ExecutionOutcomeVO.FAILED)
                    .errorCode("MISSING_TOOL_RECEIPT")
                    .errorMessage("Missing verified tool execution evidence")
                    .rawResult(raw)
                    .build();
        }
        if (blocked) {
            return ExecutionOutcomeVO.builder()
                    .status(ExecutionOutcomeVO.FAILED)
                    .errorCode("POLICY_BLOCKED")
                    .errorMessage("Execution blocked by policy")
                    .rawResult(raw)
                    .build();
        }
        if (failed) {
            return ExecutionOutcomeVO.builder()
                    .status(ExecutionOutcomeVO.FAILED)
                    .errorCode("EXECUTION_FAILED")
                    .errorMessage("Execution failed")
                    .rawResult(raw)
                    .build();
        }
        return ExecutionOutcomeVO.builder()
                .status(ExecutionOutcomeVO.SUCCESS)
                .rawResult(raw)
                .build();
    }

    public static boolean hasRelaxedToolEvidence(String executionResult) {
        String raw = safe(executionResult);
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("toolreceipt:")) {
            return true;
        }
        if (lower.contains("filesystem postcondition verified") || lower.contains("csdn postcondition verified")) {
            return true;
        }
        return StringUtils.hasText(verifyFilesystemPostcondition(raw));
    }

    static RoundExecutionSummaryVO buildRoundExecutionSummary(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                              String executionResult,
                                                              StepExecutionPlanVO plan) {
        boolean toolRequired = resolveToolRequired(dynamicContext, plan);
        List<ToolExecutionRecordVO> roundRecords = getCurrentRoundToolRecords(dynamicContext);
        List<ToolExecutionRecordVO> successRecords = roundRecords.stream()
                .filter(record -> record != null && Boolean.TRUE.equals(record.getSuccess()))
                .toList();
        List<ToolExecutionRecordVO> failedRecords = roundRecords.stream()
                .filter(record -> record != null && !Boolean.TRUE.equals(record.getSuccess()))
                .toList();

        boolean toolInvoked = !roundRecords.isEmpty() || StringUtils.hasText(extractToolReceipt(executionResult));
        boolean toolSuccess = !successRecords.isEmpty();
        String evidenceSummary = buildEvidenceSummary(dynamicContext, successRecords, executionResult);
        boolean evidenceAvailable = StringUtils.hasText(evidenceSummary)
                || (!toolRequired && StringUtils.hasText(safe(executionResult)));

        return RoundExecutionSummaryVO.builder()
                .toolRequired(toolRequired)
                .toolInvoked(toolInvoked)
                .invokedTools(roundRecords.stream()
                        .map(ToolExecutionRecordVO::getToolName)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .collect(Collectors.toList()))
                .toolSuccess(toolRequired ? toolSuccess : !hasExecutionFailureSignal(executionResult))
                .evidenceAvailable(evidenceAvailable)
                .evidenceSummary(evidenceSummary)
                .blockingReason(buildBlockingReason(failedRecords, executionResult, toolRequired, toolInvoked, evidenceAvailable))
                .rawExecutionResult(compactForContext(executionResult, MAX_EXECUTION_SNAPSHOT_LENGTH))
                .build();
    }

    static void syncExecutionState(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                   String executionResult) {
        if (dynamicContext == null) {
            return;
        }
        int round = dynamicContext.getStep();
        dynamicContext.getRoundArchive()
                .computeIfAbsent(round, key -> cn.bugstack.ai.domain.agent.model.entity.RoundArchiveVO.builder().round(round).build())
                .setNode2ExecutionSnapshot(compactForContext(executionResult, MAX_EXECUTION_SNAPSHOT_LENGTH));

        CurrentRoundTaskVO currentRound = dynamicContext.getCurrentRound();
        String stepId = currentRound != null && StringUtils.hasText(currentRound.getCurrentStepId())
                ? currentRound.getCurrentStepId()
                : safe(dynamicContext.getCurrentStepPlan() == null ? null : dynamicContext.getCurrentStepPlan().getPlanId());
        String toolName = currentRound != null && currentRound.getSuggestedTools() != null && !currentRound.getSuggestedTools().isEmpty()
                ? safe(currentRound.getSuggestedTools().get(0))
                : safe(dynamicContext.getCurrentStepPlan() == null ? null : dynamicContext.getCurrentStepPlan().getToolName());

        @SuppressWarnings("unchecked")
        List<ToolExecutionRecordVO> callbackRecords = dynamicContext.getValue("callbackToolRecords");
        if (callbackRecords != null && !callbackRecords.isEmpty()) {
            for (ToolExecutionRecordVO record : callbackRecords) {
                if (record == null) {
                    continue;
                }
                record.setRoundIndex(round);
                if (!StringUtils.hasText(record.getStepId())) {
                    record.setStepId(stepId);
                }
                dynamicContext.getToolExecutionLog().add(record);
            }

            String postconditionReceipt = verifySideEffectPostcondition(dynamicContext, executionResult);
            dynamicContext.setValue("postconditionReceipt", postconditionReceipt);
            if (StringUtils.hasText(postconditionReceipt)) {
                dynamicContext.getToolExecutionLog().add(ToolExecutionRecordVO.builder()
                        .roundIndex(round)
                        .stepId(stepId)
                        .toolName(toolName)
                        .requestPayload("{}")
                        .responsePayload(postconditionReceipt)
                        .normalizedOutcome("POSTCONDITION_SUCCESS")
                        .success(true)
                        .timestamp(LocalDateTime.now().toString())
                        .build());
            }

            dynamicContext.setValue("callbackToolRecords", null);
            return;
        }

        String syntheticReceipt = extractToolReceipt(executionResult);
        if (!StringUtils.hasText(syntheticReceipt)) {
            syntheticReceipt = synthesizeRelaxedToolReceipt(executionResult);
        }
        if (!StringUtils.hasText(syntheticReceipt)) {
            dynamicContext.setValue("postconditionReceipt", null);
            return;
        }

        dynamicContext.getToolExecutionLog().add(ToolExecutionRecordVO.builder()
                .roundIndex(round)
                .stepId(stepId)
                .toolName(toolName)
                .requestPayload("{}")
                .responsePayload(syntheticReceipt)
                .normalizedOutcome(syntheticReceipt.contains("postcondition") ? "POSTCONDITION_SUCCESS" : "SUCCESS")
                .success(true)
                .timestamp(LocalDateTime.now().toString())
                .build());
        dynamicContext.setValue("postconditionReceipt", syntheticReceipt.contains("postcondition") ? syntheticReceipt : null);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity requestParameter,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step3QualitySupervisorNode");
    }

    private void parseExecutionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                      String executionResult,
                                      String sessionId,
                                      ExecutionOutcomeVO executionOutcome) {
        if (executionOutcome != null) {
            sendExecutionSubResult(dynamicContext, "execution_result", JSON.toJSONString(executionOutcome), sessionId);
        } else if (StringUtils.hasText(executionResult)) {
            sendExecutionSubResult(dynamicContext, "execution_result", executionResult, sessionId);
        }

        List<ToolExecutionRecordVO> roundRecords = dynamicContext.getToolExecutionLog().stream()
                .filter(record -> record != null)
                .filter(record -> record.getRoundIndex() != null && record.getRoundIndex() == dynamicContext.getStep())
                .toList();
        if (!roundRecords.isEmpty()) {
            sendExecutionSubResult(dynamicContext, "execution_tool_trace", JSON.toJSONString(roundRecords), sessionId);
        }

        if (dynamicContext.getRoundExecutionSummary() != null) {
            sendExecutionSubResult(dynamicContext, "execution_summary",
                    JSON.toJSONString(dynamicContext.getRoundExecutionSummary()), sessionId);
        }

        String postconditionReceipt = dynamicContext.getValue("postconditionReceipt");
        if (StringUtils.hasText(postconditionReceipt)) {
            sendExecutionSubResult(dynamicContext, "execution_postcondition", postconditionReceipt, sessionId);
        }

        if (StringUtils.hasText(executionResult)) {
            sendExecutionSubResult(dynamicContext, "execution_raw_output", executionResult, sessionId);
        }
    }

    private static boolean hasSuccessfulToolRecord(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (dynamicContext == null || dynamicContext.getToolExecutionLog() == null) {
            return false;
        }
        int round = dynamicContext.getStep();
        String stepId = dynamicContext.getCurrentRound() == null ? "" : safe(dynamicContext.getCurrentRound().getCurrentStepId());
        return dynamicContext.getToolExecutionLog().stream()
                .anyMatch(record -> Boolean.TRUE.equals(record.getSuccess())
                        && ("SUCCESS".equalsIgnoreCase(record.getNormalizedOutcome())
                        || "POSTCONDITION_SUCCESS".equalsIgnoreCase(record.getNormalizedOutcome()))
                        && ((record.getRoundIndex() != null && record.getRoundIndex() == round)
                        || (StringUtils.hasText(stepId) && stepId.equals(record.getStepId()))));
    }

    private static boolean hasVerifiedPostcondition(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                    String executionResult) {
        if (dynamicContext == null) {
            return StringUtils.hasText(verifyFilesystemPostcondition(executionResult));
        }
        String receipt = dynamicContext.getValue("postconditionReceipt");
        if (StringUtils.hasText(receipt)) {
            return true;
        }
        return StringUtils.hasText(verifySideEffectPostcondition(dynamicContext, executionResult));
    }

    private static boolean isPostconditionRequired(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                   StepExecutionPlanVO plan) {
        String toolName = resolvePrimaryToolName(dynamicContext, plan).toLowerCase(Locale.ROOT);
        return toolName.contains("filesystem") || toolName.contains("csdn");
    }

    private static String verifySideEffectPostcondition(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                        String executionResult) {
        String toolName = resolvePrimaryToolName(dynamicContext,
                dynamicContext == null ? null : dynamicContext.getCurrentStepPlan()).toLowerCase(Locale.ROOT);
        if (toolName.contains("filesystem")) {
            return verifyFilesystemPostcondition(executionResult);
        }
        if (toolName.contains("csdn")) {
            return verifyCsdnPostcondition(dynamicContext);
        }
        return "";
    }

    private static String verifyCsdnPostcondition(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (dynamicContext == null || dynamicContext.getToolExecutionLog() == null) {
            return "";
        }
        return dynamicContext.getToolExecutionLog().stream()
                .filter(record -> Boolean.TRUE.equals(record.getSuccess()))
                .map(ToolExecutionRecordVO::getResponsePayload)
                .filter(StringUtils::hasText)
                .filter(payload -> payload.contains("http"))
                .filter(payload -> !payload.toLowerCase(Locale.ROOT).contains("error"))
                .findFirst()
                .map(payload -> {
                    JSONObject receipt = new JSONObject();
                    receipt.put("status", "success");
                    receipt.put("message", "csdn postcondition verified");
                    receipt.put("data", payload);
                    return receipt.toJSONString();
                })
                .orElse("");
    }

    private static String resolveTaskGoal(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                          StepExecutionPlanVO plan) {
        CurrentRoundTaskVO currentRound = dynamicContext == null ? null : dynamicContext.getCurrentRound();
        if (currentRound != null && StringUtils.hasText(currentRound.getRoundTask())) {
            return currentRound.getRoundTask();
        }
        return plan == null ? "" : safe(plan.getTaskGoal());
    }

    private static boolean resolveToolRequired(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                               StepExecutionPlanVO plan) {
        CurrentRoundTaskVO currentRound = dynamicContext == null ? null : dynamicContext.getCurrentRound();
        if (currentRound != null && currentRound.getToolRequired() != null) {
            return currentRound.getToolRequired();
        }
        return plan != null && Boolean.TRUE.equals(plan.getToolRequired());
    }

    private static String resolvePrimaryToolName(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                 StepExecutionPlanVO plan) {
        CurrentRoundTaskVO currentRound = dynamicContext == null ? null : dynamicContext.getCurrentRound();
        if (currentRound != null && currentRound.getSuggestedTools() != null && !currentRound.getSuggestedTools().isEmpty()) {
            return safe(currentRound.getSuggestedTools().get(0));
        }
        return plan == null ? "" : safe(plan.getToolName());
    }

    private static String resolveToolPurpose(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                             StepExecutionPlanVO plan) {
        CurrentRoundTaskVO currentRound = dynamicContext == null ? null : dynamicContext.getCurrentRound();
        if (currentRound != null && StringUtils.hasText(currentRound.getPlannerNotes())) {
            return currentRound.getPlannerNotes();
        }
        return plan == null ? "" : safe(plan.getToolPurpose());
    }

    private static String resolveExpectedOutput(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                StepExecutionPlanVO plan) {
        CurrentRoundTaskVO currentRound = dynamicContext == null ? null : dynamicContext.getCurrentRound();
        if (currentRound != null && StringUtils.hasText(currentRound.getExpectedEvidence())) {
            return currentRound.getExpectedEvidence();
        }
        return plan == null ? "" : safe(plan.getExpectedOutput());
    }

    private static String resolveSourceContent(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                               StepExecutionPlanVO plan) {
        CurrentRoundTaskVO currentRound = dynamicContext == null ? null : dynamicContext.getCurrentRound();
        if (currentRound != null && StringUtils.hasText(currentRound.getSourceContent())) {
            return currentRound.getSourceContent();
        }
        return plan == null ? "" : safe(plan.getSourceContent());
    }

    public static String validateRequiredSourceContent(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                       StepExecutionPlanVO plan) {
        if (!needsExplicitSourceContent(dynamicContext, plan)) {
            return "";
        }
        if (StringUtils.hasText(resolveSourceContent(dynamicContext, plan))) {
            return "";
        }
        return """
                ExecutionResult: MISSING_REQUIRED_SOURCE_CONTENT
                QualityCheck: missing required source content for a task that depends on previously generated text
                """;
    }

    private static boolean needsExplicitSourceContent(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                      StepExecutionPlanVO plan) {
        String combined = (resolveTaskGoal(dynamicContext, plan) + "\n"
                + safe(dynamicContext == null ? null : dynamicContext.getRawUserGoal()))
                .toLowerCase(Locale.ROOT);
        boolean referencesPriorContent = combined.contains("previous")
                || combined.contains("上一篇")
                || combined.contains("上一次")
                || combined.contains("上一轮")
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

    private static String extractToolReceipt(String executionResult) {
        String raw = safe(executionResult);
        int index = raw.indexOf("ToolReceipt:");
        if (index < 0) {
            return "";
        }
        return raw.substring(index + "ToolReceipt:".length()).trim();
    }

    private static String appendCapturedReceipt(String executionResult,
                                                DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (dynamicContext == null || !StringUtils.hasText(executionResult)) {
            return executionResult;
        }
        if (StringUtils.hasText(extractToolReceipt(executionResult))) {
            return executionResult;
        }
        @SuppressWarnings("unchecked")
        List<ToolExecutionRecordVO> callbackRecords = dynamicContext.getValue("callbackToolRecords");
        if (callbackRecords == null || callbackRecords.isEmpty()) {
            return executionResult;
        }
        List<JSONObject> receipts = callbackRecords.stream()
                .filter(record -> record != null && StringUtils.hasText(record.getResponsePayload()))
                .map(record -> {
                    JSONObject receipt = new JSONObject();
                    receipt.put("toolName", record.getToolName());
                    receipt.put("success", record.getSuccess());
                    receipt.put("response", record.getResponsePayload());
                    if (StringUtils.hasText(record.getErrorMessage())) {
                        receipt.put("errorMessage", record.getErrorMessage());
                    }
                    return receipt;
                })
                .toList();
        if (receipts.isEmpty()) {
            return executionResult;
        }
        return executionResult + System.lineSeparator() + "ToolReceipt: " + JSON.toJSONString(receipts);
    }

    public static boolean looksLikeToolIntentOnly(String executionResult) {
        String raw = safe(executionResult).trim();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = raw;
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring(7).trim();
        }
        if (normalized.startsWith("```")) {
            normalized = normalized.substring(3).trim();
        }
        if (normalized.startsWith("json")) {
            normalized = normalized.substring(4).trim();
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        }
        if (!normalized.startsWith("{") || !normalized.endsWith("}")) {
            return false;
        }
        try {
            JSONObject json = JSON.parseObject(normalized);
            return json != null
                    && StringUtils.hasText(json.getString("tool"))
                    && json.containsKey("arguments")
                    && !raw.contains("ToolReceipt:")
                    && !raw.contains("ExecutionResult:");
        } catch (Exception ignore) {
            return false;
        }
    }

    private static String synthesizeRelaxedToolReceipt(String executionResult) {
        String verified = verifyFilesystemPostcondition(executionResult);
        if (StringUtils.hasText(verified)) {
            return verified;
        }
        return "";
    }

    private static String verifyFilesystemPostcondition(String executionResult) {
        String pathValue = extractLabeledValue(executionResult, "Path:");
        String fileName = extractLabeledValue(executionResult, "FileName:");
        String content = extractLabeledValue(executionResult, "Content:");
        if (!StringUtils.hasText(pathValue) || !StringUtils.hasText(fileName)) {
            return "";
        }
        try {
            Path target = Path.of(pathValue.trim(), fileName.trim());
            if (!Files.exists(target)) {
                return "";
            }
            String actual = Files.readString(target, StandardCharsets.UTF_8);
            if (StringUtils.hasText(content) && !actual.contains(content.trim())) {
                return "";
            }
            JSONObject receipt = new JSONObject();
            receipt.put("status", "success");
            receipt.put("message", "filesystem postcondition verified");
            JSONObject data = new JSONObject();
            data.put("path", target.toString());
            receipt.put("data", data);
            return receipt.toJSONString();
        } catch (Exception ignore) {
            return "";
        }
    }

    private static String extractLabeledValue(String raw, String label) {
        if (!StringUtils.hasText(raw) || !StringUtils.hasText(label)) {
            return "";
        }
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(label)) {
                return trimmed.substring(label.length()).trim();
            }
        }
        return "";
    }

    private static List<ToolExecutionRecordVO> getCurrentRoundToolRecords(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (dynamicContext == null || dynamicContext.getToolExecutionLog() == null) {
            return List.of();
        }
        int round = dynamicContext.getStep();
        String stepId = dynamicContext.getCurrentRound() == null ? "" : safe(dynamicContext.getCurrentRound().getCurrentStepId());
        return dynamicContext.getToolExecutionLog().stream()
                .filter(record -> record != null)
                .filter(record -> (record.getRoundIndex() != null && record.getRoundIndex() == round)
                        || (StringUtils.hasText(stepId) && stepId.equals(record.getStepId())))
                .toList();
    }

    private static String buildEvidenceSummary(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                               List<ToolExecutionRecordVO> successRecords,
                                               String executionResult) {
        String postconditionReceipt = dynamicContext == null ? "" : dynamicContext.getValue("postconditionReceipt");
        if (StringUtils.hasText(postconditionReceipt)) {
            return compactForContext(postconditionReceipt, MAX_RECEIPT_PREVIEW_LENGTH);
        }
        if (successRecords != null && !successRecords.isEmpty()) {
            List<ToolExecutionRecordVO> limitedRecords = successRecords.stream()
                    .limit(MAX_TOOL_RECORDS_IN_SUMMARY)
                    .toList();
            String summary = limitedRecords.stream()
                    .map(Step2PrecisionExecutorNode::summarizeSuccessfulToolRecord)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining(System.lineSeparator()));
            if (successRecords.size() > limitedRecords.size()) {
                summary = summary + System.lineSeparator() + "...(" + (successRecords.size() - limitedRecords.size()) + " more tool records)";
            }
            return compactForContext(summary, MAX_EVIDENCE_SUMMARY_LENGTH);
        }
        return compactForContext(extractToolReceipt(executionResult), MAX_RECEIPT_PREVIEW_LENGTH);
    }

    private static String buildBlockingReason(List<ToolExecutionRecordVO> failedRecords,
                                              String executionResult,
                                              boolean toolRequired,
                                              boolean toolInvoked,
                                              boolean evidenceAvailable) {
        if (failedRecords != null && !failedRecords.isEmpty()) {
            return failedRecords.stream()
                    .map(record -> defaultIfBlank(record.getErrorMessage(), record.getResponsePayload()))
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("tool execution failed");
        }
        if (toolRequired && !toolInvoked) {
            return "tool required but no real tool invocation happened";
        }
        if (toolRequired && !evidenceAvailable) {
            return "tool executed but no credible evidence was retained";
        }
        return hasExecutionFailureSignal(executionResult) ? safe(executionResult) : "";
    }

    private static boolean hasExecutionFailureSignal(String executionResult) {
        String lower = safe(executionResult).toLowerCase(Locale.ROOT);
        return lower.contains("tool call failed")
                || lower.contains("execution failed")
                || lower.contains("missing_required_source_content")
                || lower.contains("invalid arguments")
                || lower.contains("error calling tool")
                || lower.contains("blocked");
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

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String summarizeSuccessfulToolRecord(ToolExecutionRecordVO record) {
        if (record == null) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        summary.append(defaultIfBlank(record.getToolName(), "tool")).append(": success");

        String request = compactForContext(record.getRequestPayload(), MAX_TOOL_REQUEST_PREVIEW_LENGTH);
        if (StringUtils.hasText(request)) {
            summary.append(" | request=").append(request);
        }

        String response = compactForContext(record.getResponsePayload(), MAX_TOOL_RESPONSE_PREVIEW_LENGTH);
        if (StringUtils.hasText(response)) {
            summary.append(" | response=").append(response);
        }

        return summary.toString();
    }

    private static String compactForContext(String text, int maxLength) {
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

    private static boolean hasNamedArg(String hint, String argName) {
        if (!StringUtils.hasText(hint) || !StringUtils.hasText(argName)) {
            return false;
        }
        String normalized = hint.toLowerCase(Locale.ROOT);
        String arg = argName.toLowerCase(Locale.ROOT);
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
