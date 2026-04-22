package cn.bugstack.ai.domain.agent.service.execute.auto;

import cn.bugstack.ai.domain.agent.adapter.repository.ISessionMemoryRepository;
import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.SessionMemoryEntity;
import cn.bugstack.ai.domain.agent.model.entity.TokenUsageAccumulator;
import cn.bugstack.ai.domain.agent.service.execute.IExecuteStrategy;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.AbstractExecuteSupport;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.ai.domain.agent.service.execute.auto.support.AutoAgentTraceLogSupport;
import cn.bugstack.ai.domain.agent.service.execute.auto.support.SessionMemoryPromptSupport;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 鑷姩鎵ц绛栫暐
 *
 * @author yhx
 * 2025/8/5 09:49
 */
@Slf4j
@Service
public class AutoAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultAutoAgentExecuteStrategyFactory defaultAutoAgentExecuteStrategyFactory;

    @Resource
    private ISessionMemoryRepository sessionMemoryRepository;

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
        loadSessionMemory(executeCommandEntity, dynamicContext);

        String apply = executeHandler.apply(executeCommandEntity, dynamicContext);
        log.info("娴嬭瘯缁撴灉:{}", apply);

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
            log.error("鍙戦€佸畬鎴愭爣璇嗗け璐ワ細{}", e.getMessage(), e);
        }
    }

    private void loadSessionMemory(ExecuteCommandEntity executeCommandEntity,
                                   DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        String sessionId = executeCommandEntity.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            dynamicContext.setValue(AbstractExecuteSupport.SESSION_HISTORY_KEY, List.of());
            dynamicContext.setValue(AbstractExecuteSupport.SESSION_HISTORY_PROMPT_KEY, "");
            return;
        }

        List<SessionMemoryEntity> history = sessionMemoryRepository.queryLatestBySessionId(
                sessionId,
                SessionMemoryPromptSupport.DEFAULT_HISTORY_LIMIT
        );
        List<SessionMemoryEntity> sortedHistory = SessionMemoryPromptSupport.sortChronologically(history);
        dynamicContext.setValue(AbstractExecuteSupport.SESSION_HISTORY_KEY, sortedHistory);
        dynamicContext.setValue(
                AbstractExecuteSupport.SESSION_HISTORY_PROMPT_KEY,
                SessionMemoryPromptSupport.buildSessionHistoryPrompt(sortedHistory)
        );
    }

}
