package yhx.com.domain.agent.service.observability;

import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.model.valobj.context.EvidenceCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedMemoryVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedRagVO;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MessageCandidateVO;
import yhx.com.domain.agent.model.valobj.context.RagCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public final class AutoAgentHumanLog {

    private AutoAgentHumanLog() {
    }

    public static void stage(String module, String runId, String message) {
        log.info("[AutoAgent][human][{}] runId={}，{}", module, runId, message);
    }

    public static void contextCandidates(String runId, ContextCandidateBundleVO candidates) {
        stage("上下文准备", runId, candidateSummary(candidates));
    }

    public static void contextPlannerOutput(String runId, ContextPlannerOutputVO output) {
        if (output == null) {
            stage("上下文规划", runId, "PlanNode 未返回结果。");
            return;
        }
        int selectedCount = output.getSelectedContext() == null ? 0 : output.getSelectedContext().size();
        String ask = output.getClarificationRequest() == null ? "否" : "是";
        stage("上下文规划", runId, "PlanNode 状态=" + output.getStatus()
                + "，选择候选数=" + selectedCount
                + "，是否询问用户=" + ask
                + optionalReason(output.getReason())
                + selectedRawContext(output));
    }

    public static void contextPlannerResult(String runId, ContextPlannerHandlingResult result) {
        contextPlannerResult(runId, result, null);
    }

    public static void contextPlannerResult(String runId, ContextPlannerHandlingResult result, ContextCandidateBundleVO candidates) {
        if (result == null) {
            stage("上下文规划", runId, "规划处理结果为空。");
            return;
        }
        if (result.getFailure() != null) {
            stage("上下文规划", runId, "规划失败："
                    + result.getFailure().getFailureCode() + "，原因：" + result.getFailure().getMessage());
            return;
        }
        if (result.getAskUserRequest() != null) {
            stage("上下文规划", runId, "需要用户补充信息："
                    + result.getAskUserRequest().getQuestion()
                    + "，输入模式=" + result.getAskUserRequest().getInputMode()
                    + "，选项数=" + size(result.getAskUserRequest().getOptions()));
            return;
        }
        stage("上下文规划", runId, "PlanNode 物化结果\n"
                + "  通过规划的候选：\n"
                + plannerSelectionSummary(result.getEffectiveSelections())
                + "\n"
                + plannerMaterializationSummary(candidates, result)
                + "  即将构建 MainAgentStateView。");
    }

    public static void stateView(String runId, MainAgentStateViewVO stateView) {
        if (stateView == null) {
            stage("状态视图", runId, "MainAgentStateView 为空。");
            return;
        }
        stage("状态视图", runId, stateViewSummary(stateView));
    }

    public static void mainAction(String runId, Integer loopIndex, MainAgentActionVO action) {
        String actionCode = action == null ? null : action.getAction();
        stage("调用主Node", runId, "loop=" + loopIndex + "，MainAgent 决策=" + actionCode);
    }

    public static void failure(String runId, String module, RuntimeSafeFailureVO failure) {
        if (failure == null) {
            stage(module, runId, "失败：未知原因。");
            return;
        }
        stage(module, runId, "失败：code=" + (failure.getFailureCode() == null ? null : failure.getFailureCode().code())
                + "，阶段=" + (failure.getPhase() == null ? null : failure.getPhase().code())
                + "，原因=" + failure.getDeveloperMessage()
                + "，可重试=" + failure.getRetryable());
    }

    public static void vectorRecallFailed(String runId, String sessionId, long timeoutMillis, Throwable error) {
        stage("向量记忆召回", runId, "召回失败或超时：sessionId=" + sessionId
                + "，超时阈值=" + timeoutMillis + "ms"
                + "，原因=" + (error == null ? "unknown" : error.toString())
                + "。本轮会降级为只使用 MySQL 固定上下文。");
    }

    public static String candidateSummary(ContextCandidateBundleVO candidates) {
        if (candidates == null) {
            return "候选为空。";
        }
        String memoryPreview = previewMemories(candidates.getMemoryCandidates());
        String summaryPreview = previewSummaries(candidates.getSessionSummaries());
        String artifactPreview = previewArtifacts(candidates.getArtifactCandidates());
        String evidencePreview = previewEvidence(candidates.getEvidenceCandidates());
        String ragPreview = previewRagCandidates(candidates.getRagCandidates());
        String messagePreview = previewMessages(candidates.getFixedRecentMessages());
        return "候选收集完成\n"
                + "  汇总：固定近轮全文=" + size(candidates.getFixedRecentMessages())
                + "，规划消息=" + size(candidates.getRecentMessages())
                + "，会话摘要=" + size(candidates.getSessionSummaries())
                + "，长期记忆=" + size(candidates.getMemoryCandidates())
                + "，证据=" + size(candidates.getEvidenceCandidates())
                + "，RAG候选=" + size(candidates.getRagCandidates())
                + "，用户澄清=" + size(candidates.getUserClarifications())
                + "，任务摘要=" + (candidates.getSessionTaskSummary() == null ? "无" : "有")
                + (candidates.getSessionTaskSummary() == null ? "" : "：" + preview(candidates.getSessionTaskSummary().getSummary(), 80))
                + (messagePreview.isBlank() ? "" : "\n  近轮全文候选：\n" + messagePreview)
                + (summaryPreview.isBlank() ? "" : "\n  摘要候选：\n" + summaryPreview)
                + (memoryPreview.isBlank() ? "" : "\n  长期记忆候选：\n" + memoryPreview)
                + (artifactPreview.isBlank() ? "" : "\n  产物候选：\n" + artifactPreview)
                + (evidencePreview.isBlank() ? "" : "\n  证据候选：\n" + evidencePreview)
                + (ragPreview.isBlank() ? "" : "\n  RAG候选：\n" + ragPreview);
    }

    public static String plannerSelectionSummary(List<ContextSelectionVO> selections) {
        if (selections == null || selections.isEmpty()) {
            return "无额外候选";
        }
        return numbered(selections.stream()
                .limit(10)
                .map(selection -> selection.getSourceType() + ":" + emptyToUnknown(selection.getSourceId())
                        + "(注入等级=" + selection.getContextLevel()
                        + ", 优先级=" + selection.getPriority()
                        + ", 置信度=" + selection.getConfidence()
                        + ", 原因=" + preview(selection.getReason(), 80) + ")")
                .toList(), "    ");
    }

    public static String stateViewSummary(MainAgentStateViewVO stateView) {
        if (stateView == null) {
            return "MainAgentStateView 为空。";
        }
        List<MessageCandidateVO> recentMessages = stateView.getConversation() == null ? null : stateView.getConversation().getRecentMessages();
        List<SummaryCandidateVO> summaries = stateView.getConversation() == null ? null : stateView.getConversation().getSummaries();
        return "MainAgentStateView 已注入上下文\n"
                + "  汇总：最近对话=" + size(recentMessages)
                + "，会话摘要=" + size(summaries)
                + "，记忆=" + size(stateView.getMemoryPack())
                + "，RAG=" + size(stateView.getRagPack())
                + "，证据=" + size(stateView.getEvidencePack())
                + (stateView.getConversation() == null || stateView.getConversation().getSessionTaskSummary() == null
                ? "" : "\n  任务摘要：\n    " + preview(stateView.getConversation().getSessionTaskSummary().getSummary(), 160))
                + (previewMessages(recentMessages).isBlank() ? "" : "\n  最近对话明细：\n" + previewMessages(recentMessages))
                + (previewSummaries(summaries).isBlank() ? "" : "\n  注入摘要明细：\n" + previewSummaries(summaries))
                + (previewMaterializedMemories(stateView.getMemoryPack()).isBlank() ? "" : "\n  注入记忆明细：\n" + previewMaterializedMemories(stateView.getMemoryPack()))
                + (previewMaterializedRag(stateView.getRagPack()).isBlank() ? "" : "\n  注入RAG明细：\n" + previewMaterializedRag(stateView.getRagPack()))
                + (previewMaterializedEvidence(stateView.getEvidencePack()).isBlank() ? "" : "\n  注入证据明细：\n" + previewMaterializedEvidence(stateView.getEvidencePack()));
    }

    public static String nodeInvalidSummary(String componentCode, Object failureType, String failureMessage, String rawOutput) {
        return componentCode + " 输出无效：failureType=" + failureType
                + "，原因=" + preview(failureMessage, 240)
                + "，rawChars=" + (rawOutput == null ? 0 : rawOutput.length())
                + (rawOutput == null || rawOutput.isBlank() ? "" : "，rawPreview=" + preview(rawOutput, 240))
                + "，将按调用策略尝试重试、修复或失败。";
    }

    private static String previewMemories(List<MemoryCandidateVO> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }
        return numbered(memories.stream()
                .limit(8)
                .map(memory -> memory.getMemoryType() + ":" + memory.getMemoryId()
                        + "(score=" + firstNonNull(memory.getRelevanceScore(), memory.getSourceScore(), memory.getScore())
                        + ", 来源=" + memory.getSourceChannel()
                        + ")=" + preview(firstNonBlank(memory.getSummary(), memory.getContent()), 80))
                .toList(), "    ");
    }

    private static String previewSummaries(List<SummaryCandidateVO> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "";
        }
        return numbered(summaries.stream()
                .limit(8)
                .map(summary -> summary.getSummaryId()
                        + "(turn=" + summary.getTurnId()
                        + ", score=" + firstNonNull(summary.getRelevanceScore(), summary.getSourceScore())
                        + ", 来源=" + summary.getSourceChannel()
                        + ")=" + preview(summary.getSummary(), 80))
                .toList(), "    ");
    }

    private static String previewMessages(List<MessageCandidateVO> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return numbered(messages.stream()
                .limit(8)
                .map(message -> message.getRole() + ":" + message.getMessageId()
                        + "(turn=" + message.getTurnId()
                        + ", seq=" + message.getSeq()
                        + ")=" + preview(message.getSummary(), 60))
                .toList(), "    ");
    }

    private static String previewArtifacts(List<ArtifactCandidateVO> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return "";
        }
        return numbered(artifacts.stream()
                .limit(6)
                .map(artifact -> artifact.getArtifactType() + ":" + artifact.getArtifactId()
                        + "(title=" + preview(artifact.getTitle(), 40)
                        + ", score=" + firstNonNull(artifact.getTotalScore(), artifact.getSourceScore())
                        + ")=" + preview(artifact.getSummary(), 80))
                .toList(), "    ");
    }

    private static String previewEvidence(List<EvidenceCandidateVO> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "";
        }
        return numbered(evidence.stream()
                .limit(6)
                .map(item -> item.getEvidenceType() + ":" + item.getEvidenceId()
                        + "(score=" + item.getScore()
                        + ", source=" + item.getSourceRef()
                        + ")=" + preview(item.getSummary(), 80))
                .toList(), "    ");
    }

    private static String previewRagCandidates(List<RagCandidateVO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        return numbered(candidates.stream()
                .limit(8)
                .map(candidate -> candidate.getSourceType() + ":" + candidate.getCandidateId()
                        + "(doc=" + candidate.getDocumentId()
                        + ", chunk=" + candidate.getChunkId()
                        + ", score=" + candidate.getSourceScore()
                        + ", 来源=" + candidate.getSourceChannel()
                        + ")=" + preview(firstNonBlank(candidate.getSummary(), candidate.getSnippet()), 80))
                .toList(), "    ");
    }

    private static String previewMaterializedMemories(List<MaterializedMemoryVO> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }
        return numbered(memories.stream()
                .limit(8)
                .map(memory -> memory.getMemoryType() + ":" + memory.getMemoryId()
                        + "=" + preview(firstNonBlank(memory.getSummary(), memory.getContent()), 80))
                .toList(), "    ");
    }

    private static String previewMaterializedEvidence(List<MaterializedEvidenceVO> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "";
        }
        return numbered(evidence.stream()
                .limit(6)
                .map(item -> item.getEvidenceType() + ":" + item.getEvidenceId()
                        + "(source=" + item.getSourceRef()
                        + ")=" + preview(firstNonBlank(item.getSummary(), item.getBoundedSnippet()), 80))
                .toList(), "    ");
    }

    private static String previewMaterializedRag(List<MaterializedRagVO> ragPack) {
        if (ragPack == null || ragPack.isEmpty()) {
            return "";
        }
        return numbered(ragPack.stream()
                .limit(8)
                .map(item -> item.getSourceType() + ":" + item.getCandidateId()
                        + "(doc=" + item.getDocumentId()
                        + ", chunk=" + item.getChunkId()
                        + ", level=" + item.getContextLevel()
                        + ", injectMode=" + item.getInjectMode()
                        + ")=" + preview(firstNonBlank(item.getSummary(), firstNonBlank(item.getBoundedSnippet(), item.getContent())), 80))
                .toList(), "    ");
    }

    public static String plannerMaterializationSummary(ContextCandidateBundleVO candidates, ContextPlannerHandlingResult result) {
        if (candidates == null || candidates.getSessionSummaries() == null || candidates.getSessionSummaries().isEmpty()) {
            return "";
        }
        List<ContextSelectionVO> selections = result == null ? List.of() : result.getEffectiveSelections();
        MainAgentStateViewVO stateView = result == null ? null : result.getStateView();
        Set<String> injectedSummaryIds = stateView == null || stateView.getConversation() == null || stateView.getConversation().getSummaries() == null
                ? Set.of()
                : stateView.getConversation().getSummaries().stream().map(SummaryCandidateVO::getSummaryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> injectedTurnIds = stateView == null || stateView.getConversation() == null || stateView.getConversation().getRecentMessages() == null
                ? Set.of()
                : stateView.getConversation().getRecentMessages().stream().map(MessageCandidateVO::getTurnId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> fixedTurnIds = candidates.getFixedRecentMessages() == null
                ? Set.of()
                : candidates.getFixedRecentMessages().stream().map(MessageCandidateVO::getTurnId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<String> lines = candidates.getSessionSummaries().stream()
                .limit(12)
                .map(summary -> summary.getSummaryId() + "(turn=" + summary.getTurnId() + ") -> " + summaryMaterializationReason(summary, selections, injectedSummaryIds, injectedTurnIds, fixedTurnIds)
                        + "；摘要=" + preview(summary.getSummary(), 70))
                .toList();
        return "  摘要候选处理结果：\n" + numbered(lines, "    ") + "\n";
    }

    private static String summaryMaterializationReason(SummaryCandidateVO summary,
                                                       List<ContextSelectionVO> selections,
                                                       Set<String> injectedSummaryIds,
                                                       Set<String> injectedTurnIds,
                                                       Set<String> fixedTurnIds) {
        ContextSelectionVO selection = findSummarySelection(summary, selections);
        if (selection == null) {
            return "未选择";
        }
        if (summary.getTurnId() != null && fixedTurnIds.contains(summary.getTurnId())) {
            return "已选择但被固定近轮全文覆盖，避免重复注入";
        }
        if (injectedSummaryIds.contains(summary.getSummaryId())) {
            return "已按" + selection.getContextLevel() + "注入摘要";
        }
        if (selection.getContextLevel() != null
                && "FULL_TEXT".equals(selection.getContextLevel().code())
                && summary.getTurnId() != null
                && injectedTurnIds.contains(summary.getTurnId())) {
            return "已按FULL_TEXT转为该轮用户/助手原文";
        }
        return "已选择但未进入StateView，可能缺少turn/payload或被预算/去重过滤";
    }

    private static ContextSelectionVO findSummarySelection(SummaryCandidateVO summary, List<ContextSelectionVO> selections) {
        if (summary == null || selections == null) {
            return null;
        }
        return selections.stream()
                .filter(selection -> selection != null)
                .filter(selection -> {
                    String sourceType = selection.getSourceType();
                    boolean summaryMatched = ("TURN_SUMMARY".equals(sourceType)
                            || "SESSION_SUMMARY".equals(sourceType)
                            || "SUMMARY".equals(sourceType))
                            && Objects.equals(summary.getSummaryId(), selection.getSourceId());
                    boolean turnMatched = "TURN".equals(sourceType)
                            && Objects.equals(summary.getTurnId(), selection.getSourceId());
                    return summaryMatched || turnMatched;
                })
                .findFirst()
                .orElse(null);
    }

    private static String numbered(List<String> lines, String indent) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        String prefix = indent == null ? "" : indent;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append("\n");
            }
            builder.append(prefix).append(i + 1).append(". ").append(lines.get(i));
        }
        return builder.toString();
    }

    private static String optionalReason(String reason) {
        return reason == null || reason.isBlank() ? "" : "，原因=" + preview(reason, 120);
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String selectedRawContext(ContextPlannerOutputVO output) {
        if (output == null || output.getSelectedContext() == null || output.getSelectedContext().isEmpty()) {
            return "";
        }
        String details = output.getSelectedContext().stream()
                .limit(10)
                .map(item -> stringValue(item, "sourceType") + ":" + emptyToUnknown(firstNonBlank(
                        stringValue(item, "sourceId"),
                        firstNonBlank(stringValue(item, "summaryId"),
                                firstNonBlank(stringValue(item, "turnId"),
                                        firstNonBlank(stringValue(item, "artifactId"),
                                                firstNonBlank(stringValue(item, "memoryId"),
                                                        firstNonBlank(stringValue(item, "evidenceId"), stringValue(item, "messageId"))))))))
                        + "(useLevel=" + firstNonBlank(stringValue(item, "contextLevel"), stringValue(item, "useLevel"))
                        + ", reason=" + preview(stringValue(item, "reason"), 80) + ")")
                .collect(Collectors.joining("; "));
        return "；原始选中明细=" + details;
    }

    private static String stringValue(java.util.Map<String, Object> item, String key) {
        if (item == null || item.get(key) == null) {
            return null;
        }
        return String.valueOf(item.get(key));
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String emptyToUnknown(String value) {
        return value == null || value.isBlank() ? "<缺失ID>" : value;
    }

    private static String preview(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
