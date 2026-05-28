package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.RunEventTypeEnumVO;
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class RunEventPublisher {

    private final IEventTraceRepository eventTraceRepository;
    private final IPayloadRepository payloadRepository;
    private final RunDiagnosticRecorder diagnosticRecorder;

    public RunEventPublisher(IEventTraceRepository eventTraceRepository, IPayloadRepository payloadRepository) {
        this(eventTraceRepository, payloadRepository, null);
    }

    public RunEventPublisher(IEventTraceRepository eventTraceRepository,
                             IPayloadRepository payloadRepository,
                             RunDiagnosticRecorder diagnosticRecorder) {
        this.eventTraceRepository = eventTraceRepository;
        this.payloadRepository = payloadRepository;
        this.diagnosticRecorder = diagnosticRecorder;
    }

    public void received(String runId, String summary) {
        append(runId, RunEventTypeEnumVO.RUN_STARTED, payload("received", summary, null, null));
    }

    public void phase(String runId, String title, String summary) {
        String humanTitle = humanPhase(title);
        if (humanTitle != null) {
            AutoAgentHumanLog.stage(humanTitle, runId, "进入阶段：" + title + "，" + humanSummary(title, summary));
        }
        append(runId, RunEventTypeEnumVO.STATUS_CHANGED, payload(title, summary, null, null));
    }

    public void askingUser(String runId, String pendingInputId, String question) {
        append(runId, RunEventTypeEnumVO.ASK_USER, payload("asking_user", question, pendingInputId, null));
    }

    public void askingUser(String runId, String pendingInputId, AskUserRequestVO request) {
        Map<String, Object> payload = payload("asking_user",
                request == null ? null : request.getQuestion(),
                pendingInputId,
                null);
        if (request != null) {
            payload.put("question", request.getQuestion());
            payload.put("inputMode", request.getInputMode());
            payload.put("allowFreeText", request.getAllowFreeText());
            payload.put("options", request.getOptions());
        }
        append(runId, RunEventTypeEnumVO.ASK_USER, payload);
    }

    public void completed(String runId, String finalMessageId) {
        append(runId, RunEventTypeEnumVO.FINAL_READY, payload("completed", "Final response is ready.", null, finalMessageId));
    }

    public void failed(String runId, String userSafeSummary) {
        AutoAgentHumanLog.stage("任务失败", runId, "任务进入失败状态，用户提示=" + userSafeSummary);
        append(runId, RunEventTypeEnumVO.RUN_FAILED, payload("failed", userSafeSummary, null, null));
    }

    public void cancelled(String runId, String summary) {
        append(runId, RunEventTypeEnumVO.RUN_FAILED, payload("cancelled", summary, null, null));
    }

    private void append(String runId, RunEventTypeEnumVO eventType, Map<String, Object> payload) {
        if (eventTraceRepository == null || payloadRepository == null) {
            return;
        }
        log.info("[AutoAgent][event] runId={}, eventType={}, title={}, summary={}, pendingInputId={}, finalMessageId={}",
                runId, eventType == null ? null : eventType.code(), payload.get("title"), payload.get("summary"),
                payload.get("pendingInputId"), payload.get("finalMessageId"));
        if (diagnosticRecorder != null) {
            Map<String, Object> diagnostic = new LinkedHashMap<>(payload);
            diagnostic.put("eventType", eventType == null ? null : eventType.code());
            diagnosticRecorder.record(runId, "USER_EVENT", eventType == null ? null : eventType.code(), diagnostic);
        }
        String payloadRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(payload))
                .preview(String.valueOf(payload.get("summary")))
                .createdAt(LocalDateTime.now())
                .build());
        eventTraceRepository.appendUserVisibleEvent(AgentRunEventEntity.builder()
                .runId(runId)
                .eventType(eventType)
                .payloadRef(payloadRef)
                .userVisible(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Map<String, Object> payload(String title, String summary, String pendingInputId, String finalMessageId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("summary", summary);
        payload.put("pendingInputId", pendingInputId);
        payload.put("finalMessageId", finalMessageId);
        return payload;
    }

    private String humanPhase(String title) {
        if (title == null) {
            return null;
        }
        return switch (title) {
            case "PREPARING_CONTEXT" -> "上下文准备";
            case "PLANNING_CONTEXT" -> "上下文规划";
            case "BUILDING_STATE_VIEW" -> "状态视图";
            case "CALLING_MAIN_NODE" -> "调用主Node";
            case "VALIDATING_ACTION" -> "动作校验";
            case "HANDLING_ACTION" -> "动作路由";
            case "VERIFYING_FINAL" -> "最终检查";
            case "REPAIRING_FINAL" -> "最终修复";
            case "WAITING_USER" -> "等待用户";
            case "RESOLVING_USER_ANSWER" -> "处理用户回答";
            case "COMPLETED" -> "任务完成";
            case "FAILED" -> "任务失败";
            default -> null;
        };
    }

    private String humanSummary(String title, String summary) {
        return switch (title == null ? "" : title) {
            case "PREPARING_CONTEXT" -> "正在读取 MySQL 固定上下文并并行召回向量记忆。";
            case "PLANNING_CONTEXT" -> "正在让 ContextPlanner 判断哪些候选需要注入 MainAgent。";
            case "BUILDING_STATE_VIEW" -> "正在把通过规划的内容组装为 MainAgentStateView。";
            case "CALLING_MAIN_NODE" -> "正在调用 MainAgent 做语义决策或生成回答。";
            case "VALIDATING_ACTION" -> "正在解析并校验 MainAgent 输出的动作 JSON。";
            case "HANDLING_ACTION" -> "正在把动作路由给对应的确定性模块。";
            case "VERIFYING_FINAL" -> "正在进行最终回答检查。";
            case "WAITING_USER" -> "已经暂停运行，等待用户回答。";
            case "RESOLVING_USER_ANSWER" -> "正在解析用户刚刚提交的回答，并从断点继续。";
            case "COMPLETED" -> "最终回答已落库，任务完成。";
            case "FAILED" -> "任务已经失败，查看后续失败日志获取原因。";
            default -> summary;
        };
    }
}
