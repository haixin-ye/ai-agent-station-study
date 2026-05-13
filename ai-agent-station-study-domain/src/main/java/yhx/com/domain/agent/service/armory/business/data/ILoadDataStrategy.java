package yhx.com.domain.agent.service.armory.business.data;

import yhx.com.domain.agent.model.entity.armory.ArmoryCommandEntity;
import yhx.com.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;

import java.util.List;

/**
 * 鏁版嵁鍔犺浇绛栫暐
 *
 * @author yhx
 * 2025/6/27 17:16
 */
public interface ILoadDataStrategy {

    void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext);

}

