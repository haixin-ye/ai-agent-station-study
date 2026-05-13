package yhx.com.domain.agent.service.execute.auto.step;

import yhx.com.domain.agent.model.entity.ExecuteCommandEntity;
import yhx.com.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import yhx.com.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * execute 链入口节点。
 *
 * <p>只负责把本轮请求初始化后送入 Node1，不承担规划、执行、验收职责。
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
        if (dynamicContext.getSessionGoal() == null) {
            dynamicContext.initSession(
                    requestParameter.getMessage(),
                    requestParameter.getMaxStep() != null ? requestParameter.getMaxStep() : 3
            );
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity requestParameter,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        return step1AnalyzerNode;
    }
}