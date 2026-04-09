package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Node3 quality supervisor.
 * Only Node3 can decide whether to finish or continue the loop.
 */
@Slf4j
@Service
public class Step3QualitySupervisorNode extends AbstractExecuteSupport {

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
        String supervisionPrompt = buildSupervisionPrompt(
                sanitizedGoal,
                JSON.toJSONString(plan),
                executionResult,
                dynamicContext.getExecutionHistory() == null ? "" : dynamicContext.getExecutionHistory().toString(),
                lastToolError
        );
        sendSupervisionSubResult(dynamicContext, "supervision_plan_ref", JSON.toJSONString(plan), requestParameter.getSessionId());
        sendSupervisionSubResult(dynamicContext, "supervision_execution_ref", executionResult, requestParameter.getSessionId());

        AiAgentClientFlowConfigVO flowConfig = dynamicContext.getAiAgentClientFlowConfigVOMap()
                .get(AiClientTypeEnumVO.QUALITY_SUPERVISOR_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(flowConfig.getClientId());

        String supervisionResult = chatClient
                .prompt(supervisionPrompt)
                .advisors(a -> {
                    // 节点级会话隔离，防止监督节点读到规划/执行节点的历史噪音
                    a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, buildNodeConversationId(requestParameter.getSessionId(), "node3"))
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 30);
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

        int currentRound = dynamicContext.getStep();
        dynamicContext.getExecutionHistory().append(String.format("""
                === Round %d Supervision(Node3) ===
                %s
                """, currentRound, supervisionResult));

        // Only Node3 decides completion.
        if (containsPass(supervisionResult)) {
            dynamicContext.setCompleted(true);
            sendSupervisionSubResult(dynamicContext, "supervision_decision", "PASS -> Step4", requestParameter.getSessionId());
            log.info("Node3 PASS -> go Step4");
        } else {
            dynamicContext.setCompleted(false);
            dynamicContext.setCurrentTask("re-plan based on supervision");
            dynamicContext.setStep(currentRound + 1);
            sendSupervisionSubResult(dynamicContext, "supervision_decision", "REPLAN -> back to Node1", requestParameter.getSessionId());
            log.info("Node3 not pass -> loop back to Step1");
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

    private static boolean containsPass(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("Pass: PASS")
                || text.contains("是否通过: PASS")
                || text.contains("Decision: PASS");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
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
}
