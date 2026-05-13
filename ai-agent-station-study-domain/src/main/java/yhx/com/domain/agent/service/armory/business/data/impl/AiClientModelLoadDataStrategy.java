package yhx.com.domain.agent.service.armory.business.data.impl;

import yhx.com.domain.agent.adapter.repository.IAgentRepository;
import yhx.com.domain.agent.model.entity.armory.ArmoryCommandEntity;
import yhx.com.domain.agent.model.valobj.armory.AiClientApiVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientModelVO;
import yhx.com.domain.agent.service.armory.business.data.ILoadDataStrategy;
import yhx.com.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 浠ュ鎴风瀵硅瘽妯″瀷锛屽姞杞芥暟鎹瓥鐣? * @author yhx
 * 2025/6/28 20:12
 */
@Slf4j
@Service("aiClientModelLoadDataStrategy")
public class AiClientModelLoadDataStrategy implements ILoadDataStrategy {

    @Resource
    private IAgentRepository repository;

    @Resource
    protected ThreadPoolExecutor threadPoolExecutor;

    @Override
    public void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) {
        List<String> modelIdList = armoryCommandEntity.getCommandIdList();

        CompletableFuture<List<AiClientApiVO>> aiClientApiListFuture = CompletableFuture.supplyAsync(() -> {
            log.info("鏌ヨ閰嶇疆鏁版嵁(ai_client_api) {}", modelIdList);
            return repository.queryAiClientApiVOListByModelIds(modelIdList);
        }, threadPoolExecutor);

        CompletableFuture<List<AiClientModelVO>> aiClientModelListFuture = CompletableFuture.supplyAsync(() -> {
            log.info("鏌ヨ閰嶇疆鏁版嵁(ai_client_model) {}", modelIdList);
            return repository.AiClientModelVOByModelIds(modelIdList);
        }, threadPoolExecutor);

    }

}

