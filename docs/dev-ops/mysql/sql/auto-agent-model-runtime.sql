-- AutoAgent model runtime schema and MVP seed data.
-- Execute after auto-agent-main-loop-harness.sql.
-- Replace placeholder base_url, api_key, and model_name values before using real LLM calls.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `agent_node_model_binding`;
DROP TABLE IF EXISTS `agent_model_profile`;
DROP TABLE IF EXISTS `agent_model_api`;

SET FOREIGN_KEY_CHECKS = 1;
CREATE TABLE IF NOT EXISTS `agent_model_api` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `api_id` varchar(64) NOT NULL,
  `provider` varchar(64) NOT NULL,
  `base_url` varchar(512) NOT NULL,
  `api_key` varchar(1024) NOT NULL,
  `completions_path` varchar(256) NOT NULL DEFAULT '/v1/chat/completions',
  `embeddings_path` varchar(256) NOT NULL DEFAULT '/v1/embeddings',
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_model_api_id` (`api_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent clean model API endpoint';

CREATE TABLE IF NOT EXISTS `agent_model_profile` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `model_profile_id` varchar(64) NOT NULL,
  `api_id` varchar(64) NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `model_type` varchar(64) NOT NULL DEFAULT 'CHAT',
  `default_temperature` decimal(4,3) DEFAULT NULL,
  `default_max_output_tokens` int DEFAULT NULL,
  `timeout_ms` int DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_model_profile_id` (`model_profile_id`),
  KEY `idx_agent_model_profile_api` (`api_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent model profile';

CREATE TABLE IF NOT EXISTS `agent_node_model_binding` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `binding_id` varchar(64) NOT NULL,
  `node_code` varchar(64) NOT NULL,
  `model_profile_id` varchar(64) NOT NULL,
  `prompt_version` varchar(64) NOT NULL DEFAULT 'v1',
  `contract_version` varchar(64) NOT NULL,
  `temperature` decimal(4,3) DEFAULT NULL,
  `max_output_tokens` int DEFAULT NULL,
  `max_repair_attempts` int NOT NULL DEFAULT 1,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_node_model_binding_id` (`binding_id`),
  UNIQUE KEY `uk_agent_node_model_binding_node` (`node_code`, `enabled`),
  KEY `idx_agent_node_model_binding_profile` (`model_profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent node to clean model binding';

INSERT INTO `agent_model_api`
(`api_id`, `provider`, `base_url`, `api_key`, `completions_path`, `embeddings_path`, `enabled`)
VALUES
('amr-api-openai-001', 'OPENAI_COMPATIBLE', '<YOUR_OPENAI_COMPATIBLE_BASE_URL>', '<YOUR_API_KEY>', '/v1/chat/completions', '/v1/embeddings', 1)
ON DUPLICATE KEY UPDATE
  `provider` = VALUES(`provider`),
  `base_url` = VALUES(`base_url`),
  `api_key` = VALUES(`api_key`),
  `completions_path` = VALUES(`completions_path`),
  `embeddings_path` = VALUES(`embeddings_path`),
  `enabled` = VALUES(`enabled`);

INSERT INTO `agent_model_profile`
(`model_profile_id`, `api_id`, `model_name`, `model_type`, `default_temperature`, `default_max_output_tokens`, `timeout_ms`, `enabled`)
VALUES
('amr-model-main-001', 'amr-api-openai-001', '<YOUR_CHAT_MODEL_NAME>', 'CHAT', 0.200, 2400, 120000, 1),
('amr-model-verify-001', 'amr-api-openai-001', '<YOUR_CHAT_MODEL_NAME>', 'CHAT', 0.000, 1200, 120000, 1),
('amr-model-repair-001', 'amr-api-openai-001', '<YOUR_CHAT_MODEL_NAME>', 'CHAT', 0.100, 1200, 120000, 1)
ON DUPLICATE KEY UPDATE
  `api_id` = VALUES(`api_id`),
  `model_name` = VALUES(`model_name`),
  `model_type` = VALUES(`model_type`),
  `default_temperature` = VALUES(`default_temperature`),
  `default_max_output_tokens` = VALUES(`default_max_output_tokens`),
  `timeout_ms` = VALUES(`timeout_ms`),
  `enabled` = VALUES(`enabled`);

