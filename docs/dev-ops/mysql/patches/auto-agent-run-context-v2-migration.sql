-- Canonical per-run context and MainAgent v2 migration.

CREATE TABLE IF NOT EXISTS `agent_run_context` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) NOT NULL,
  `schema_version` int NOT NULL,
  `main_agent_stage` varchar(32) NOT NULL,
  `base_context_ref` varchar(64) NOT NULL,
  `task_ledger_ref` varchar(64) NOT NULL,
  `runtime_control_ref` varchar(64) NOT NULL,
  `context_version` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_context_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical AutoAgent run context';

CREATE TABLE IF NOT EXISTS `agent_run_loop` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) NOT NULL,
  `loop_index` int NOT NULL,
  `main_agent_stage` varchar(32) NOT NULL,
  `status` varchar(64) NOT NULL,
  `record_ref` varchar(64) NOT NULL,
  `record_version` bigint NOT NULL,
  `started_at` datetime NOT NULL,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_loop_index` (`run_id`, `loop_index`),
  KEY `idx_agent_run_loop_status` (`run_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical causal record index for each AutoAgent loop';

INSERT INTO `agent_payload`
(`payload_id`, `payload_type`, `storage_type`, `content`, `preview`, `compressed`, `encrypted`)
VALUES
('amr-prompt-main-agent-v2', 'PROMPT_CONTENT', 'DB',
'You are AutoAgent''s MainAgent, the decision-making component responsible for understanding and advancing the current user task.

You operate inside a Java Runtime task loop. On each call, you receive the original user request together with the current facts from this run, and return exactly one structured action. Runtime executes external operations, persists state, and returns actual outcomes in later calls.

Your overall goal is to complete every user-requested deliverable. Judge each action by how it contributes to that complete result. The current stage defines your immediate responsibility: PLANNING understands the request and chooses the first step; EXECUTING reconciles results and chooses the next step; DELIVERING composes the final user-facing response.',
'MainAgent role prompt v2', 0, 0)
ON DUPLICATE KEY UPDATE
  `content` = VALUES(`content`),
  `preview` = VALUES(`preview`);

INSERT INTO `agent_node_prompt`
(`prompt_id`, `agent_id`, `node_code`, `prompt_version`, `content_ref`, `enabled`)
VALUES
('amr-node-prompt-main-agent-v2', 'GLOBAL', 'MAIN_AGENT', 'v2', 'amr-prompt-main-agent-v2', 1)
ON DUPLICATE KEY UPDATE
  `content_ref` = VALUES(`content_ref`),
  `enabled` = VALUES(`enabled`);

UPDATE `agent_node_prompt`
SET `enabled` = 0
WHERE `node_code` = 'MAIN_AGENT' AND `prompt_version` <> 'v2';

INSERT INTO `agent_node_model_binding`
(`binding_id`, `node_code`, `model_profile_id`, `prompt_version`, `contract_version`, `temperature`, `max_output_tokens`, `max_repair_attempts`, `enabled`)
VALUES
('amr-bind-main-agent-001', 'MAIN_AGENT', 'amr-model-main-001', 'v2', 'main-agent-action-v2', 0.200, 8192, 1, 1),
('amr-bind-final-repair-001', 'FINAL_REPAIR', 'amr-model-repair-001', 'v1', 'final-repair-action-v1', 0.100, 1200, 1, 1)
ON DUPLICATE KEY UPDATE
  `prompt_version` = VALUES(`prompt_version`),
  `contract_version` = VALUES(`contract_version`),
  `temperature` = VALUES(`temperature`),
  `max_output_tokens` = VALUES(`max_output_tokens`),
  `max_repair_attempts` = VALUES(`max_repair_attempts`),
  `enabled` = VALUES(`enabled`);
