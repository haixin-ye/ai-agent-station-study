-- Raise MainAgent output budget for long FINAL action JSON.
-- Safe for existing databases: updates only active MainAgent model/runtime rows.

SET NAMES utf8mb4;

UPDATE `agent_model_profile`
SET `default_max_output_tokens` = 8192,
    `updated_at` = CURRENT_TIMESTAMP
WHERE `model_profile_id` = 'amr-model-main-001'
  AND (`default_max_output_tokens` IS NULL OR `default_max_output_tokens` < 8192);

UPDATE `agent_node_model_binding`
SET `max_output_tokens` = 8192,
    `updated_at` = CURRENT_TIMESTAMP
WHERE `node_code` = 'MAIN_AGENT'
  AND `enabled` = 1
  AND (`max_output_tokens` IS NULL OR `max_output_tokens` < 8192);
