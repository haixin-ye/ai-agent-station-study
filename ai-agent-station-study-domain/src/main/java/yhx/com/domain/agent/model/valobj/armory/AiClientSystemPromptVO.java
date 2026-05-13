package yhx.com.domain.agent.model.valobj.armory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 鎻愮ず璇?鍔ㄦ€佽鍒掞紝鍊煎璞? *
 * @author yhx
 * 2025/6/27 18:45
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientSystemPromptVO {

    /**
     * 鎻愮ず璇岻D
     */
    private String promptId;

    /**
     * 鎻愮ず璇嶅悕绉?     */
    private String promptName;

    /**
     * 鎻愮ず璇嶅唴瀹?     */
    private String promptContent;

    /**
     * 鎻忚堪
     */
    private String description;


}

