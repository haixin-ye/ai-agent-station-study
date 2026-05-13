package yhx.com.domain.agent.model.valobj.armory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 瀹㈡埛绔厤缃? * @author yhx
 * 2025/7/27 17:18
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiAgentClientFlowConfigVO {

    /**
     * 瀹㈡埛绔疘D
     */
    private String clientId;

    /**
     * 瀹㈡埛绔悕绉?     */
    private String clientName;

    /**
     * 瀹㈡埛绔灇涓?     */
    private String clientType;

    /**
     * 搴忓垪鍙?鎵ц椤哄簭)
     */
    private Integer sequence;

}

