package yhx.com.domain.agent.model.entity.armory;

import yhx.com.domain.agent.model.valobj.enums.armory.AiAgentEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 瑁呴厤鍛戒护
 *
 * @author yhx
 * 2025/6/27 07:26
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArmoryCommandEntity {

    /**
     * 鍛戒护绫诲瀷 AiAgentEnumVO.getCode
     */
    private String commandType;

    /**
     * 鍛戒护绱㈠紩锛坈lientId銆乵odelId銆乤piId...锛?     */
    private List<String> commandIdList;

    /**
     * 鏍规嵁 commandType 鑾峰彇瀵瑰簲鐨勬暟鎹姞杞界瓥鐣ュ瓧绗︿覆銆?     * 閫氳繃璋冪敤 AiAgentEnumVO 鏋氫妇绫荤殑 getByCode 鏂规硶锛岃幏鍙栨灇涓惧疄渚嬶紝
     * 鐒跺悗杩斿洖璇ュ疄渚嬬殑 loadDataStrategy 瀛楁鍊笺€?     * <p>
     * 娉ㄦ剰锛歝ommandType 蹇呴』鏄湁鏁堢殑鏋氫妇缂栫爜锛屽惁鍒欏彲鑳藉紩鍙戝紓甯搞€?     *
     * @return 杩斿洖瀵瑰簲鐨勫姞杞芥暟鎹瓥鐣ュ瓧绗︿覆
     */
    public String getLoadDataStrategy() {
        return AiAgentEnumVO.getByCode(commandType).getLoadDataStrategy();
    }

}


