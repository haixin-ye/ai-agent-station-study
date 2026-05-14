package yhx.com.domain.agent.model.entity.armory;

import yhx.com.domain.agent.model.valobj.enums.armory.AiAgentEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Armory assembly command.
 *
 * @author yhx
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArmoryCommandEntity {

    /**
     * Command type. See {@link AiAgentEnumVO#getCode()}.
     */
    private String commandType;

    /**
     * Command identifiers, such as clientId, modelId, or apiId.
     */
    private List<String> commandIdList;

    /**
     * Resolve the data loading strategy from commandType.
     */
    public String getLoadDataStrategy() {
        return AiAgentEnumVO.getByCode(commandType).getLoadDataStrategy();
    }

}
