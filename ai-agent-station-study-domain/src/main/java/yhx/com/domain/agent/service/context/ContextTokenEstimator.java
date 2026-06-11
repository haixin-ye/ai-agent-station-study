package yhx.com.domain.agent.service.context;

import com.alibaba.fastjson.JSON;

public class ContextTokenEstimator {

    public int estimateTextTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 2.0);
    }

    public int estimateObjectTokens(Object object) {
        if (object == null) {
            return 0;
        }
        return estimateTextTokens(JSON.toJSONString(object));
    }
}
