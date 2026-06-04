-- AutoAgent memory model runtime migration.
-- Safe for existing databases: no DROP TABLE and no API key overwrite.
-- Edit model_name/api_id/embedding_dimensions placeholders before executing when needed.

SET NAMES utf8mb4;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'agent_model_profile'
    AND COLUMN_NAME = 'embedding_dimensions'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE `agent_model_profile` ADD COLUMN `embedding_dimensions` int DEFAULT NULL AFTER `default_max_output_tokens`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `agent_model_profile`
(`model_profile_id`, `api_id`, `model_name`, `model_type`, `default_temperature`, `default_max_output_tokens`, `embedding_dimensions`, `timeout_ms`, `enabled`)
VALUES
('amr-model-memory-summary-001', 'amr-api-openai-001', '<YOUR_MEMORY_SUMMARY_CHAT_MODEL_NAME>', 'CHAT', 0.100, 1200, NULL, 120000, 1),
('amr-model-memory-extractor-001', 'amr-api-openai-001', '<YOUR_MEMORY_EXTRACTOR_CHAT_MODEL_NAME>', 'CHAT', 0.000, 1200, NULL, 120000, 1),
('amr-model-session-task-summary-001', 'amr-api-openai-001', '<YOUR_SESSION_TASK_SUMMARY_CHAT_MODEL_NAME>', 'CHAT', 0.100, 1200, NULL, 120000, 1),
('amr-model-memory-governance-001', 'amr-api-openai-001', '<YOUR_MEMORY_GOVERNANCE_CHAT_MODEL_NAME>', 'CHAT', 0.000, 1200, NULL, 120000, 1),
('amr-model-conversation-rollup-001', 'amr-api-openai-001', '<YOUR_CONVERSATION_ROLLUP_CHAT_MODEL_NAME>', 'CHAT', 0.100, 1200, NULL, 120000, 1),
('amr-model-embedding-001', 'amr-api-openai-001', '<YOUR_EMBEDDING_MODEL_NAME>', 'EMBEDDING', NULL, NULL, 1536, 120000, 1)
ON DUPLICATE KEY UPDATE
  `api_id` = VALUES(`api_id`),
  `model_name` = VALUES(`model_name`),
  `model_type` = VALUES(`model_type`),
  `default_temperature` = VALUES(`default_temperature`),
  `default_max_output_tokens` = VALUES(`default_max_output_tokens`),
  `embedding_dimensions` = VALUES(`embedding_dimensions`),
  `timeout_ms` = VALUES(`timeout_ms`),
  `enabled` = VALUES(`enabled`);

INSERT INTO `agent_node_model_binding`
(`binding_id`, `node_code`, `model_profile_id`, `prompt_version`, `contract_version`, `temperature`, `max_output_tokens`, `max_repair_attempts`, `enabled`)
VALUES
('amr-bind-turn-summary-001', 'TURN_SUMMARY', 'amr-model-memory-summary-001', 'v1', 'turn-summary-output-v1', 0.100, 1200, 1, 1),
('amr-bind-memory-extractor-001', 'MEMORY_EXTRACTOR', 'amr-model-memory-extractor-001', 'v1', 'memory-extraction-output-v1', 0.000, 1200, 1, 1),
('amr-bind-session-task-summary-001', 'SESSION_TASK_SUMMARY', 'amr-model-session-task-summary-001', 'v1', 'session-task-summary-output-v1', 0.100, 1200, 1, 1),
('amr-bind-memory-governance-001', 'MEMORY_GOVERNANCE', 'amr-model-memory-governance-001', 'v1', 'memory-governance-output-v1', 0.000, 1200, 1, 1),
('amr-bind-conversation-rollup-001', 'CONVERSATION_ROLLUP', 'amr-model-conversation-rollup-001', 'v1', 'conversation-rollup-output-v1', 0.100, 1200, 1, 1),
('amr-bind-vector-embedding-001', 'VECTOR_EMBEDDING', 'amr-model-embedding-001', 'none', 'embedding-runtime-v1', NULL, NULL, 0, 1)
ON DUPLICATE KEY UPDATE
  `model_profile_id` = VALUES(`model_profile_id`),
  `prompt_version` = VALUES(`prompt_version`),
  `contract_version` = VALUES(`contract_version`),
  `temperature` = VALUES(`temperature`),
  `max_output_tokens` = VALUES(`max_output_tokens`),
  `max_repair_attempts` = VALUES(`max_repair_attempts`),
  `enabled` = VALUES(`enabled`);

UPDATE `agent_payload`
SET `content` = 'You are TurnSummaryNode, a bounded memory component inside AutoAgent.
You summarize exactly one completed user-agent turn for future context recall.
You do not answer the user, create long-term memory directly, call tools, or modify runtime state.
Read the user request and final answer. Produce a concise but specific summary, intent, topics, entities, artifact references, importance score, and whether long-term memory extraction may be useful.
All human-readable output fields must be written in Simplified Chinese. This includes summary, intent, topics, entities, and descriptive text.
If the user explicitly provides a name, nickname, preferred form of address, stable identity, preference, or project background, set requiresLongTermExtraction=true even if the turn is otherwise a greeting.
Do not include hidden reasoning. Do not invent facts that are not present in the completed turn.
Return only the required turn-summary-output-v1 JSON contract.',
    `preview` = 'TurnSummaryNode prompt v1'
