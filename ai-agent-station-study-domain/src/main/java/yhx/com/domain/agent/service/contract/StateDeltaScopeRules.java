package yhx.com.domain.agent.service.contract;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.StateDeltaFieldEnumVO;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StateDeltaScopeRules {

    private static final Map<MainAgentActionTypeEnumVO, Set<String>> ALLOWED_FIELDS = new EnumMap<>(MainAgentActionTypeEnumVO.class);
    private static final Set<String> RUNTIME_OWNED_FIELDS = new HashSet<>();

    static {
        allow(MainAgentActionTypeEnumVO.FINAL, StateDeltaFieldEnumVO.FINAL_ANSWER_CANDIDATE);
        allow(MainAgentActionTypeEnumVO.RETRIEVE_RAG, StateDeltaFieldEnumVO.RAG_REQUEST);
        allow(MainAgentActionTypeEnumVO.CALL_TOOL, StateDeltaFieldEnumVO.TOOL_INTENT);
        allow(MainAgentActionTypeEnumVO.ASK_USER, StateDeltaFieldEnumVO.ASK_USER_REQUEST);
        allow(MainAgentActionTypeEnumVO.PLAN, StateDeltaFieldEnumVO.PLAN_DRAFT);
        allow(MainAgentActionTypeEnumVO.CONTINUE, StateDeltaFieldEnumVO.NEXT_ACTION_HINT);
        allow(MainAgentActionTypeEnumVO.DELEGATE_AGENTS, StateDeltaFieldEnumVO.DELEGATE_AGENTS_REQUEST);
        allow(MainAgentActionTypeEnumVO.REPAIR_FINAL, StateDeltaFieldEnumVO.FINAL_ANSWER_CANDIDATE);
        allow(MainAgentActionTypeEnumVO.FAIL, StateDeltaFieldEnumVO.FAILURE);

        Collections.addAll(RUNTIME_OWNED_FIELDS,
                "runId",
                "sessionId",
                "runStatus",
                "runtimePhase",
                "loopIndex",
                "nextPhase",
                "trace",
                "audit",
                "toolReceipt",
                "ragWasUsed");
    }

    public static boolean isAllowed(String actionCode, String stateDeltaField) {
        return MainAgentActionTypeEnumVO.ofCode(actionCode)
                .map(action -> ALLOWED_FIELDS.getOrDefault(action, Collections.emptySet()).contains(stateDeltaField))
                .orElse(false);
    }

    public static boolean isRuntimeOwnedField(String field) {
        return RUNTIME_OWNED_FIELDS.contains(field);
    }

    public static Set<String> allowedFields(String actionCode) {
        return MainAgentActionTypeEnumVO.ofCode(actionCode)
                .map(action -> ALLOWED_FIELDS.getOrDefault(action, Collections.emptySet()))
                .orElse(Collections.emptySet());
    }

    private static void allow(MainAgentActionTypeEnumVO action, StateDeltaFieldEnumVO... fields) {
        Set<String> codes = new HashSet<>();
        for (StateDeltaFieldEnumVO field : fields) {
            codes.add(field.code());
        }
        ALLOWED_FIELDS.put(action, Collections.unmodifiableSet(codes));
    }
}

