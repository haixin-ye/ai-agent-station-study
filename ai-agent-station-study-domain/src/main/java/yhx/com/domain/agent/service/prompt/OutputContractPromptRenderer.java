package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentProfileTypeEnumVO;
import yhx.com.domain.agent.service.agent.AgentProfileRegistry;

import java.util.Comparator;
import java.util.stream.Collectors;

public class OutputContractPromptRenderer {

    public String renderFor(String componentCode, String contractVersion) {
        return switch (componentCode) {
            case "MAIN_AGENT" -> requireVersion(componentCode, contractVersion, "main-agent-action-v2",
                    renderMainAgentActionContractV2());
            case "FINAL_REPAIR" -> requireVersion(componentCode, contractVersion, "final-repair-action-v1",
                    renderFinalRepairContract());
            case "GENERIC_SUB_AGENT" -> requireVersion(componentCode, contractVersion, "generic-sub-agent-action-v1",
                    renderSubAgentActionContract());
            case "CONTEXT_PLANNER" -> requireVersion(componentCode, contractVersion, "context-planner-output-v1",
                    renderContextPlannerOutputContract());
            case "RAG_VERIFIER", "TOOL_VERIFIER" -> requireVersion(componentCode, contractVersion, "verification-result-v1",
                    renderVerificationResultContract());
            case "FINAL_RESPONSE_GUARD" -> requireVersion(componentCode, contractVersion, "final-response-guard-result-v1",
                    renderFinalResponseGuardResultContract());
            case "CONTRACT_REPAIR" -> renderRepairContract(componentCode, contractVersion);
            case "TURN_SUMMARY" -> requireVersion(componentCode, contractVersion, "turn-summary-output-v1",
                    renderTurnSummaryContract());
            case "MEMORY_EXTRACTOR" -> requireVersion(componentCode, contractVersion, "memory-extraction-output-v1",
                    renderMemoryExtractionContract());
            case "SESSION_TASK_SUMMARY" -> requireVersion(componentCode, contractVersion, "session-task-summary-output-v1",
                    renderSessionTaskSummaryContract());
            case "MEMORY_GOVERNANCE" -> requireVersion(componentCode, contractVersion, "memory-governance-output-v1",
                    renderMemoryGovernanceContract());
            case "CONVERSATION_ROLLUP" -> requireVersion(componentCode, contractVersion, "conversation-rollup-output-v1",
                    renderConversationRollupContract());
            case "RAG_ASSET_ANALYZER" -> requireVersion(componentCode, contractVersion, "rag-asset-analysis-output-v1",
                    renderRagAssetAnalysisContract());
            default -> "Return one JSON object that satisfies component contract version " + contractVersion + ".";
        };
    }

