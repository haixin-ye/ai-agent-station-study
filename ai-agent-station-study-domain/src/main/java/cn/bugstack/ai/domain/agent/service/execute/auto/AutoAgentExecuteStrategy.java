package cn.bugstack.ai.domain.agent.service.execute.auto;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.TokenUsageAccumulator;
import cn.bugstack.ai.domain.agent.service.execute.IExecuteStrategy;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.AbstractExecuteSupport;
import cn.bugstack.ai.domain.agent.service.execute.auto.support.AutoAgentTraceLogSupport;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自动执行策略
 *
 * @author yhx
 * 2025/8/5 09:49
 */
@Slf4j
@Service
public class AutoAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultAutoAgentExecuteStrategyFactory defaultAutoAgentExecuteStrategyFactory;

    @Override
    public void execute(ExecuteCommandEntity executeCommandEntity, ResponseBodyEmitter emitter) throws Exception {
        StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultAutoAgentExecuteStrategyFactory.armoryStrategyHandler();

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext = new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.initSession(
                executeCommandEntity.getMessage(),
                executeCommandEntity.getMaxStep() != null ? executeCommandEntity.getMaxStep() : 3
        );
        dynamicContext.setValue("knowledgeName", executeCommandEntity.getKnowledgeName());
        dynamicContext.setValue("emitter", emitter);
        dynamicContext.setValue(AbstractExecuteSupport.TOKEN_STAT_ACCUMULATOR_KEY, new TokenUsageAccumulator());
        dynamicContext.setValue("traceLogPath", AutoAgentTraceLogSupport.initializeTraceLog(
                executeCommandEntity.getSessionId(),
                executeCommandEntity.getMessage()
        ));

        String apply = executeHandler.apply(executeCommandEntity, dynamicContext);
        log.info("测试结果:{}", apply);

        try {
            TokenUsageAccumulator accumulator = dynamicContext.getValue(AbstractExecuteSupport.TOKEN_STAT_ACCUMULATOR_KEY);
            if (accumulator != null) {
                TokenUsageAccumulator.TokenUsageSnapshot snapshot = accumulator.snapshot();
                Map<String, Object> usage = new LinkedHashMap<>();
                usage.put("inputTokens", snapshot.getInputTokens());
                usage.put("outputTokens", snapshot.getOutputTokens());
                usage.put("totalTokens", snapshot.getTotalTokens());

                AutoAgentExecuteResultEntity tokenTotalResult =
                        AutoAgentExecuteResultEntity.createTokenTotalUsageResult(JSON.toJSONString(usage), executeCommandEntity.getSessionId());
                AutoAgentTraceLogSupport.appendEvent(dynamicContext.getValue("traceLogPath"), tokenTotalResult);
                emitter.send("data: " + JSON.toJSONString(tokenTotalResult) + "\n\n");
            }

            AutoAgentExecuteResultEntity completeResult = AutoAgentExecuteResultEntity.createCompleteResult(executeCommandEntity.getSessionId());
            AutoAgentTraceLogSupport.appendEvent(dynamicContext.getValue("traceLogPath"), completeResult);
            String sseData = "data: " + JSON.toJSONString(completeResult) + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            log.error("发送完成标识失败：{}", e.getMessage(), e);
        }
    }

}
