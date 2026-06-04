-- AutoAgent RAG asset migration.
-- Safe for existing databases: creates RAG asset tables if they do not exist.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_rag_document` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `document_id` varchar(64) NOT NULL,
  `user_id` varchar(64) DEFAULT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  `source_type` varchar(64) NOT NULL,
  `source_name` varchar(512) DEFAULT NULL,
  `repository_url` varchar(1024) DEFAULT NULL,
  `repository_name` varchar(256) DEFAULT NULL,
  `branch_name` varchar(256) DEFAULT NULL,
  `relative_path` varchar(1024) DEFAULT NULL,
  `title` varchar(512) DEFAULT NULL,
  `summary` text,
  `content_ref` varchar(64) DEFAULT NULL,
  `summary_ref` varchar(64) DEFAULT NULL,
  `content_sha256` varchar(128) DEFAULT NULL,
  `status` varchar(64) NOT NULL DEFAULT 'INGESTING',
  `chunk_count` int DEFAULT NULL,
  `failure_message` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_rag_document_id` (`document_id`),
  KEY `idx_agent_rag_document_user` (`user_id`, `status`, `updated_at`),
  KEY `idx_agent_rag_document_session` (`session_id`, `status`, `updated_at`),
  KEY `idx_agent_rag_document_source` (`source_type`, `source_name`(128))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent private RAG document asset';

CREATE TABLE IF NOT EXISTS `agent_rag_chunk` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `chunk_id` varchar(96) NOT NULL,
  `document_id` varchar(64) NOT NULL,
  `user_id` varchar(64) DEFAULT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  `chunk_no` int NOT NULL,
  `chunk_type` varchar(64) NOT NULL DEFAULT 'TEXT_PARAGRAPH',
  `heading_path` varchar(1024) DEFAULT NULL,
  `summary` text,
  `content_ref` varchar(64) DEFAULT NULL,
  `retrieval_text_ref` varchar(64) DEFAULT NULL,
  `content_sha256` varchar(128) DEFAULT NULL,
  `status` varchar(64) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_rag_chunk_id` (`chunk_id`),
  KEY `idx_agent_rag_chunk_document` (`document_id`, `chunk_no`),
  KEY `idx_agent_rag_chunk_user` (`user_id`, `status`),
  KEY `idx_agent_rag_chunk_session` (`session_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent private RAG document chunk';

CREATE TABLE IF NOT EXISTS `agent_rag_code_file` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `code_file_id` varchar(64) NOT NULL,
  `document_id` varchar(64) NOT NULL,
  `repository_url` varchar(1024) DEFAULT NULL,
  `branch_name` varchar(256) DEFAULT NULL,
  `relative_path` varchar(1024) NOT NULL,
  `language` varchar(64) DEFAULT NULL,
  `file_summary` text,
  `status` varchar(64) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_rag_code_file_id` (`code_file_id`),
  KEY `idx_agent_rag_code_file_document` (`document_id`),
  KEY `idx_agent_rag_code_file_repo_path` (`repository_url`(128), `relative_path`(256)),
  KEY `idx_agent_rag_code_file_language` (`language`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent RAG code file metadata';
