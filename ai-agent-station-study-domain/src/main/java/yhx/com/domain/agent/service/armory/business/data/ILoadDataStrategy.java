package yhx.com.domain.agent.service.armory.business.data;

import yhx.com.domain.agent.model.entity.ArmoryCommandEntity;
import yhx.com.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;

import java.util.List;

/**
 * 数据加载策略
 *
 * @author yhx
 * 2025/6/27 17:16
 */
public interface ILoadDataStrategy {

    void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext);

}
