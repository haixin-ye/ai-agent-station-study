package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunTranscriptRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTranscriptEntity;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TranscriptBlockTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class RunTranscriptRecorder {

    private final IRunTranscriptRepository transcriptRepository;
    private final IPayloadRepository payloadRepository;

    public RunTranscriptRecorder(IRunTranscriptRepository transcriptRepository, IPayloadRepository payloadRepository) {
        this.transcriptRepository = transcriptRepository;
        this.payloadRepository = payloadRepository;
    }

    public void appendUserMessage(String runId, String sessionId, String messageId, String payloadRef) {
        append(runId, TranscriptBlockTypeEnumVO.USER_MESSAGE, refPayload(sessionId, messageId, payloadRef), payloadRef, false);
    }

    public void appendContextPlan(String runId, Integer loopIndex, ContextPlannerOutputVO output, String payloadRef) {
        append(runId, TranscriptBlockTypeEnumVO.RUNTIME_EVENT, JSON.toJSONString(output), payloadRef, true);
    }

    public void appendStateViewSummary(String runId, Integer loopIndex, MainAgentStateViewVO stateView, String payloadRef) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("loopIndex", loopIndex);
        summary.put("userInput", stateView == null || stateView.getUserInput() == null ? null : stateView.getUserInput().getContent());
        summary.put("artifactContentCount", stateView == null || stateView.getArtifactContent() == null ? 0 : stateView.getArtifactContent().size());
        append(runId, TranscriptBlockTypeEnumVO.RUNTIME_EVENT, JSON.toJSONString(summary), payloadRef, true);
    }

    public void appendAssistantAction(String runId, Integer loopIndex, MainAgentActionVO action, String payloadRef) {
        append(runId, TranscriptBlockTypeEnumVO.RUNTIME_EVENT, JSON.toJSONString(action), payloadRef, true);
    }

    public void appendUserReply(String runId, Integer loopIndex, UserAnswerVO answer, String payloadRef) {
        append(runId, TranscriptBlockTypeEnumVO.USER_MESSAGE, JSON.toJSONString(answer), payloadRef, false);
    }

    public void appendError(String runId, Integer loopIndex, RuntimeFailureCodeEnumVO failureCode, String summary, String payloadRef) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loopIndex", loopIndex);
        payload.put("failureCode", failureCode == null ? null : failureCode.code());
        payload.put("summary", summary);
        append(runId, TranscriptBlockTypeEnumVO.RUNTIME_EVENT, JSON.toJSONString(payload), payloadRef, true);
    }

    private void append(String runId, TranscriptBlockTypeEnumVO blockType, String content, String existingPayloadRef, boolean compactable) {
        if (transcriptRepository == null) {
            return;
        }
        String payloadRef = existingPayloadRef;
        if ((payloadRef == null || payloadRef.isBlank()) && payloadRepository != null) {
            payloadRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                    .payloadType(PayloadTypeEnumVO.TRANSCRIPT_BLOCK)
                    .content(content)
                    .preview(preview(content))
                    .createdAt(LocalDateTime.now())
                    .build());
        }
        transcriptRepository.appendBlock(AgentRunTranscriptEntity.builder()
                .runId(runId)
                .blockType(blockType)
                .payloadRef(payloadRef)
                .compactable(compactable)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String refPayload(String sessionId, String messageId, String payloadRef) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("messageId", messageId);
        payload.put("payloadRef", payloadRef);
        return JSON.toJSONString(payload);
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }
}
