package yhx.com.domain.agent.model.valobj.armory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 鑱婂ぉ妯″瀷閰嶇疆锛屽€煎璞? * @author yhx
 * 2025/6/27 17:43
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientModelVO {

    /**
     * 鍏ㄥ眬鍞竴妯″瀷ID
     */
    private String modelId;

    /**
     * 鍏宠仈鐨凙PI閰嶇疆ID
     */
    private String apiId;

    /**
     * 妯″瀷鍚嶇О
     */
    private String modelName;

    /**
     * 妯″瀷绫诲瀷锛歰penai銆乨eepseek銆乧laude
     */
    private String modelType;

    /**
     * 宸ュ叿 mcp ids
     */
    private List<String> toolMcpIds;

}

