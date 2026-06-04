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
            case "MEMORY_GOVERNANCE" -> renderMemoryGovernanceContract();
            case "CONVERSATION_ROLLUP" -> renderConversationRollupContract();
            default -> "Return one JSON object that satisfies component contract version " + contractVersion + ".";
        };
    }

    public String renderMainAgentActionContract() {
        return """
                Output exactly one valid JSON object.

                Required top-level fields:
                - perUpdate: object
                - action: one of %s
                - stateDelta: object

                Forbidden top-level fields:
                - runId, sessionId, runStatus, runtimePhase, loopIndex, nextPhase, trace, audit, toolReceipt, developerTrace, ragWasUsed

                perUpdate contract:
                - perUpdate updates notebook before Runtime executes the action.
                - perUpdate is not hidden reasoning and not an arbitrary state patch.
                - perUpdate.mode is required: DIRECT or PER.
                - Use {"mode":"DIRECT","lastDecision":"..."} for simple one-step answers.
                - Use {"mode":"PER","goal":"...","stepUpdates":[...],"nextStepId":"...","lastDecision":"..."} for multi-step work.
                - Valid PER fields: mode, goal, stepUpdates, factsLearned, openQuestions, risks, nextStepId, lastDecision, metadata.
                - Valid stepUpdates item fields: stepId, title, status, note, relatedWorkIds, relatedEvidenceIds, metadata.
                - Valid step status values: PENDING, IN_PROGRESS, DONE, FAILED, BLOCKED, CANCELLED.
                - Use FAILED when a step was actually attempted and failed. Use BLOCKED when a step cannot proceed because information, approval, target, capability, or another prerequisite is missing.
                - Do not output learnedFacts. Use factsLearned.
                - Do not output unsupported step statuses such as COMPLETED, ERROR, or SKIPPED.
                - Keep perUpdate concise. Do not include chain-of-thought.

                stateDelta contract:
                - stateDelta is only the payload for the selected action.
                - stateDelta is not an arbitrary notebook, Runtime, or StateView patch.
                - The selected action determines the only allowed stateDelta field:
                StateDelta allowed fields by action:
                %s

                Action-specific stateDelta schema:
                - FINAL: stateDelta must contain finalAnswerCandidate.content. It must not contain ragRequest, toolIntent, askUserRequest, planDraft, nextActionHint, or failure.
                - RETRIEVE_RAG: stateDelta must contain ragRequest.query. Optional ragRequest fields include topK, sourceHints, filters, and reason.
                - CALL_TOOL: stateDelta must contain toolIntent. toolIntent must include capabilityCode, toolName, goal, and arguments. Optional fields include mcpServerCode and expectedOutcome. capabilityCode and toolName must match a tool capability exposed in availableCapabilities. Use the exposed alias values exactly; do not use MCP discovery/internal wrapper names that are not exposed as capabilities. Do not output repeatGuardKey; Runtime owns it.
                - ASK_USER: stateDelta must contain askUserRequest.question and askUserRequest.inputMode. FREE_TEXT requires allowFreeText=true and options=[]. SINGLE_CHOICE requires allowFreeText=false and non-empty options. SINGLE_CHOICE_OR_FREE_TEXT requires allowFreeText=true and non-empty options. CONFIRM requires allowFreeText=false and concrete approve/reject-style options.
                - PLAN: stateDelta may contain planDraft only for rare plan-only or legacy compatibility cases. PLAN is not required for normal PER. If no planDraft is needed, perUpdate must still contain the meaningful plan update.
                - CONTINUE: stateDelta must contain nextActionHint with a non-empty reason.
                - REPAIR_FINAL: stateDelta must contain finalAnswerCandidate.content and should only be used when Runtime explicitly requests final-answer repair.
                - FAIL: stateDelta must contain failure.message. Optional failure fields include code, recoverable, and suggestedResolution.

                Valid examples:
                {"perUpdate":{"mode":"DIRECT","lastDecision":"answer ready"},"action":"FINAL","stateDelta":{"finalAnswerCandidate":{"content":"Answer text for the user."}}}
                {"perUpdate":{"mode":"PER","goal":"retrieve deployment rules","stepUpdates":[{"stepId":"s1","title":"retrieve private evidence","status":"IN_PROGRESS"}],"nextStepId":"s1","lastDecision":"need private evidence"},"action":"RETRIEVE_RAG","stateDelta":{"ragRequest":{"query":"Find the uploaded project document section about deployment rules.","topK":5}}}
                {"perUpdate":{"mode":"PER","goal":"inspect folder","stepUpdates":[{"stepId":"s1","title":"resolve folder","status":"IN_PROGRESS"}],"nextStepId":"s1","lastDecision":"resolve first"},"action":"CALL_TOOL","stateDelta":{"toolIntent":{"capabilityCode":"file_system_search_files","toolName":"search_files","goal":"Find domain folders before reading files.","arguments":{"path":".","pattern":"**/*domain*"}}}}
                {"perUpdate":{"mode":"PER","goal":"publish approved content","stepUpdates":[{"stepId":"s1","title":"publish through tool","status":"IN_PROGRESS"}],"nextStepId":"s1","lastDecision":"request tool execution"},"action":"CALL_TOOL","stateDelta":{"toolIntent":{"capabilityCode":"publish_csdn","toolName":"csdn.publish","goal":"Publish approved content.","arguments":{"contentRef":"payload-1"}}}}
                {"perUpdate":{"mode":"PER","goal":"choose topic","stepUpdates":[{"stepId":"s1","title":"ask topic","status":"BLOCKED"}],"nextStepId":"s1","lastDecision":"need user choice"},"action":"ASK_USER","stateDelta":{"askUserRequest":{"question":"Which topic should I use?","inputMode":"SINGLE_CHOICE","options":[{"optionId":"topic_1","label":"MCP deployment","value":{"topic":"MCP deployment"}}]}}}
                {"perUpdate":{"mode":"PER","goal":"learn hometown","stepUpdates":[{"stepId":"s1","title":"ask hometown","status":"BLOCKED"}],"nextStepId":"s1","lastDecision":"need user answer"},"action":"ASK_USER","stateDelta":{"askUserRequest":{"question":"What is your hometown?","inputMode":"FREE_TEXT","allowFreeText":true,"options":[]}}}
                {"perUpdate":{"mode":"PER","goal":"answer with evidence","stepUpdates":[{"stepId":"s1","title":"retrieve evidence","status":"PENDING"},{"stepId":"s2","title":"write answer","status":"PENDING"}],"nextStepId":"s1","lastDecision":"user asked to plan before execution"},"action":"PLAN","stateDelta":{"planDraft":{"goal":"answer with evidence","steps":[{"stepId":"s1","title":"retrieve evidence","status":"PENDING"},{"stepId":"s2","title":"write answer","status":"PENDING"}]}}}
                {"perUpdate":{"mode":"PER","lastDecision":"Need another loop after context update."},"action":"CONTINUE","stateDelta":{"nextActionHint":{"reason":"Need another loop after context update."}}}
                {"perUpdate":{"mode":"PER","goal":"answer from tool evidence","stepUpdates":[{"stepId":"s1","title":"read requested file","status":"DONE","relatedEvidenceIds":["evidence-tool-1"]}],"factsLearned":[{"factId":"fact-file-1","content":"The requested file content is available in evidence-tool-1.","sourceEvidenceIds":["evidence-tool-1"]}],"lastDecision":"tool evidence is sufficient; answer now"},"action":"FINAL","stateDelta":{"finalAnswerCandidate":{"content":"Summary based on the file evidence: ..."}}}
                {"perUpdate":{"mode":"PER","goal":"write requested file","stepUpdates":[{"stepId":"s1","title":"write desktop file","status":"FAILED","relatedEvidenceIds":["evidence-tool-failed"],"note":"The file write tool failed; use the evidence message to decide whether a corrected retry is possible."},{"stepId":"s2","title":"choose recovery path","status":"IN_PROGRESS"}],"factsLearned":[{"factId":"fact-tool-failed","content":"The attempted file write failed; see evidence-tool-failed for the concrete tool error.","sourceEvidenceIds":["evidence-tool-failed"]}],"nextStepId":"s2","lastDecision":"tool failed; recover or explain limitation"},"action":"FINAL","stateDelta":{"finalAnswerCandidate":{"content":"I could not save the file because the file tool failed with the reported error. Here is the content so you can still use it: ..."}}}
                {"perUpdate":{"mode":"DIRECT","lastDecision":"repair final answer"},"action":"REPAIR_FINAL","stateDelta":{"finalAnswerCandidate":{"content":"Repaired clean answer."}}}
                {"perUpdate":{"mode":"DIRECT","lastDecision":"cannot complete safely"},"action":"FAIL","stateDelta":{"failure":{"message":"The request cannot be completed safely right now."}}}
                """.formatted(actionCodes(), stateDeltaScopeTable());
    }

    public String renderContextPlannerOutputContract() {
        return """
                Required top-level fields:
                - status: prefer READY, NO_RELEVANT_CONTEXT, or NEEDS_USER_CLARIFICATION
                - selectedContext: array

                Context level values:
                - METADATA_ONLY
                - SUMMARY_ONLY
                - SUMMARY_PLUS_SNIPPET
                - FULL_TEXT
                - CHUNKED_CONTEXT

                READY:
                - Use when necessary additional context has been selected.
                - selectedContext must be a non-empty array.

                NO_RELEVANT_CONTEXT:
                - Use when no additional context should be materialized.
                - selectedContext must be an empty array.

                NEEDS_USER_CLARIFICATION:
                - Use only when candidate identity or user intent cannot be resolved safely after inspecting all available context.
                - selectedContext must be an empty array.
                - clarificationRequest is required with question, inputMode, allowFreeText, and options.
                - inputMode must be SINGLE_CHOICE, SINGLE_CHOICE_OR_FREE_TEXT, or FREE_TEXT.
                - FREE_TEXT requires allowFreeText=true and options=[].
                - SINGLE_CHOICE requires allowFreeText=false and non-empty options.
                - SINGLE_CHOICE_OR_FREE_TEXT requires allowFreeText=true and non-empty options.

                selectedContext item contract:
                - sourceType: required
                - sourceId: required; use an id that exists in the current StateView candidate, never invent ids
                - useLevel: required
                - reason: required and concise
                - priority: optional
                - confidence: optional

                Valid sourceType and useLevel:
                - SESSION_SUMMARY: sourceId=summaryId; useLevel SUMMARY_ONLY, SUMMARY_PLUS_SNIPPET, or FULL_TEXT
                - MEMORY: sourceId=memoryId; useLevel METADATA_ONLY, SUMMARY_ONLY, SUMMARY_PLUS_SNIPPET, or FULL_TEXT
                - EVIDENCE: sourceId=evidenceId; useLevel SUMMARY_ONLY, SUMMARY_PLUS_SNIPPET, or FULL_TEXT
                - RAG_FILE_CHUNK: sourceId=candidateId or chunkId; useLevel CHUNKED_CONTEXT only
                - RAG_CODE_FILE_SUMMARY: sourceId=candidateId or documentId; useLevel SUMMARY_ONLY or FULL_TEXT
                - RAG_CODE_CHUNK: sourceId=candidateId or chunkId; useLevel CHUNKED_CONTEXT only
                - ARTIFACT: sourceId=artifactId; useLevel METADATA_ONLY, SUMMARY_ONLY, SUMMARY_PLUS_SNIPPET, or FULL_TEXT
                - ARTIFACT_CHUNK: sourceId=chunkId or sourceId; useLevel SUMMARY_PLUS_SNIPPET or CHUNKED_CONTEXT

                Valid examples:
                {"status":"NO_RELEVANT_CONTEXT","selectedContext":[]}
                {"status":"READY","selectedContext":[{"sourceType":"SESSION_SUMMARY","sourceId":"turn-summary-1","useLevel":"FULL_TEXT","reason":"User asked to reuse the previous draft."}]}
                {"status":"READY","selectedContext":[{"sourceType":"RAG_FILE_CHUNK","sourceId":"rag-candidate-1","useLevel":"CHUNKED_CONTEXT","reason":"The chunk contains the requested contract clause."}]}
                {"status":"NEEDS_USER_CLARIFICATION","selectedContext":[],"clarificationRequest":{"question":"Which previous draft do you mean?","inputMode":"SINGLE_CHOICE_OR_FREE_TEXT","allowFreeText":true,"options":[{"optionId":"summary_1","label":"Product intro draft","value":{"sourceType":"SESSION_SUMMARY","sourceId":"summary-product-draft"}},{"optionId":"summary_2","label":"Email reply draft","value":{"sourceType":"SESSION_SUMMARY","sourceId":"summary-email-draft"}}]}}
                {"status":"NEEDS_USER_CLARIFICATION","selectedContext":[],"clarificationRequest":{"question":"What is your hometown?","inputMode":"FREE_TEXT","allowFreeText":true,"options":[]}}
                """;
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
                - artifactRefs: array of strings, normally empty because AutoAgent no longer uses artifact actions
                - importanceScore: number from 0.0 to 1.0
                - requiresLongTermExtraction: boolean

                Valid example:
                {"summary":"User asked for an RAG article and the agent drafted a structured explanation.","intent":"write article","topics":["RAG","article"],"entities":[],"artifactRefs":[],"importanceScore":0.7,"requiresLongTermExtraction":false}
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
                - content: required fuller factual memory text for MainAgent
                - recallText: required semantic-search text with likely future query aliases and user wording
                - score: number from 0.0 to 1.0
                - reason: short diagnostic reason

                Valid examples:
                {"memories":[]}
                {"memories":[{"memoryType":"USER_PREFERENCE","summary":"用户偏好详细的中文工程解释。","content":"用户明确要求后续回答使用详细的中文工程解释。","recallText":"用户偏好、回答风格、喜欢、希望以后、默认回答方式是详细中文工程解释。","score":0.9,"reason":"用户明确表达了稳定回答偏好。"}]}
                {"memories":[{"memoryType":"LONG_TERM_MEMORY","summary":"用户居住在西安。","content":"用户明确表示自己居住在西安。","recallText":"用户家乡、故乡、老家、居住地、所在城市、住在哪里、来自哪里、本地、当地、家乡美食、当地特色是西安。","score":0.9,"reason":"用户明确表达了稳定居住地信息。"}]}
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

    public String renderMemoryGovernanceContract() {
        return """
                Required contract version: memory-governance-output-v1

                Required top-level fields:
                - actions: array

                Each actions item:
                - action: KEEP, DISABLE, SUPERSEDE, or NOOP
                - memoryId: memory id from input
                - targetMemoryId: required only for SUPERSEDE
                - reason: short diagnostic reason

                Valid examples:
                {"actions":[]}
                {"actions":[{"action":"DISABLE","memoryId":"memory-1","targetMemoryId":null,"reason":"One-off task, not durable memory."}]}
                {"actions":[{"action":"SUPERSEDE","memoryId":"memory-old","targetMemoryId":"memory-new","reason":"Newer memory replaces older preference."}]}
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