    public String renderSubAgentActionContract() {
        return """
                Required contract: SubAgentActionContract
                Required contract version: generic-sub-agent-action-v1

                Output exactly one valid JSON object.

                Required top-level fields:
                - action: one of CALL_TOOL, RETRIEVE_RAG, ASK_USER, CONTINUE, COMMIT, FAIL

                Forbidden actions:
                - FINAL
                - DELEGATE_AGENTS
                - DELEGATE_CODE_AGENT

                Common action rules:
                - Do not include markdown, prose, or hidden reasoning outside the JSON object.
                - Use only capabilities listed in effectiveCapabilities from the current full context.
                - If a needed capability is absent, output FAIL with a clear reason.

                Capability-to-action meaning:
                - COMMIT permits action=COMMIT and structured commit payloads to the parent.
                - RAG permits action=RETRIEVE_RAG.
                - MCP_TOOL permits action=CALL_TOOL for any AVAILABLE tool in availableMcpTools. Runtime still enforces the concrete tool's schema, scope, risk, and approval policy.
                - FILE_READ permits action=CALL_TOOL for granted read/discovery workspace file capabilities inside the effective workspace scope, including search_files, list_directory, directory_tree, read_file, and read_multiple_files when those tools are available.
                - FILE_WRITE permits action=CALL_TOOL for granted file write capabilities inside the effective workspace scope; Runtime policy and approval still apply.
                - ASK_USER permits action=ASK_USER through Runtime pending input.
                - Generic subagents normally receive MCP_TOOL by profile default. Its presence does not authorize bypassing a tool's configured user approval.

                Action-specific schema:
                - CALL_TOOL: actionInput must contain capabilityCode, toolName, goal, and arguments. Use capabilityCode="MCP_TOOL" with the exact mcpServerCode and toolName from an AVAILABLE availableMcpTools entry when the toolName is unique; when the same toolName appears more than once, use the concrete capabilityCode from that entry. Do not invent leaf capabilityCode values.
                - RETRIEVE_RAG: actionInput must contain query. Optional fields include knowledgeName, topK, reason, sourceHints, and filters.
                - ASK_USER: actionInput must contain askUserRequest with question and inputMode. FREE_TEXT requires allowFreeText=true and options=[]. SINGLE_CHOICE requires allowFreeText=false and non-empty options. SINGLE_CHOICE_OR_FREE_TEXT requires allowFreeText=true and non-empty options.
                - CONTINUE: actionInput must contain reason. Use only when the previous handler result requires another child loop.
                - COMMIT: commit is required. Do not use actionInput as the commit payload.
                - FAIL: actionInput.message or actionInput.reason is required.

                COMMIT payload schema:
                - taskId: required, must match the delegated task id.
                - status: required, one of SUCCESS, PARTIAL, BLOCKED, FAILED.
                - result: required. When requiredOutput asks for user-readable content, this field contains the complete required work product rather than a completion acknowledgement.
                - detail: a concise work note; required when the task used tools, RAG, files, code, or research evidence.
                - evidenceRefs: optional array of evidence ids or tool/RAG references.
                - inspectedResources: optional array of files, resources, URLs, or datasets inspected.
                - assumptions: optional array.
                - blockers: optional array.
                - suggestedParentNextStep: optional string.
                - safeForUserVisibleUse: optional boolean.
                Keep COMMIT JSON parseable. Multiline Markdown is allowed in result when it is the required work product, but it must remain a valid JSON string. Put method and caveats in concise detail, and use structured lists for evidenceRefs, inspectedResources, assumptions, and blockers. If a string needs a newline, escape it as \\n.

                Valid examples:
                {"action":"CALL_TOOL","actionInput":{"capabilityCode":"FILE_READ","toolName":"search_files","goal":"Discover SQL files under the delegated folder before reading them.","arguments":{"path":"E:/project/docs/dev-ops/pgvector","pattern":"**/*.sql"}}}
                {"action":"CALL_TOOL","actionInput":{"capabilityCode":"FILE_READ","toolName":"read_multiple_files","goal":"Read the discovered source files for this delegated task.","arguments":{"paths":["E:/project/a.java","E:/project/b.java"]}}}
                {"action":"RETRIEVE_RAG","actionInput":{"query":"Find the uploaded policy section relevant to the delegated question.","topK":3,"reason":"Need private evidence before committing."}}
                {"action":"ASK_USER","actionInput":{"askUserRequest":{"question":"Which folder should this delegated worker inspect?","inputMode":"FREE_TEXT","allowFreeText":true,"options":[]}}}
                {"action":"CONTINUE","actionInput":{"reason":"Tool evidence was added to full context; need one more loop to commit with details."}}
                {"action":"COMMIT","commit":{"taskId":"s1","status":"SUCCESS","result":"The requested files were inspected.","detail":"File A defines the aggregate root. File B defines repository ports.","evidenceRefs":["evidence-tool-1"],"inspectedResources":["E:/project/a.java","E:/project/b.java"],"assumptions":[],"blockers":[],"suggestedParentNextStep":"Use this result to update step s1 in the parent TaskLedger.","safeForUserVisibleUse":false}}
                {"action":"FAIL","actionInput":{"message":"The delegated task requires FILE_READ, but FILE_READ is not present in effectiveCapabilities."}}
                """;
    }

    public String renderMainAgentActionContractV2() {
        return renderMainAgentActionContractV2(null);
    }

