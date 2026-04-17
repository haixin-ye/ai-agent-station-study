package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.RoundArchiveVO;
import cn.bugstack.ai.domain.agent.model.entity.TokenUsageAccumulator;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.support.AutoAgentTraceLogSupport;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * @author yhx
 * 2025/7/27 16:48
 */
public abstract class AbstractExecuteSupport extends AbstractMultiThreadStrategyRouter<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> {

    private final Logger log = LoggerFactory.getLogger(AbstractExecuteSupport.class);

    @Resource
    protected ApplicationContext applicationContext;

    @Resource
    protected IAgentRepository repository;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";
    public static final String TOKEN_STAT_EMITTER_KEY = "token_stat_emitter";
    public static final String TOKEN_STAT_SESSION_ID_KEY = "token_stat_session_id";
    public static final String TOKEN_STAT_CLIENT_ID_KEY = "token_stat_client_id";
    public static final String TOKEN_STAT_CLIENT_TYPE_KEY = "token_stat_client_type";
    public static final String TOKEN_STAT_STEP_KEY = "token_stat_step";
    public static final String TOKEN_STAT_ACCUMULATOR_KEY = "token_stat_accumulator";

    @Override
    protected void multiThread(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {

    }

    protected ChatClient getChatClientByClientId(String clientId) {
        return getBean(AiAgentEnumVO.AI_CLIENT.getBeanName(clientId));
    }

    protected <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }

    protected void applyTokenStatParams(ChatClient.AdvisorSpec advisorSpec,
                                        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        ExecuteCommandEntity requestParameter,
                                        String clientId,
                                        String clientType) {
        advisorSpec.param(TOKEN_STAT_EMITTER_KEY, dynamicContext.getValue("emitter"))
                .param(TOKEN_STAT_SESSION_ID_KEY, requestParameter.getSessionId())
                .param(TOKEN_STAT_CLIENT_ID_KEY, clientId)
                .param(TOKEN_STAT_CLIENT_TYPE_KEY, clientType)
                .param(TOKEN_STAT_STEP_KEY, dynamicContext.getStep());

        TokenUsageAccumulator accumulator = dynamicContext.getValue(TOKEN_STAT_ACCUMULATOR_KEY);
        if (accumulator != null) {
            advisorSpec.param(TOKEN_STAT_ACCUMULATOR_KEY, accumulator);
        }
    }

    protected void sendSseResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                 AutoAgentExecuteResultEntity result) {
        try {
            AutoAgentTraceLogSupport.appendEvent(dynamicContext.getValue("traceLogPath"), result);
            ResponseBodyEmitter emitter = dynamicContext.getValue("emitter");
            if (emitter != null) {
                String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
                emitter.send(sseData);
            }
        } catch (IOException e) {
            log.error("发送 SSE 结果失败：{}", e.getMessage(), e);
        }
    }

    protected void appendRoundArchive(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                      int round,
                                      Consumer<RoundArchiveVO> updater) {
        RoundArchiveVO archive = dynamicContext.getRoundArchive().computeIfAbsent(
                round,
                key -> RoundArchiveVO.builder().round(round).build()
        );
        updater.accept(archive);
    }
}
