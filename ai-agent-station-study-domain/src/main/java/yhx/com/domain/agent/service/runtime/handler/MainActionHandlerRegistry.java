package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.service.runtime.MainActionHandler;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MainActionHandlerRegistry {

    private final Map<MainAgentActionTypeEnumVO, MainActionHandler> handlers = new EnumMap<>(MainAgentActionTypeEnumVO.class);

    public MainActionHandlerRegistry(List<MainActionHandler> handlers) {
        if (handlers != null) {
            for (MainActionHandler handler : handlers) {
                if (handler != null && handler.actionType() != null) {
                    this.handlers.put(handler.actionType(), handler);
                }
            }
        }
    }

    public MainActionHandler getHandler(MainAgentActionTypeEnumVO actionType) {
        return handlers.get(actionType);
    }

    public boolean supports(MainAgentActionTypeEnumVO actionType) {
        return handlers.containsKey(actionType);
    }

    public boolean hasExactlyOneHandlerForEveryAction() {
        return handlers.size() == MainAgentActionTypeEnumVO.values().length;
    }
}
