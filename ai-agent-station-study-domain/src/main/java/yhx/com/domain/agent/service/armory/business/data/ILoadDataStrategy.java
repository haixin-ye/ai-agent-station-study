package yhx.com.domain.agent.service.armory.business.data;

import yhx.com.domain.agent.model.entity.armory.ArmoryCommandEntity;
import yhx.com.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;

/**
 * Armory data loading strategy.
 *
 * @author yhx
 */
public interface ILoadDataStrategy {

    void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext);

}
