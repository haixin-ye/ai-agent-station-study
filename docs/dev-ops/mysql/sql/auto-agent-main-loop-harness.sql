`-- AutoAgent main-loop harness runtime persistence schema.
-- Execute this file after the base database has been created.
-- Historical ai-agent-station-study.sql remains untouched because the new harness uses a new table set.

CREATE TABLE IF NOT EXISTS `agent_session` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) NOT NULL,
  `user_id` varchar(64) DEFAULT NULL,
  `title` varchar(512) DEFAULT NULL,
  `status` varchar(64) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_session_id` (`session_id`),
  KEY `idx_agent_session_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent session';

CREATE TABLE IF NOT EXISTS `agent_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `message_id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `run_id` varchar(64) DEFAULT NULL,
  `role` varchar(64) NOT NULL,
  `content_ref` varchar(64) NOT NULL,
  `metadata_ref` varchar(64) DEFAULT NULL,
  `visible_to_user` tinyint(1) NOT NULL DEFAULT 1,
  `seq` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_message_id` (`message_id`),
  KEY `idx_agent_message_session_created` (`session_id`, `created_at`),
  KEY `idx_agent_message_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent conversation message';

CREATE TABLE IF NOT EXISTS `agent_run` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `user_id` varchar(64) DEFAULT NULL,
  `agent_id` varchar(64) DEFAULT NULL,
  `status` varchar(64) NOT NULL,
  `phase` varchar(64) NOT NULL,
  `rag_was_used` tinyint(1) NOT NULL DEFAULT 0,
  `final_message_id` varchar(64) DEFAULT NULL,
  `final_answer_ref` varchar(64) DEFAULT NULL,
  `failure_code` varchar(128) DEFAULT NULL,
  `failure_message` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_id` (`run_id`),
  KEY `idx_agent_run_session_created` (`session_id`, `created_at`),
  KEY `idx_agent_run_status_phase` (`status`, `phase`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent run lifecycle';

CREATE TABLE IF NOT EXISTS `agent_run_state_snapshot` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `snapshot_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `phase` varchar(64) NOT NULL,
  `state_ref` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_state_snapshot_id` (`snapshot_id`),
  KEY `idx_agent_run_state_snapshot_run` (`run_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent run state snapshot';

CREATE TABLE IF NOT EXISTS `agent_run_transcript` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `block_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `seq` bigint NOT NULL,
  `block_type` varchar(64) NOT NULL,
  `payload_ref` varchar(64) NOT NULL,
  `compactable` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_transcript_block` (`block_id`),
  UNIQUE KEY `uk_agent_run_transcript_run_seq` (`run_id`, `seq`),
  KEY `idx_agent_run_transcript_type_seq` (`run_id`, `block_type`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent typed run transcript';

CREATE TABLE IF NOT EXISTS `agent_conversation_summary` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `summary_id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `user_id` varchar(64) DEFAULT NULL,
  `summary_ref` varchar(64) NOT NULL,
  `message_start_seq` bigint DEFAULT NULL,
  `message_end_seq` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_conversation_summary_id` (`summary_id`),
  KEY `idx_agent_conversation_summary_session` (`session_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent conversation summary';

CREATE TABLE IF NOT EXISTS `agent_long_term_memory` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `memory_id` varchar(64) NOT NULL,
  `user_id` varchar(64) DEFAULT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  `memory_type` varchar(64) NOT NULL,
  `summary` varchar(512) DEFAULT NULL,
  `content_ref` varchar(64) NOT NULL,
  `score` decimal(8,4) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_long_term_memory_id` (`memory_id`),
  KEY `idx_agent_long_term_memory_user` (`user_id`, `memory_type`),
  KEY `idx_agent_long_term_memory_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent long-term memory';

CREATE TABLE IF NOT EXISTS `agent_memory_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `run_id` varchar(64) DEFAULT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  `memory_id` varchar(64) DEFAULT NULL,
  `event_type` varchar(64) NOT NULL,
  `payload_ref` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_memory_event_id` (`event_id`),
  KEY `idx_agent_memory_event_run` (`run_id`),
  KEY `idx_agent_memory_event_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent memory event';

CREATE TABLE IF NOT EXISTS `agent_payload` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `payload_id` varchar(64) NOT NULL,
  `payload_type` varchar(64) NOT NULL,
  `storage_type` varchar(64) NOT NULL DEFAULT 'DB',
  `content` longtext,
  `content_path` varchar(1024) DEFAULT NULL,
  `content_sha256` varchar(128) DEFAULT NULL,
  `preview` varchar(512) DEFAULT NULL,
  `compressed` tinyint(1) NOT NULL DEFAULT 0,
  `encrypted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_payload_id` (`payload_id`),
  KEY `idx_agent_payload_type` (`payload_id`, `payload_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent payload storage';

CREATE TABLE IF NOT EXISTS `agent_artifact` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `artifact_id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `run_id` varchar(64) DEFAULT NULL,
  `artifact_type` varchar(64) NOT NULL,
  `title` varchar(512) DEFAULT NULL,
  `summary` varchar(512) DEFAULT NULL,
  `content_ref` varchar(64) NOT NULL,
  `version` int NOT NULL DEFAULT 1,
  `last_mentioned_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_artifact_id` (`artifact_id`),
  KEY `idx_agent_artifact_session_updated` (`session_id`, `updated_at`),
  KEY `idx_agent_artifact_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent artifact';

CREATE TABLE IF NOT EXISTS `agent_artifact_alias` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `alias_id` varchar(64) NOT NULL,
  `artifact_id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `alias_text` varchar(512) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_artifact_alias_id` (`alias_id`),
  KEY `idx_agent_artifact_alias_session` (`session_id`, `alias_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent artifact alias';

CREATE TABLE IF NOT EXISTS `agent_artifact_relation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `relation_id` varchar(64) NOT NULL,
  `source_artifact_id` varchar(64) NOT NULL,
  `target_artifact_id` varchar(64) NOT NULL,
  `relation_type` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_artifact_relation_id` (`relation_id`),
  KEY `idx_agent_artifact_relation_source` (`source_artifact_id`),
  KEY `idx_agent_artifact_relation_target` (`target_artifact_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent artifact relation';

CREATE TABLE IF NOT EXISTS `agent_evidence` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `evidence_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `evidence_type` varchar(64) NOT NULL,
  `source_ref` varchar(64) DEFAULT NULL,
  `summary` varchar(512) DEFAULT NULL,
  `confidence` decimal(10,6) DEFAULT NULL,
  `used_by_final` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_evidence_id` (`evidence_id`),
  KEY `idx_agent_evidence_run_type` (`run_id`, `evidence_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent evidence';

CREATE TABLE IF NOT EXISTS `agent_pending_input` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `pending_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `source_component` varchar(64) NOT NULL,
  `pending_type` varchar(64) NOT NULL,
  `input_mode` varchar(64) NOT NULL,
  `status` varchar(64) NOT NULL,
  `question` varchar(512) NOT NULL,
  `options_ref` varchar(64) DEFAULT NULL,
  `answer_schema_ref` varchar(64) DEFAULT NULL,
  `continuation_ref` varchar(64) DEFAULT NULL,
  `user_answer_ref` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `answered_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_pending_input_id` (`pending_id`),
  KEY `idx_agent_pending_input_run_status` (`run_id`, `status`),
  KEY `idx_agent_pending_input_pending_status` (`pending_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent pending user input';

CREATE TABLE IF NOT EXISTS `agent_tool_call` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tool_call_id` varchar(64) NOT NULL,
  `tool_invocation_id` varchar(64) DEFAULT NULL,
  `run_id` varchar(64) NOT NULL,
  `tool_name` varchar(128) NOT NULL,
  `mcp_server_name` varchar(128) DEFAULT NULL,
  `mcp_transport_type` varchar(64) DEFAULT NULL,
  `status` varchar(64) NOT NULL,
  `input_schema_ref` varchar(64) DEFAULT NULL,
  `intent_ref` varchar(64) DEFAULT NULL,
  `arguments_ref` varchar(64) DEFAULT NULL,
  `receipt_ref` varchar(64) DEFAULT NULL,
  `failure_code` varchar(128) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_tool_call_id` (`tool_call_id`),
  KEY `idx_agent_tool_call_run_status` (`run_id`, `status`),
  KEY `idx_agent_tool_call_invocation` (`tool_invocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent tool call';

CREATE TABLE IF NOT EXISTS `agent_tool_approval` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `approval_id` varchar(64) NOT NULL,
  `approval_key` varchar(128) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `tool_call_id` varchar(64) DEFAULT NULL,
  `status` varchar(64) NOT NULL,
  `permission_mode` varchar(64) NOT NULL,
  `arguments_hash` varchar(128) DEFAULT NULL,
  `options_ref` varchar(64) DEFAULT NULL,
  `user_answer_ref` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `decided_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_tool_approval_id` (`approval_id`),
  KEY `idx_agent_tool_approval_key_status` (`approval_key`, `status`),
  KEY `idx_agent_tool_approval_run_status` (`run_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent tool approval';

CREATE TABLE IF NOT EXISTS `agent_tool_verification` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `verification_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `tool_call_id` varchar(64) NOT NULL,
  `status` varchar(64) NOT NULL,
  `failure_code` varchar(128) DEFAULT NULL,
  `detail_ref` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_tool_verification_id` (`verification_id`),
  KEY `idx_agent_tool_verification_run` (`run_id`),
  KEY `idx_agent_tool_verification_call` (`tool_call_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent tool verification';

CREATE TABLE IF NOT EXISTS `agent_rag_query` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `rag_query_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `query_text` varchar(512) NOT NULL,
  `knowledge_tag` varchar(128) DEFAULT NULL,
  `filters_ref` varchar(64) DEFAULT NULL,
  `top_k` int DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'REQUESTED',
  `failure_code` varchar(64) DEFAULT NULL,
  `failure_message` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_rag_query_id` (`rag_query_id`),
  KEY `idx_agent_rag_query_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent RAG query';

CREATE TABLE IF NOT EXISTS `agent_rag_hit` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `rag_hit_id` varchar(64) NOT NULL,
  `rag_query_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `chunk_ref` varchar(64) NOT NULL,
  `score` decimal(10,6) DEFAULT NULL,
  `source_title` varchar(512) DEFAULT NULL,
  `source_uri` varchar(1024) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_rag_hit_id` (`rag_hit_id`),
  KEY `idx_agent_rag_hit_query` (`rag_query_id`),
  KEY `idx_agent_rag_hit_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent RAG hit';

CREATE TABLE IF NOT EXISTS `agent_run_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `seq` bigint NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `payload_ref` varchar(64) DEFAULT NULL,
  `user_visible` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_event_id` (`event_id`),
  UNIQUE KEY `uk_agent_run_event_run_seq` (`run_id`, `seq`),
  KEY `idx_agent_run_event_run` (`run_id`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent user-visible run event';

CREATE TABLE IF NOT EXISTS `agent_run_trace` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `trace_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `seq` bigint NOT NULL,
  `trace_type` varchar(64) NOT NULL,
  `payload_ref` varchar(64) DEFAULT NULL,
  `summary` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_trace_id` (`trace_id`),
  KEY `idx_agent_run_trace_run` (`run_id`, `seq`),
  KEY `idx_agent_run_trace_type` (`run_id`, `trace_type`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent developer debug trace';

CREATE TABLE IF NOT EXISTS `agent_run_audit` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `audit_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `audit_type` varchar(64) NOT NULL,
  `payload_ref` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_audit_id` (`audit_id`),
  KEY `idx_agent_run_audit_run` (`run_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent audit record';

CREATE TABLE IF NOT EXISTS `agent_node_prompt` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `prompt_id` varchar(64) NOT NULL,
  `agent_id` varchar(64) NOT NULL,
  `node_code` varchar(64) NOT NULL,
  `prompt_version` varchar(64) NOT NULL,
  `content_ref` varchar(64) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_node_prompt_id` (`prompt_id`),
  KEY `idx_agent_node_prompt_lookup` (`agent_id`, `node_code`, `enabled`),
  KEY `idx_agent_node_prompt_version` (`agent_id`, `node_code`, `prompt_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent editable node prompt';
