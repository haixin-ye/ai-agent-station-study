package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 精准任务执行节点
 *
 * @author yhx
 * 2025/7/27 16:42
 */
@Slf4j
@Service
public class Step2PrecisionExecutorNode extends AbstractExecuteSupport {

    static final String WORKSPACE_ROOT = "E:\\javaProject\\ai-agent-station-study";

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n阶段2: 精准任务执行");

        String analysisResult = dynamicContext.getValue("analysisResult");
        if (analysisResult == null || analysisResult.trim().isEmpty()) {
            log.warn("分析结果为空，使用默认执行策略");
            analysisResult = "执行当前任务步骤";
        }

        String executionPrompt = buildExecutionPrompt(requestParameter.getMessage(), analysisResult);

        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap()
                .get(AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String knowledgeName = dynamicContext.getValue("knowledgeName");
        String filterExpression;
        if (StringUtils.hasText(knowledgeName)) {
            filterExpression = String.format("knowledge == '%s'", knowledgeName);
        } else {
            filterExpression = "";
        }

        String executionResult = chatClient
                .prompt(executionPrompt)
                .advisors(a -> {
                    a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024)
                            .param("qa_filter_expression", filterExpression);
                    applyTokenStatParams(
                            a, dynamicContext, requestParameter,
                            aiAgentClientFlowConfigVO.getClientId(),
                            AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode()
                    );
                })
                .call()
                .content();

        parseExecutionResult(dynamicContext, executionResult, requestParameter.getSessionId());
        dynamicContext.setValue("executionResult", executionResult);

        String stepSummary = String.format("""
                === 第%d步执行记录===
                【分析阶段】%s
                【执行阶段】%s
                """, dynamicContext.getStep(), analysisResult, executionResult);
        dynamicContext.getExecutionHistory().append(stepSummary);

        return router(requestParameter, dynamicContext);
    }

    public static String buildExecutionPrompt(String userMessage, String analysisResult) {
        return String.format("""
                **用户原始需求:** %s

                **分析师策略:** %s

                **当前工作区根路径:** %s

                **执行指令:** 你是一个精准任务执行器，需要根据用户需求和分析师策略，实际执行具体任务并产出结果。

                **工具调用硬约束:**
                1. 只要调用文件搜索、目录搜索、代码检索类工具，必须显式传入 path，不能省略，不能传空，不能传 undefined。
                2. 如果调用 search_files，默认从工作区根路径 %s 开始搜索，或传入该路径下的具体子目录。
                3. 如果你不知道路径，先使用工作区根路径 %s，不要自行省略参数。
                4. 工具报参数错误时，不要重复错误调用，要改正参数后再继续。

                **执行要求:**
                1. 直接执行用户的具体需求，如搜索、检索、生成内容等。
                2. 如果需要搜索信息，请实际进行搜索和检索。
                3. 如果需要生成计划、列表等，请直接生成完整内容。
                4. 提供具体的执行结果，而不只是描述过程。
                5. 确保执行结果能够直接回答用户的问题。

                **输出格式:**
                执行目标: [明确的执行目标]
                执行过程: [实际执行的步骤和调用的工具]
                执行结果: [具体的执行成果和获得的信息内容]
                质量检查: [对执行结果的质量评估]
                """, userMessage, analysisResult, WORKSPACE_ROOT, WORKSPACE_ROOT, WORKSPACE_ROOT);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity requestParameter,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step3QualitySupervisorNode");
    }

    private void parseExecutionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, String executionResult, String sessionId) {
        int step = dynamicContext.getStep();
        log.info("\n=== 第{}步执行结果===", step);

        String[] lines = executionResult.split("\n");
        String currentSection = "";
        StringBuilder sectionContent = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.contains("执行目标:")) {
                sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "execution_target";
                sectionContent = new StringBuilder();
                continue;
            } else if (line.contains("执行过程:")) {
                sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "execution_process";
                sectionContent = new StringBuilder();
                continue;
            } else if (line.contains("执行结果:")) {
                sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "execution_result";
                sectionContent = new StringBuilder();
                continue;
            } else if (line.contains("质量检查:")) {
                sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "execution_quality";
                sectionContent = new StringBuilder();
                continue;
            }

            if (!currentSection.isEmpty()) {
                sectionContent.append(line).append("\n");
            }
        }

        sendExecutionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
    }

    private void sendExecutionSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String subType, String content, String sessionId) {
        if (!subType.isEmpty() && !content.isEmpty()) {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createExecutionSubResult(
                    dynamicContext.getStep(), subType, content, sessionId);
            sendSseResult(dynamicContext, result);
        }
    }
}
