package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.context.ContextPlannerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.service.contract.StateDeltaScopeRules;

import java.util.Arrays;
import java.util.stream.Collectors;

public class OutputContractPromptRenderer {

    public String renderFor(String componentCode, String contractVersion) {
        return switch (componentCode) {
            case "MAIN_AGENT", "FINAL_REPAIR" -> renderMainAgentActionContract();
            case "CONTEXT_PLANNER" -> renderContextPlannerOutputContract();
            case "RAG_VERIFIER", "TOOL_VERIFIER" -> renderVerificationResultContract();
            case "FINAL_RESPONSE_GUARD" -> renderFinalResponseGuardResultContract();
            case "CONTRACT_REPAIR" -> renderRepairContract(componentCode, contractVersion);
            case "TURN_SUMMARY" -> renderTurnSummaryContract();
            case "MEMORY_EXTRACTOR" -> renderMemoryExtractionContract();
            case "SESSION_TASK_SUMMARY" -> renderSessionTaskSummaryContract();
            case "CONVERSATION_ROLLUP" -> renderConversationRollupContract();
            default -> "Return one JSON object that satisfies component contract version " + contractVersion + ".";
        };
    }

    public String renderMainAgentActionContract() {
        return """
                Required top-level fields:
                - action: one of %s
                - stateDelta: object
                Forbidden top-level fields:
                - runId, sessionId, runStatus, runtimePhase, loopIndex, nextPhase, trace, audit, toolReceipt, ragWasUsed

                StateDelta allowed fields by action:
                %s

                Valid examples:
                {"action":"FINAL","stateDelta":{"finalAnswerCandidate":{"content":"Answer text for the user."}}}
                {"action":"CREATE_ARTIFACT","stateDelta":{"artifactDraft":{"artifactType":"ARTICLE","title":"RAG notes","content":"..."},"finalAnswerCandidate":{"content":"Article draft created."}}}
                {"action":"UPDATE_ARTIFACT","stateDelta":{"artifactPatch":{"artifactId":"artifact-1","patchType":"REPLACE_CONTENT","content":"..."}}}
                {"action":"RETRIEVE_RAG","stateDelta":{"ragRequest":{"query":"Find the uploaded project document section about deployment rules.","topK":5}}}
                {"action":"CALL_TOOL","stateDelta":{"toolIntent":{"toolName":"csdn.publish","intent":"Publish selected artifact.","arguments":{"artifactId":"artifact-1"}}}}
                {"action":"ASK_USER","stateDelta":{"askUserRequest":{"question":"Which article should I use?","inputMode":"SINGLE_CHOICE","options":[{"optionId":"article-1","label":"Latest article","value":{"artifactId":"artifact-1"}}]}}}
                {"action":"PLAN","stateDelta":{"planDraft":{"steps":["retrieve evidence","write answer"]}}}
                {"action":"CONTINUE","stateDelta":{"nextActionHint":{"reason":"Need another loop after context update."}}}
                {"action":"REPAIR_FINAL","stateDelta":{"finalAnswerCandidate":{"content":"Repaired clean answer."}}}
                {"action":"FAIL","stateDelta":{"failure":{"message":"The request cannot be completed safely right now."}}}
                """.formatted(actionCodes(), stateDeltaScopeTable());
    }

    public String renderContextPlannerOutputContract() {
        return """
                Required top-level fields:
                - status: one of %s
                - selectedContext: array, required when status is READY

                Context level values:
                - METADATA_ONLY
                - SUMMARY_PLUS_SNIPPET
                - FULL_TEXT
                - CHUNKED_CONTEXT

                Valid examples:
                {"status":"READY","selectedContext":[{"sourceType":"ARTIFACT","artifactId":"artifact-1","useLevel":"FULL_TEXT","reason":"User asked to rewrite the article."}]}
                {"status":"NEEDS_USER_CLARIFICATION","clarificationRequest":{"question":"Which article do you want to use?","inputMode":"SINGLE_CHOICE_OR_FREE_TEXT","options":[{"optionId":"article-1","label":"Latest article","value":{"artifactId":"artifact-1"}}]}}
                """.formatted(contextStatusCodes());
    }

