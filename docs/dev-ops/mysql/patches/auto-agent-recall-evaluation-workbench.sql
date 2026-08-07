SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_recall_eval_dataset` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `dataset_id` varchar(64) NOT NULL, `name` varchar(160) NOT NULL, `description` varchar(1024) DEFAULT NULL,
  `status` varchar(32) NOT NULL, `eval_user_id` varchar(96) NOT NULL, `eval_session_id` varchar(96) NOT NULL,
  `corpus_count` int NOT NULL DEFAULT 0, `ready_corpus_count` int NOT NULL DEFAULT 0, `case_count` int NOT NULL DEFAULT 0,
  `failure_code` varchar(128) DEFAULT NULL, `failure_message` varchar(2048) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_recall_eval_dataset_id` (`dataset_id`),
  KEY `idx_recall_eval_dataset_status` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recall evaluation dataset';

CREATE TABLE IF NOT EXISTS `agent_recall_eval_corpus_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT, `corpus_item_id` varchar(64) NOT NULL,
  `dataset_id` varchar(64) NOT NULL, `external_id` varchar(160) NOT NULL, `item_type` varchar(32) NOT NULL,
  `title` varchar(512) DEFAULT NULL, `summary` varchar(2048) DEFAULT NULL, `content_ref` varchar(64) DEFAULT NULL,
  `tags_json` text, `source_type` varchar(64) DEFAULT NULL, `source_id` varchar(128) DEFAULT NULL,
  `parent_source_id` varchar(128) DEFAULT NULL, `source_refs_json` longtext, `status` varchar(32) NOT NULL,
  `failure_stage` varchar(64) DEFAULT NULL, `failure_code` varchar(128) DEFAULT NULL,
  `failure_message` varchar(2048) DEFAULT NULL, `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_recall_eval_corpus_item_id` (`corpus_item_id`),
  UNIQUE KEY `uk_recall_eval_corpus_external` (`dataset_id`, `external_id`),
  KEY `idx_recall_eval_corpus_status` (`dataset_id`, `status`, `created_at`),
  KEY `idx_recall_eval_corpus_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recall evaluation corpus item';

CREATE TABLE IF NOT EXISTS `agent_recall_eval_case` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT, `case_id` varchar(64) NOT NULL, `dataset_id` varchar(64) NOT NULL,
  `external_id` varchar(160) NOT NULL, `query_text` text NOT NULL, `source_scope` varchar(32) NOT NULL,
  `expected_json` longtext NOT NULL, `tags_json` text, `status` varchar(32) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_recall_eval_case_id` (`case_id`),
  UNIQUE KEY `uk_recall_eval_case_external` (`dataset_id`, `external_id`),
  KEY `idx_recall_eval_case_status` (`dataset_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recall evaluation labeled case';

CREATE TABLE IF NOT EXISTS `agent_recall_eval_run` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT, `evaluation_run_id` varchar(64) NOT NULL,
  `dataset_id` varchar(64) NOT NULL, `name` varchar(160) DEFAULT NULL, `status` varchar(32) NOT NULL,
  `config_json` longtext NOT NULL, `metrics_json` longtext, `total_case_count` int NOT NULL DEFAULT 0,
  `completed_case_count` int NOT NULL DEFAULT 0, `failed_case_count` int NOT NULL DEFAULT 0,
  `cancel_requested` tinyint(1) NOT NULL DEFAULT 0, `failure_code` varchar(128) DEFAULT NULL,
  `failure_message` varchar(2048) DEFAULT NULL, `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` datetime DEFAULT NULL, `completed_at` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_recall_eval_run_id` (`evaluation_run_id`),
  KEY `idx_recall_eval_run_dataset` (`dataset_id`, `created_at`), KEY `idx_recall_eval_run_status` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recall evaluation run';

CREATE TABLE IF NOT EXISTS `agent_recall_eval_case_result` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT, `case_result_id` varchar(64) NOT NULL,
  `evaluation_run_id` varchar(64) NOT NULL, `case_id` varchar(64) NOT NULL, `status` varchar(32) NOT NULL,
  `retrieval_latency_ms` bigint DEFAULT NULL, `planner_latency_ms` bigint DEFAULT NULL, `hit` tinyint(1) DEFAULT NULL,
  `precision_at_k` decimal(14,8) DEFAULT NULL, `recall_at_k` decimal(14,8) DEFAULT NULL,
  `reciprocal_rank` decimal(14,8) DEFAULT NULL, `ndcg_at_k` decimal(14,8) DEFAULT NULL,
  `average_precision_at_k` decimal(14,8) DEFAULT NULL, `planner_status` varchar(32) DEFAULT NULL,
  `planner_reason` varchar(2048) DEFAULT NULL, `planner_selected_ids_json` longtext, `planner_output_json` longtext,
  `failure_stage` varchar(64) DEFAULT NULL, `failure_code` varchar(128) DEFAULT NULL,
  `failure_message` varchar(2048) DEFAULT NULL, `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_recall_eval_case_result_id` (`case_result_id`),
  UNIQUE KEY `uk_recall_eval_case_result_run_case` (`evaluation_run_id`, `case_id`),
  KEY `idx_recall_eval_case_result_run` (`evaluation_run_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recall evaluation case result';

CREATE TABLE IF NOT EXISTS `agent_recall_eval_hit` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT, `hit_id` varchar(64) NOT NULL,
  `evaluation_run_id` varchar(64) NOT NULL, `case_id` varchar(64) NOT NULL, `rank_no` int NOT NULL,
  `retrieval_channel` varchar(32) NOT NULL, `collection_type` varchar(64) DEFAULT NULL,
  `source_type` varchar(64) DEFAULT NULL, `source_id` varchar(128) NOT NULL,
  `parent_source_id` varchar(128) DEFAULT NULL, `score` decimal(14,8) DEFAULT NULL,
  `expected_grade` int DEFAULT NULL, `selected_by_planner` tinyint(1) DEFAULT NULL,
  `candidate_json` longtext, `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_recall_eval_hit_id` (`hit_id`),
  KEY `idx_recall_eval_hit_case` (`evaluation_run_id`, `case_id`, `rank_no`),
  KEY `idx_recall_eval_hit_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recall evaluation ranked hit';
