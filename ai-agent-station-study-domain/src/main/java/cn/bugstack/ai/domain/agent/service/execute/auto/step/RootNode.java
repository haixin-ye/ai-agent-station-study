package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 执行入口节点，负责初始化本次自动执行的上下文。
 */
@Slf4j
@Service("executeRootNode")
public class RootNode extends AbstractExecuteSupport {

    @Resource
    private Step1AnalyzerNode step1AnalyzerNode;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter,
                             DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("=== 动态多轮执行开始 ===");
        log.info("用户输入: {}", requestParameter.getMessage());
        log.info("最大执行步数: {}", requestParameter.getMaxStep());
        log.info("会话ID: {}", requestParameter.getSessionId());

        Map<String, AiAgentClientFlowConfigVO> flowConfigMap =
                repository.queryAiAgentClientFlowConfig(requestParameter.getAiAgentId());

        dynamicContext.setAiAgentClientFlowConfigVOMap(flowConfigMap);
        dynamicContext.setExecutionHistory(new StringBuilder());
        dynamicContext.setPlanHistory(new HashMap<Integer, StepExecutionPlanVO>());
        dynamicContext.setCurrentTask(requestParameter.getMessage());

        // 原始输入仅用于审计，不作为后续节点的推理输入。
        dynamicContext.setRawUserGoal(requestParameter.getMessage());
        dynamicContext.setMaxStep(requestParameter.getMaxStep());
        dynamicContext.setCompleted(false);
        dynamicContext.setStep(1);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity requestParameter,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        return step1AnalyzerNode;
    }
}

