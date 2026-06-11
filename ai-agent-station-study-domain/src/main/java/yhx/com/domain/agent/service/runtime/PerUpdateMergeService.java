package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.runtime.MainAgentNotebookVO;
import yhx.com.domain.agent.model.valobj.runtime.NotebookFactVO;
import yhx.com.domain.agent.model.valobj.runtime.NotebookQuestionVO;
import yhx.com.domain.agent.model.valobj.runtime.NotebookRiskVO;
import yhx.com.domain.agent.model.valobj.runtime.NotebookStepVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PerUpdateMergeService {

    private static final Set<String> VALID_STEP_STATUSES = Set.of("PENDING", "IN_PROGRESS", "DONE", "FAILED", "BLOCKED", "CANCELLED");

    public MainAgentNotebookVO merge(MainAgentNotebookVO existing,
                                     Map<String, Object> perUpdate,
                                     Integer loopIndex,
                                     Long sequence) {
        if (perUpdate == null || perUpdate.isEmpty()) {
            return existing;
        }
        MainAgentNotebookVO notebook = existing == null ? emptyNotebook() : ensureLists(existing);
        applyScalarFields(notebook, perUpdate);
        applyStepUpdates(notebook, listOfMaps(perUpdate.get("stepUpdates")), loopIndex, sequence);
        appendFacts(notebook, listOfMaps(perUpdate.get("factsLearned")), loopIndex, sequence);
        appendQuestions(notebook, listOfMaps(perUpdate.get("openQuestions")), loopIndex, sequence);
        appendRisks(notebook, listOfMaps(perUpdate.get("risks")), loopIndex, sequence);
        notebook.setNotebookVersion(notebook.getNotebookVersion() == null ? 1 : notebook.getNotebookVersion() + 1);
        notebook.setLastUpdatedLoopIndex(loopIndex);
        notebook.setLastUpdatedSequence(sequence);
        return notebook;
    }

    private MainAgentNotebookVO emptyNotebook() {
        return MainAgentNotebookVO.builder()
                .notebookVersion(0)
                .steps(new ArrayList<>())
                .facts(new ArrayList<>())
                .openQuestions(new ArrayList<>())
                .risks(new ArrayList<>())
                .metadata(new LinkedHashMap<>())
                .build();
    }

    private MainAgentNotebookVO ensureLists(MainAgentNotebookVO notebook) {
        if (notebook.getSteps() == null) {
            notebook.setSteps(new ArrayList<>());
        }
        if (notebook.getFacts() == null) {
            notebook.setFacts(new ArrayList<>());
        }
        if (notebook.getOpenQuestions() == null) {
            notebook.setOpenQuestions(new ArrayList<>());
        }
        if (notebook.getRisks() == null) {
            notebook.setRisks(new ArrayList<>());
        }
        if (notebook.getMetadata() == null) {
            notebook.setMetadata(new LinkedHashMap<>());
        }
        return notebook;
    }

    @SuppressWarnings("unchecked")
    private void applyScalarFields(MainAgentNotebookVO notebook, Map<String, Object> perUpdate) {
        setIfText(perUpdate.get("mode"), notebook::setMode);
        setIfText(perUpdate.get("goal"), notebook::setGoal);
        setIfText(perUpdate.get("nextStepId"), notebook::setNextStepId);
        setIfText(perUpdate.get("lastDecision"), notebook::setLastDecision);
        Object metadata = perUpdate.get("metadata");
        if (metadata instanceof Map<?, ?> map) {
            notebook.getMetadata().putAll((Map<String, Object>) map);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyStepUpdates(MainAgentNotebookVO notebook,
                                  List<Map<String, Object>> stepUpdates,
                                  Integer loopIndex,
                                  Long sequence) {
        if (stepUpdates.isEmpty()) {
            return;
        }
        Map<String, NotebookStepVO> byId = new LinkedHashMap<>();
        for (NotebookStepVO step : notebook.getSteps()) {
            if (step != null && !isBlank(step.getStepId())) {
                byId.put(step.getStepId(), step);
            }
        }
        for (Map<String, Object> update : stepUpdates) {
            String stepId = stringValue(update.get("stepId"));
            if (isBlank(stepId)) {
                throw new IllegalArgumentException("Notebook step update requires stepId.");
            }
            String status = stringValue(update.get("status"));
            if (!isBlank(status) && !VALID_STEP_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Invalid notebook step status: " + status);
            }
            NotebookStepVO step = byId.get(stepId);
            if (step == null) {
                step = NotebookStepVO.builder()
                        .stepId(stepId)
                        .createdLoopIndex(loopIndex)
                        .createdSequence(sequence)
                        .build();
                notebook.getSteps().add(step);
                byId.put(stepId, step);
            }
            setIfText(update.get("title"), step::setTitle);
            setIfText(update.get("status"), step::setStatus);
            setIfText(update.get("note"), step::setNote);
            if (update.containsKey("relatedWorkIds")) {
                step.setRelatedWorkIds(stringList(update.get("relatedWorkIds")));
            }
            if (update.containsKey("relatedEvidenceIds")) {
                step.setRelatedEvidenceIds(stringList(update.get("relatedEvidenceIds")));
            }
            if (update.get("metadata") instanceof Map<?, ?> map) {
                step.setMetadata(new LinkedHashMap<>((Map<String, Object>) map));
            }
            step.setUpdatedLoopIndex(loopIndex);
            step.setUpdatedSequence(sequence);
        }
    }

    private void appendFacts(MainAgentNotebookVO notebook,
                             List<Map<String, Object>> facts,
                             Integer loopIndex,
                             Long sequence) {
        Map<String, NotebookFactVO> byId = new LinkedHashMap<>();
        for (NotebookFactVO existing : notebook.getFacts()) {
            if (existing != null && !isBlank(existing.getFactId())) {
                byId.put(existing.getFactId(), existing);
            }
        }
        for (Map<String, Object> fact : facts) {
            String factId = firstNonBlank(stringValue(fact.get("factId")), "fact-" + UUID.randomUUID());
            NotebookFactVO existing = byId.get(factId);
            if (existing == null) {
                existing = NotebookFactVO.builder().factId(factId).build();
                notebook.getFacts().add(existing);
                byId.put(factId, existing);
            }
            setIfText(fact.get("content"), existing::setContent);
            if (fact.containsKey("sourceEvidenceIds")) {
                existing.setSourceEvidenceIds(stringList(fact.get("sourceEvidenceIds")));
            }
            if (fact.containsKey("sourceWorkIds")) {
                existing.setSourceWorkIds(stringList(fact.get("sourceWorkIds")));
            }
            existing.setLoopIndex(loopIndex);
            existing.setSequence(sequence);
        }
    }

    private void appendQuestions(MainAgentNotebookVO notebook,
                                 List<Map<String, Object>> questions,
                                 Integer loopIndex,
                                 Long sequence) {
        Map<String, NotebookQuestionVO> byId = new LinkedHashMap<>();
        for (NotebookQuestionVO existing : notebook.getOpenQuestions()) {
            if (existing != null && !isBlank(existing.getId())) {
                byId.put(existing.getId(), existing);
            }
        }
        for (Map<String, Object> question : questions) {
            String id = firstNonBlank(stringValue(question.get("id")), "question-" + UUID.randomUUID());
            NotebookQuestionVO existing = byId.get(id);
            if (existing == null) {
                existing = NotebookQuestionVO.builder().id(id).build();
                notebook.getOpenQuestions().add(existing);
                byId.put(id, existing);
            }
            setIfText(question.get("content"), existing::setContent);
            setIfText(question.get("status"), existing::setStatus);
            if (question.containsKey("sourceEvidenceIds")) {
                existing.setSourceEvidenceIds(stringList(question.get("sourceEvidenceIds")));
            }
            if (question.containsKey("sourceWorkIds")) {
                existing.setSourceWorkIds(stringList(question.get("sourceWorkIds")));
            }
            existing.setLoopIndex(loopIndex);
            existing.setSequence(sequence);
        }
    }

    private void appendRisks(MainAgentNotebookVO notebook,
                             List<Map<String, Object>> risks,
                             Integer loopIndex,
                             Long sequence) {
        Map<String, NotebookRiskVO> byId = new LinkedHashMap<>();
        for (NotebookRiskVO existing : notebook.getRisks()) {
            if (existing != null && !isBlank(existing.getId())) {
                byId.put(existing.getId(), existing);
            }
        }
        for (Map<String, Object> risk : risks) {
            String id = firstNonBlank(stringValue(risk.get("id")), "risk-" + UUID.randomUUID());
            NotebookRiskVO existing = byId.get(id);
            if (existing == null) {
                existing = NotebookRiskVO.builder().id(id).build();
                notebook.getRisks().add(existing);
                byId.put(id, existing);
            }
            setIfText(risk.get("content"), existing::setContent);
            setIfText(risk.get("status"), existing::setStatus);
            if (risk.containsKey("sourceEvidenceIds")) {
                existing.setSourceEvidenceIds(stringList(risk.get("sourceEvidenceIds")));
            }
            if (risk.containsKey("sourceWorkIds")) {
                existing.setSourceWorkIds(stringList(risk.get("sourceWorkIds")));
            }
            existing.setLoopIndex(loopIndex);
            existing.setSequence(sequence);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                mapped.add((Map<String, Object>) map);
            }
        }
        return mapped;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private void setIfText(Object value, java.util.function.Consumer<String> setter) {
        String text = stringValue(value);
        if (!isBlank(text)) {
            setter.accept(text);
        }
    }

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
