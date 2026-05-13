package yhx.com.domain.agent.model.valobj.armory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI API閰嶇疆锛屽€煎璞? *
 * @author yhx
 * 2025/6/27 17:29
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientApiVO {

    /**
     * API ID
     */
    private String apiId;

    /**
     * 鍩虹URL
     */
    private String baseUrl;

    /**
     * API瀵嗛挜
     */
    private String apiKey;

    /**
     * 瀵硅瘽琛ュ叏璺緞
     */
    private String completionsPath;

    /**
     * 宓屽叆鍚戦噺璺緞
     */
    private String embeddingsPath;

}

