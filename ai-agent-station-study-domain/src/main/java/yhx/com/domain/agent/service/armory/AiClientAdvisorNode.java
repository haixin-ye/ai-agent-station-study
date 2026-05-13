package yhx.com.domain.agent.service.armory;

import yhx.com.domain.agent.model.entity.armory.ArmoryCommandEntity;
import yhx.com.domain.agent.model.valobj.enums.armory.AiAgentEnumVO;
import yhx.com.domain.agent.model.valobj.enums.armory.AiClientAdvisorTypeEnumVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientAdvisorVO;
import yhx.com.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 椤鹃棶瑙掕壊鑺傜偣
 *
 * @author yhx
 * 2025/7/19 08:51
 */
@Slf4j
@Service
public class AiClientAdvisorNode extends AbstractArmorySupport {

    @Lazy
    @Resource(name = "pgVectorStore")
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private VectorStore pgVectorStore;

    @Resource
    private AiClientNode aiClientNode;

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 鏋勫缓鑺傜偣锛孉dvisor 椤鹃棶瑙掕壊{}", JSON.toJSONString(requestParameter));

        List<AiClientAdvisorVO> aiClientAdvisorList = dynamicContext.getValue(dataName());

        if (aiClientAdvisorList == null || aiClientAdvisorList.isEmpty()) {
            log.warn("娌℃湁闇€瑕佽鍒濆鍖栫殑 ai client advisor");
            return router(requestParameter, dynamicContext);
        }

        for (AiClientAdvisorVO aiClientAdvisorVO : aiClientAdvisorList) {
            // 鏋勫缓椤鹃棶璁块棶瀵硅薄
            Advisor advisor = createAdvisor(aiClientAdvisorVO);
            // 娉ㄥ唽Bean瀵硅薄
            registerBean(beanName(aiClientAdvisorVO.getAdvisorId()), Advisor.class, advisor);
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return aiClientNode;
    }

    protected String beanName(String beanId) {
        return AiAgentEnumVO.AI_CLIENT_ADVISOR.getBeanName(beanId);
    }

    @Override
    protected String dataName() {
        return AiAgentEnumVO.AI_CLIENT_ADVISOR.getDataName();
    }

    private Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO) {
        String advisorType = aiClientAdvisorVO.getAdvisorType();
        AiClientAdvisorTypeEnumVO advisorTypeEnum = AiClientAdvisorTypeEnumVO.getByCode(advisorType);
        // 閫忎紶妯″瀷鎻愪緵鍣紝渚涢渶瑕侀澶栨ā鍨嬬殑 Advisor锛堝 PromptInjectionSanitizer锛変娇鐢?
        return advisorTypeEnum.createAdvisor(aiClientAdvisorVO, pgVectorStore, this::resolveChatModelBean);
    }

    private OpenAiChatModel resolveChatModelBean(String beanName) {
        // 鎸?BeanName 鍔ㄦ€佽幏鍙栬交閲忔竻娲楁ā鍨?
        return getBean(beanName);
    }

}