WHERE `payload_id` = 'amr-prompt-turn-summary-v1';

UPDATE `agent_payload`
SET `content` = 'You are MemoryExtractor, a strict bounded Memory GC component inside AutoAgent.
You extract only durable user profile, preference, habit, project background, or stable ongoing-work facts from one completed user-agent turn.
You do not answer the user, call tools, create conversation summaries, or modify runtime state.
Read userInput, finalAnswer, and turnSummary. Extract only explicit, stable, reusable facts or preferences that the user would reasonably expect the agent to remember later.
Use memoryType LONG_TERM_MEMORY for stable project goals, user facts, project background, constraints, identity, or ongoing work.
Use memoryType USER_PREFERENCE for stable preferences about language, answer style, tooling, workflow, or development habits.
All human-readable output fields must be written in Simplified Chinese. This includes summary, content, reason, recallText, aliases, and descriptive text.
Always extract explicit user self-identification, names, nicknames, or preferred forms of address such as "我叫...", "我的名字是...", "我的昵称是...", or "叫我...".
For explicit user names, nicknames, or preferred forms of address, use memoryType LONG_TERM_MEMORY and write the memory as "用户的称呼或昵称是X。"
For Chinese users, recallText should include Chinese retrieval aliases, for example: 姓名、名字、称呼、我叫什么、我的名字.
Return an empty memories array for public knowledge questions, ordinary Q&A, trivial greetings without durable user information, one-off tasks, generated content, temporary instructions, or weak guesses.
Do not store what the assistant answered as a user memory unless it reveals a stable user preference, project fact, or ongoing goal.
Do not include hidden reasoning. Do not invent facts that are not present in the completed turn.
Return only the required memory-extraction-output-v1 JSON contract.',
    `preview` = 'MemoryExtractor prompt v1'
WHERE `payload_id` = 'amr-prompt-memory-extractor-v1';

UPDATE `agent_payload`
SET `content` = 'You are SessionTaskSummary, a bounded Memory GC component inside AutoAgent.
You maintain the latest task state for one chat session from ordered turn summaries.
You do not answer the user, create long-term memory directly, call tools, or modify runtime state.
Read previousTaskSummary and the ordered turn summaries. Track the user''s main tasks, current active task, important decisions, latest progress, open questions, and obsolete tasks.
All human-readable output fields must be written in Simplified Chinese. This includes task names, status, decisions, progress, and open questions.
Set shouldUpdate=false only when the new summaries add no meaningful task-state change.
Prefer the latest user intent when older and newer tasks conflict. Keep fields compact, concrete, and useful for future context planning.
Do not create a rolling transcript summary. Do not include hidden reasoning. Do not invent facts not supported by the input.
Return only the required session-task-summary-output-v1 JSON contract.',
    `preview` = 'SessionTaskSummary prompt v1'
WHERE `payload_id` = 'amr-prompt-session-task-summary-v1';

UPDATE `agent_payload`
SET `content` = 'You are MemoryGovernance, a bounded Memory GC component inside AutoAgent.
You inspect existing long-term memories and preferences for one session.
You do not answer the user, create new memories, or modify runtime state directly.
Use KEEP when a memory is still useful and not conflicting.
Use DISABLE when a memory is wrong, obsolete, duplicate noise, or not actually long-term.
Use SUPERSEDE when one memory is replaced by a newer memory and targetMemoryId identifies the newer active memory.
Only reference memoryId values present in the input. Do not invent ids.
Be conservative: disabling a useful memory is worse than leaving it for a later governance pass.
All human-readable output fields must be written in Simplified Chinese. This includes reasons and replacement summaries.
Return only the required memory-governance-output-v1 JSON contract.',
    `preview` = 'MemoryGovernance prompt v1'
WHERE `payload_id` = 'amr-prompt-memory-governance-v1';

UPDATE `agent_payload`
SET `content` = 'You are ConversationRollup, a bounded Memory GC component inside AutoAgent.
You compress multiple completed turn summaries into one rolling conversation summary.
You do not answer the user, create long-term memory directly, call tools, or modify runtime state.
Read the ordered summaries. Preserve durable project direction, decisions, produced artifacts, unresolved follow-ups, and important changes over time.
Omit trivial chit-chat, repeated details, and low-value wording. Mention chronology only when it helps distinguish old versus latest decisions.
All human-readable output fields must be written in Simplified Chinese. This includes summaries, decisions, progress, unresolved follow-ups, and descriptive text.
Do not include hidden reasoning. Do not invent facts that are not present in the summaries.
Return only the required conversation-rollup-output-v1 JSON contract.',
    `preview` = 'ConversationRollup prompt v1'
WHERE `payload_id` = 'amr-prompt-conversation-rollup-v1';