    public String renderMainAgentActionContractV2(MainAgentStageEnumVO stage) {
        boolean delivering = stage == MainAgentStageEnumVO.DELIVERING;
        String actions = delivering
                ? "FINAL, FAIL"
                : "RETRIEVE_RAG, CALL_TOOL, DELEGATE_AGENTS, ASK_USER, READY_TO_DELIVER, FAIL";
        String actionPayloads = delivering
                ? """
                - FINAL: finalAnswerCandidate object with required content or contentRef; optional format belongs inside
                  finalAnswerCandidate at stateDelta.finalAnswerCandidate.format
                - FAIL: failure with a user-safe message
                """
                : """
                - RETRIEVE_RAG: ragRequest
                - CALL_TOOL: toolIntent
                - DELEGATE_AGENTS: delegateAgentsRequest
                - ASK_USER: askUserRequest
                - READY_TO_DELIVER: deliveryRequest with a concise reason
                - FAIL: failure with a user-safe message
                """;
        String executionRules = delivering
                ? ""
                : """
                CALL_TOOL toolIntent includes capabilityCode, toolName, goal, and arguments. Use an exposed capability.
                ASK_USER askUserRequest contains question, inputMode, allowFreeText, and options. inputMode is one of
                SINGLE_CHOICE, SINGLE_CHOICE_OR_FREE_TEXT, FREE_TEXT, or CONFIRM, with options and allowFreeText matched
                to the selected mode.
                DELEGATE_AGENTS uses waitMode=WAIT_ALL and a non-empty tasks array. Every tasks[i] object contains
                taskId, name, objective, requiredOutput, and a non-empty requestedCapabilities array. The only valid
                capability location is stateDelta.delegateAgentsRequest.tasks[i].requestedCapabilities. Use Runtime
                permission codes from the delegated capability list below, not task topics or worker labels. Include
                COMMIT in every task so the child can return its result to the parent.
                """;
        return """
                Required contract version: main-agent-action-v2
                Output exactly one valid JSON object.

                Required top-level fields:
                - taskUpdate: object
                - action: one of %s
                - stateDelta: object containing only the payload for the selected action

                taskUpdate fields:
                - goal: the stable current user goal when establishing or changing it
                - deliverableUpdates: items with deliverableId and optional description, acceptanceCriteria, status,
                  relatedStepIds, evidenceRefs, and payloadRefs
                - stepUpdates: items with stepId and optional description, status, dependsOn,
                  affectedDeliverableIds, and resultRefs
                - currentStepId, facts, blockers, lastDecision
                - planRevision: reason plus retainedStepIds, addedStepIds, and cancelledStepIds

                Step statuses: PENDING, IN_PROGRESS, COMPLETED, FAILED, BLOCKED, CANCELLED.
                Deliverable statuses: PENDING, IN_PROGRESS, READY, COMPLETED, BLOCKED, CANCELLED.

                stateDelta field by action:
                %s

                Delegated child capability codes:
                %s
                Every delegated task has a non-empty requestedCapabilities array and includes COMMIT. COMMIT is enough
                for a content-only worker. Generic subagents also receive MCP_TOOL from their Runtime profile, so a
                content-only task can use ["COMMIT"] without repeating MCP_TOOL. Add FILE_READ or FILE_WRITE for
                explicitly scoped workspace semantics, RAG for retrieval, or ASK_USER for a genuinely blocking user
                decision. Concrete MCP tools still enforce their own permission and approval policy.
                Never invent semantic labels such as content-writing, article-generation, or web-publishing.

                %s
                """.formatted(actions, actionPayloads.trim(), delegatedCapabilityCodes(), executionRules.trim());
    }