    public String renderVerificationResultContract() {
        return """
                Required top-level fields:
                - status: PASSED, FAILED, or SKIPPED
                - failureCode: nullable string
                - detail: short diagnostic text for Runtime, not for final user display

                Valid examples:
                {"status":"PASSED","failureCode":null,"detail":"Answer is grounded in retrieved evidence."}
                {"status":"FAILED","failureCode":"RAG_UNGROUNDED","detail":"The answer asserts facts that do not appear in evidence."}
                """;
    }

    public String renderFinalResponseGuardResultContract() {
        return """
                Required top-level fields:
                - status: PASSED or FAILED
                - finalContent: clean user-facing content when passed
                - failureCode: nullable string
                - detail: diagnostic text for Runtime
                """;
    }

    public String renderRepairContract(String originalComponentCode, String contractVersion) {
        return """
                Repair the invalid output for component %s and contract %s.
                Required output is the same JSON object expected from the original component.
                Do not add repair explanations.
                """.formatted(originalComponentCode, contractVersion);
    }

    public String renderTurnSummaryContract() {
        return """
                Required contract version: turn-summary-output-v1

                Required top-level fields:
                - summary: concise string
                - intent: concise string
                - topics: array of strings
                - entities: array of objects
                - artifactRefs: array of strings
                - importanceScore: number from 0.0 to 1.0
                - requiresLongTermExtraction: boolean

                Valid example:
                {"summary":"User asked for an RAG article and the agent drafted a structured explanation.","intent":"create article","topics":["RAG","article"],"entities":[],"artifactRefs":["artifact-1"],"importanceScore":0.7,"requiresLongTermExtraction":false}
                """;
    }

    public String renderMemoryExtractionContract() {
        return """
                Required contract version: memory-extraction-output-v1

                Required top-level fields:
                - memories: array

                Each memories item:
                - memoryType: LONG_TERM_MEMORY or USER_PREFERENCE
                - summary: concise durable memory text
                - content: optional fuller memory text
                - score: number from 0.0 to 1.0
                - reason: short diagnostic reason

                Valid examples:
                {"memories":[]}
                {"memories":[{"memoryType":"USER_PREFERENCE","summary":"User prefers detailed Chinese engineering explanations.","content":"User explicitly asked for detailed Chinese engineering explanations in future work.","score":0.9,"reason":"Explicit stable preference."}]}
                """;
    }

    public String renderConversationRollupContract() {
        return """
                Required contract version: conversation-rollup-output-v1

                Required top-level fields:
                - summary: concise rolling conversation summary string

                Valid example:
                {"summary":"User planned an AutoAgent memory architecture, approved MySQL/vector parallel recall, and the agent implemented vector indexing and GC worker foundations."}
                """;
    }

    public String renderSessionTaskSummaryContract() {
        return """
                Required contract version: session-task-summary-output-v1

                Required top-level fields:
                - shouldUpdate: boolean
                - mainTasks: array of strings
                - currentTask: nullable string
                - importantDecisions: array of strings
                - latestProgress: array of strings
                - openQuestions: array of strings
                - obsoleteTasks: array of strings

                Valid examples:
                {"shouldUpdate":false,"mainTasks":[],"currentTask":null,"importantDecisions":[],"latestProgress":[],"openQuestions":[],"obsoleteTasks":[]}
                {"shouldUpdate":true,"mainTasks":["Redesign AutoAgent memory system"],"currentTask":"Implement session task summary GC worker","importantDecisions":["Use MySQL for session task summary state"],"latestProgress":["Session task summary persistence exists"],"openQuestions":[],"obsoleteTasks":["Rolling conversation summary design"]}
                """;
    }

    private String actionCodes() {
        return Arrays.stream(MainAgentActionTypeEnumVO.values()).map(MainAgentActionTypeEnumVO::code).collect(Collectors.joining(", "));
    }

    private String contextStatusCodes() {
        return Arrays.stream(ContextPlannerStatusEnumVO.values()).map(ContextPlannerStatusEnumVO::code).collect(Collectors.joining(", "));
    }

    private String stateDeltaScopeTable() {
        return Arrays.stream(MainAgentActionTypeEnumVO.values())
                .map(action -> "- " + action.code() + ": " + StateDeltaScopeRules.allowedFields(action.code()))
                .collect(Collectors.joining("\n"));
    }
}