INSERT INTO `agent_node_model_binding`
(`binding_id`, `node_code`, `model_profile_id`, `prompt_version`, `contract_version`, `temperature`, `max_output_tokens`, `max_repair_attempts`, `enabled`)
VALUES
('amr-bind-context-planner-001', 'CONTEXT_PLANNER', 'amr-model-main-001', 'v1', 'context-planner-output-v1', 0.100, 1600, 1, 1),
('amr-bind-main-agent-001', 'MAIN_AGENT', 'amr-model-main-001', 'v1', 'main-agent-action-v1', 0.200, 2400, 1, 1),
('amr-bind-rag-verifier-001', 'RAG_VERIFIER', 'amr-model-verify-001', 'v1', 'verification-result-v1', 0.000, 1200, 1, 1),
('amr-bind-final-repair-001', 'FINAL_REPAIR', 'amr-model-repair-001', 'v1', 'main-agent-action-v1', 0.100, 1200, 1, 1),
('amr-bind-contract-repair-001', 'CONTRACT_REPAIR', 'amr-model-repair-001', 'v1', 'contract-repair-v1', 0.000, 1200, 1, 1)
ON DUPLICATE KEY UPDATE
  `model_profile_id` = VALUES(`model_profile_id`),
  `prompt_version` = VALUES(`prompt_version`),
  `contract_version` = VALUES(`contract_version`),
  `temperature` = VALUES(`temperature`),
  `max_output_tokens` = VALUES(`max_output_tokens`),
  `max_repair_attempts` = VALUES(`max_repair_attempts`),
  `enabled` = VALUES(`enabled`);

