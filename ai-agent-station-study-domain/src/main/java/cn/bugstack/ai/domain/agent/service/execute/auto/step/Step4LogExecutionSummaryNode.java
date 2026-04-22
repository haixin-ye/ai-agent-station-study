package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.adapter.repository.ISessionMemoryRepository;
import cn.bugstack.ai.domain.agent.model.entity.AcceptedResultVO;
import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.SessionMemoryEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class Step4LogExecutionSummaryNode extends AbstractExecuteSupport {

    @Resource
    private ISessionMemoryRepository sessionMemoryRepository;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter,
                             DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n=== Step4 Final Summary ===");
        logExecutionSummary(dynamicContext.getMaxStep(), dynamicContext.getExecutionHistory(), dynamicContext.isCompleted());
        generateFinalReport(requestParameter, dynamicContext);
        log.info("\n=== Auto Agent Finished ===");
        return "ai agent execution summary completed!";
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity requestParameter,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        return defaultStrategyHandler;
    }

    private void logExecutionSummary(int maxSteps, StringBuilder executionHistory, boolean isCompleted) {
        String history = executionHistory == null ? "" : executionHistory.toString();
        int actualSteps = Math.min(maxSteps, Math.max(0, history.split("=== ").length - 1));
        log.info("summary.steps={} completed={} efficiency={}%%", actualSteps, isCompleted,
                isCompleted ? 100.0 : (maxSteps <= 0 ? 0.0 : (double) actualSteps / maxSteps * 100));
    }

    private void generateFinalReport(ExecuteCommandEntity requestParameter,
                                     DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        try {
            boolean isCompleted = dynamicContext.isCompleted();
            AiAgentClientFlowConfigVO flowConfig = dynamicContext.getAiAgentClientFlowConfigVOMap()
                    .get(AiClientTypeEnumVO.RESPONSE_ASSISTANT.getCode());
            ChatClient chatClient = getChatClientByClientId(flowConfig.getClientId());
            String summaryPrompt = buildSummaryInput(dynamicContext, isCompleted);

            String summaryResult = chatClient
                    .prompt(summaryPrompt)
                    .advisors(a -> {
                        a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId() + "-summary")
                                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 30);
                        applyTokenStatParams(
                                a, dynamicContext, requestParameter,
                                flowConfig.getClientId(),
                                AiClientTypeEnumVO.RESPONSE_ASSISTANT.getCode()
                        );
                    })
                    .call()
                    .content();

            if (summaryResult == null) {
                summaryResult = "";
            }
            logFinalReport(dynamicContext, summaryResult, requestParameter.getSessionId());
            dynamicContext.setValue("finalSummary", summaryResult);
            persistSessionMemory(requestParameter, summaryResult);
        } catch (Exception e) {
            log.error("generate final summary failed: {}", e.getMessage(), e);
        }
    }

    private void persistSessionMemory(ExecuteCommandEntity requestParameter, String summaryResult) {
        if (requestParameter == null
                || !StringUtils.hasText(requestParameter.getSessionId())
                || !StringUtils.hasText(requestParameter.getMessage())
                || !StringUtils.hasText(summaryResult)) {
            return;
        }

        Integer maxRoundNo = sessionMemoryRepository.queryMaxRoundNo(requestParameter.getSessionId());
        int nextRoundNo = maxRoundNo == null ? 1 : maxRoundNo + 1;

        sessionMemoryRepository.save(SessionMemoryEntity.builder()
                .sessionId(requestParameter.getSessionId())
                .roundNo(nextRoundNo)
                .userMessage(requestParameter.getMessage())
                .finalAnswer(summaryResult)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());
    }

    private void logFinalReport(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                String summaryResult,
                                String sessionId) {
        String[] lines = summaryResult == null ? new String[0] : summaryResult.split("\n");
        String currentSection = "summary_overview";
        StringBuilder sectionContent = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            String newSection = detectSummarySection(line);
            if (newSection != null && !newSection.equals(currentSection)) {
                if (!sectionContent.isEmpty()) {
                    sendSummarySubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                }
                currentSection = newSection;
                sectionContent.setLength(0);
            }

            if (!sectionContent.isEmpty()) {
                sectionContent.append("\n");
            }
            sectionContent.append(line);
            log.info("summary.line={}", line);
        }

        if (!sectionContent.isEmpty()) {
            sendSummarySubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
        }

        sendSummaryResult(dynamicContext, summaryResult, sessionId);
        sendCompleteResult(dynamicContext, sessionId);
    }

    private void sendSummaryResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                   String summaryResult,
                                   String sessionId) {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSummaryResult(summaryResult, sessionId);
        sendSseResult(dynamicContext, result);
    }

    private void sendSummarySubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                      String subType,
                                      String content,
                                      String sessionId) {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSummarySubResult(subType, content, sessionId);
        sendSseResult(dynamicContext, result);
    }

    private void sendCompleteResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                    String sessionId) {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createCompleteResult(sessionId);
        sendSseResult(dynamicContext, result);
        log.info("summary.complete sent");
    }

    private String detectSummarySection(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        String normalized = content.toLowerCase(Locale.ROOT);
        if (normalized.contains("completed")
                || normalized.contains("deliver")
                || normalized.contains("finished")) {
            return "completed_work";
        }
        if (normalized.contains("incomplete")
                || normalized.contains("failed")
                || normalized.contains("blocked")
                || normalized.contains("reason")) {
            return "incomplete_reasons";
        }
        if (normalized.contains("suggest")
                || normalized.contains("next step")
                || normalized.contains("recommend")) {
            return "suggestions";
        }
        if (normalized.contains("evaluation")
                || normalized.contains("result")
                || normalized.contains("conclusion")) {
            return "evaluation";
        }
        return null;
    }

    static String buildSummaryInput(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, boolean isCompleted) {
        String rawUserInput = dynamicContext.getSessionGoal() != null
                ? dynamicContext.getSessionGoal().getRawUserInput()
                : dynamicContext.getRawUserGoal();
        String sanitizedGoal = dynamicContext.getSessionGoal() != null
                ? dynamicContext.getSessionGoal().getSanitizedGoal()
                : dynamicContext.getSanitizedUserGoal();

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("rawUserInput", rawUserInput == null ? "" : rawUserInput);
        payload.put("sanitizedGoal", sanitizedGoal == null ? "" : sanitizedGoal);
        payload.put("acceptedResults", dynamicContext.getAcceptedResults() == null ? List.<AcceptedResultVO>of() : dynamicContext.getAcceptedResults());
        payload.put("taskBoard", dynamicContext.getTaskBoard() == null ? Map.of() : dynamicContext.getTaskBoard());
        payload.put("roundArchive", dynamicContext.getRoundArchive() == null ? Map.of() : dynamicContext.getRoundArchive());
        payload.put("nextRoundDirective", dynamicContext.getNextRoundDirective() == null ? Map.of() : dynamicContext.getNextRoundDirective());
        payload.put("overallStatus", dynamicContext.getOverallStatus() == null ? Map.of() : dynamicContext.getOverallStatus());
        payload.put("answerPolicy", Map.of(
                "primaryTruthSources", List.of("rawUserInput", "acceptedResults", "taskBoard", "roundArchive", "overallStatus", "nextRoundDirective"),
                "mustRespectOverallStatus", true,
                "mustSummarizeAcceptedResults", true,
                "mustDescribePartialOrFailureClearly", true,
                "mustNotUseExecutionHistoryAsTruthSource", true,
                "mustBeUserFacing", true
        ));
        payload.put("completed", isCompleted);
        return JSON.toJSONString(payload);
    }
}