    private String delegatedCapabilityCodes() {
        AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
                .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);
        return profile.getMaximumCapabilityCodes().stream()
                .sorted(Comparator.naturalOrder())
                .map(code -> "- " + code)
                .collect(Collectors.joining("\n"));
    }

    public String renderFinalRepairContract() {
        return """
                Required contract version: final-repair-action-v1
                Output exactly one valid JSON object.

                Required top-level fields:
                - action: REPAIR_FINAL
                - stateDelta.finalAnswerCandidate: object

                finalAnswerCandidate must contain content or contentRef. Optional
                stateDelta.finalAnswerCandidate.format describes the content format.
                Preserve the supported meaning of the original answer while resolving the supplied guard failure.
                """;
    }

    public String renderContextPlannerOutputContract() {
        return """
                Required contract version: context-planner-output-v1

                Required top-level fields:
                - status: one of READY, NO_RELEVANT_CONTEXT, NEEDS_USER_CLARIFICATION, CONTEXT_OVER_BUDGET, or FAILED
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

                CONTEXT_OVER_BUDGET and FAILED are exceptional planner outcomes. Keep selectedContext as an array and
                include a concise reason describing the planner failure condition.

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
                Required contract version: verification-result-v1

                Required top-level fields:
                - status: PASSED, FAILED, or SKIPPED
                - failureCode: required field; null for PASSED or SKIPPED, non-empty string for FAILED
                - detail: required short diagnostic text for Runtime, not for final user display

                Valid examples:
                {"status":"PASSED","failureCode":null,"detail":"Answer is grounded in retrieved evidence."}
                {"status":"FAILED","failureCode":"RAG_UNGROUNDED","detail":"The answer asserts facts that do not appear in evidence."}
                """;
    }

    public String renderFinalResponseGuardResultContract() {
        return """
                Required contract version: final-response-guard-result-v1

                Required top-level fields:
                - status: PASSED or FAILED
                - finalContent: required clean user-facing content when PASSED
                - failureCode: required field; null when PASSED, non-empty string when FAILED
                - detail: required diagnostic text for Runtime
                """;
    }

    public String renderRepairContract(String originalComponentCode, String contractVersion) {
        return renderRepairContract(originalComponentCode, contractVersion, null);
    }

    public String renderRepairContract(String originalComponentCode,
                                       String contractVersion,
                                       MainAgentStageEnumVO stage) {
        if ("main-agent-action-v1".equals(contractVersion)) {
            throw new IllegalArgumentException("Unsupported MainAgent repair contract version: " + contractVersion);
        }
        if ("main-agent-action-v2".equals(contractVersion)) {
            return """
                    Repair the invalid output for the MainAgent v2 action contract.
                    Return the repaired JSON object without an explanation.

                    %s
                    """.formatted(renderMainAgentActionContractV2(stage));
        }
        if ("generic-sub-agent-action-v1".equals(contractVersion)) {
            return """
                    Repair the invalid output for the original GenericSubAgent action contract.
                    Required output is the same JSON object expected from GenericSubAgent.
                    Do not add repair explanations.

                    %s
                    """.formatted(renderSubAgentActionContract());
        }
        String originalContract = switch (contractVersion) {
            case "context-planner-output-v1" -> renderContextPlannerOutputContract();
            case "verification-result-v1" -> renderVerificationResultContract();
            case "final-response-guard-result-v1" -> renderFinalResponseGuardResultContract();
            case "final-repair-action-v1" -> renderFinalRepairContract();
            case "turn-summary-output-v1" -> renderTurnSummaryContract();
            case "memory-extraction-output-v1" -> renderMemoryExtractionContract();
            case "session-task-summary-output-v1" -> renderSessionTaskSummaryContract();
            case "memory-governance-output-v1" -> renderMemoryGovernanceContract();
            case "conversation-rollup-output-v1" -> renderConversationRollupContract();
            case "rag-asset-analysis-output-v1" -> renderRagAssetAnalysisContract();
            default -> throw new IllegalArgumentException("Unsupported repair contract version: " + contractVersion
                    + " for component " + originalComponentCode);
        };
        return """
                Repair the invalid output for component %s.
                Return the same JSON object shape required by the original contract without an explanation.

                %s
                """.formatted(originalComponentCode, originalContract);
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

    public String renderRagAssetAnalysisContract() {
        return """
                Required contract version: rag-asset-analysis-output-v1

                Required top-level fields:
                - title: concise title for the analyzed document or chunk
                - summary: concise factual summary
                - retrievalText: self-contained text optimized for later semantic retrieval
                - language: detected content language code or name
                - keySymbols: array of important code symbols, entities, or domain terms; use an empty array when absent

                Valid example:
                {"title":"Order aggregate","summary":"Defines order state and lifecycle rules.","retrievalText":"Order aggregate root, order lifecycle, state transitions, domain invariants.","language":"en","keySymbols":["Order","OrderStatus"]}
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

    private String requireVersion(String componentCode, String actual, String expected, String contract) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Unsupported " + componentCode + " contract version: " + actual
                    + "; expected " + expected);
        }
        return contract;
    }

}