INSERT INTO `agent_payload`
(`payload_id`, `payload_type`, `storage_type`, `content`, `preview`, `compressed`, `encrypted`)
VALUES
('amr-prompt-context-planner-v1', 'PROMPT_CONTENT', 'DB',
'You are ContextPlannerNode, a bounded context planning component inside AutoAgent.
Your only job is to decide which candidate context references should be loaded for the next MainAgentNode call.
You do not answer the user, call tools, create artifacts, or control run lifecycle.
Read the user request, recent conversation summary, candidate artifacts, candidate memories, candidate evidence, pending action, and token budget.
Select only context that is necessary for the next semantic decision. Prefer references and summaries unless the request requires full artifact content.
If the user reference is ambiguous and cannot be resolved from candidates, output ASK_USER_REQUIRED with clear single-choice options or free text enabled when appropriate.
Return only the required JSON contract. Do not include markdown, explanations, trace, node names, or hidden reasoning outside JSON.',
'ContextPlannerNode prompt v1', 0, 0),
('amr-prompt-main-agent-v1', 'PROMPT_CONTENT', 'DB',
'You are MainAgentNode, the main semantic decision and generation component inside AutoAgent.
For each loop iteration, read MainAgentStateView and output exactly one next action JSON.
You do not directly call tools. If a tool is needed, output CALL_TOOL with intent and structured arguments.
You do not directly query RAG. If private or configured knowledge-base retrieval is needed, output RETRIEVE_RAG with query requests.
Use FINAL directly for public knowledge questions, concept explanations, protocol introductions, summaries, tutorials, interview notes, and examples that can be answered from general model knowledge.
Do not use RETRIEVE_RAG just because the user asks for "knowledge points", "summary", "details", or an article about a public technology such as MCP, RAG, Java, Spring, SQL, or HTTP.
Use RETRIEVE_RAG only when the user explicitly asks to use a knowledge base, uploaded document, project document, private material, company/internal data, citation-backed retrieval, or missing evidence not already present in MainAgentStateView.
MainAgentStateView may include userClarifications. These are authoritative answers to previous ASK_USER requests in this same run. If a clarification answers your previous question, use it and continue; do not ask the same question again.
You do not access databases, write trace records, update lifecycle status, or mention internal harness details to the user.
When enough information is available, output FINAL with user-facing content only. Follow this global answer style: default to substantial, structured, practical answers rather than a short generic paragraph. For explanations, comparisons, summaries, plans, tutorials, troubleshooting, designs, interview answers, knowledge notes, or analysis, use clear sections or bullet points by default. Each point must carry real information: meaning, reason, mechanism, trade-off, example, boundary, risk, or practical use. Start by answering the user''s core request directly, then add supporting details. Match explicit length constraints; if the user requests about 200 Chinese characters, stay compact but still keep useful structure when possible. Expand noticeably when the user asks for detail, completeness, examples, steps, or "具体一些". Very short answers are allowed only for greetings, trivial facts, or explicit brevity requests. Final answers must not mention agent nodes, validation, trace, contracts, JSON, or internal workflow.
When creating or updating long content, use CREATE_ARTIFACT or UPDATE_ARTIFACT and include concise user-facing content.
Use ASK_USER only when the missing information blocks safe completion, when multiple existing artifacts or targets are truly indistinguishable, or when explicit approval is required. Before asking the user, inspect userClarifications. If the needed answer is already present there, continue with that answer instead of asking again. Do not ask for clarification if a reasonable assumption can be stated and the answer can proceed safely. For pronouns and follow-up wording, use conversation memory and selected context first; ask only when no antecedent can be resolved. When user choice is required, output ASK_USER with question, inputMode, and options that map cleanly to the next step. Prefer SINGLE_CHOICE for approval or bounded choices, and SINGLE_CHOICE_OR_FREE_TEXT when the user may either choose an option or type a clarification.
Return only the required JSON contract. Do not include markdown fences, extra prose, or hidden reasoning outside JSON.',
'MainAgentNode prompt v1', 0, 0),
('amr-prompt-rag-verifier-v1', 'PROMPT_CONTENT', 'DB',
'You are RagVerifier, a bounded verification component.
You receive the user request, final answer candidate, RAG queries, and retrieved evidence snippets.
Your job is to determine whether the answer misuses retrieved evidence.
Fail only when the answer claims unsupported facts from RAG, contradicts retrieved evidence, fabricates citations or document facts, or ignores required retrieved evidence for a RAG-dependent answer.
Pass when RAG was retrieved but the final answer legitimately does not need to cite or use it, or when the answer is grounded enough for the user request.
Return only the verification-result JSON contract with status, failureCode, detail, and confidence. Do not rewrite the answer unless the contract asks for a repair hint.',
'RagVerifier prompt v1', 0, 0),
('amr-prompt-final-repair-v1', 'PROMPT_CONTENT', 'DB',
'You are FinalRepairNode, a bounded final-answer repair component.
You receive a failed final answer candidate and guard feedback.
Rewrite only the user-facing final content while preserving the original user intent and useful answer substance.
Remove internal harness details, node names, trace details, validation details, JSON mentions, and repair-process explanations.
Return a valid FINAL action JSON according to main-agent-action-v1. Do not include markdown fences or extra prose outside JSON.',
'FinalRepair prompt v1', 0, 0),
('amr-prompt-contract-repair-v1', 'PROMPT_CONTENT', 'DB',
'You are ContractRepairNode, a bounded structured-output repair component.
You receive invalid raw output, contract information, and validation failures.
Repair only JSON syntax, missing required fields, forbidden fields, invalid enum values, or stateDelta shape violations.
Do not change the task intent, invent new facts, call tools, ask the user, or add explanations.
Return only one JSON object that satisfies the requested contract. Do not include markdown fences or prose outside JSON.',
'ContractRepair prompt v1', 0, 0)
ON DUPLICATE KEY UPDATE
  `payload_type` = VALUES(`payload_type`),
  `storage_type` = VALUES(`storage_type`),
  `content` = VALUES(`content`),
  `preview` = VALUES(`preview`),
  `compressed` = VALUES(`compressed`),
  `encrypted` = VALUES(`encrypted`);

INSERT INTO `agent_node_prompt`
(`prompt_id`, `agent_id`, `node_code`, `prompt_version`, `content_ref`, `enabled`)
VALUES
('amr-node-prompt-context-planner-v1', 'GLOBAL', 'CONTEXT_PLANNER', 'v1', 'amr-prompt-context-planner-v1', 1),
('amr-node-prompt-main-agent-v1', 'GLOBAL', 'MAIN_AGENT', 'v1', 'amr-prompt-main-agent-v1', 1),
('amr-node-prompt-rag-verifier-v1', 'GLOBAL', 'RAG_VERIFIER', 'v1', 'amr-prompt-rag-verifier-v1', 1),
('amr-node-prompt-final-repair-v1', 'GLOBAL', 'FINAL_REPAIR', 'v1', 'amr-prompt-final-repair-v1', 1),
('amr-node-prompt-contract-repair-v1', 'GLOBAL', 'CONTRACT_REPAIR', 'v1', 'amr-prompt-contract-repair-v1', 1)
ON DUPLICATE KEY UPDATE
  `agent_id` = VALUES(`agent_id`),
  `node_code` = VALUES(`node_code`),
  `prompt_version` = VALUES(`prompt_version`),
  `content_ref` = VALUES(`content_ref`),
  `enabled` = VALUES(`enabled`);
