-- Persist verification trust metadata and safe diagnostic payload references.
-- Safe to execute repeatedly against an existing AutoAgent database.

SET NAMES utf8mb4;

ALTER TABLE `agent_evidence`
    MODIFY COLUMN `summary` TEXT DEFAULT NULL;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_evidence' AND COLUMN_NAME = 'content_ref'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `agent_evidence` ADD COLUMN `content_ref` varchar(64) DEFAULT NULL AFTER `summary`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_evidence' AND COLUMN_NAME = 'content_format'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `agent_evidence` ADD COLUMN `content_format` varchar(32) DEFAULT NULL AFTER `content_ref`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_evidence' AND COLUMN_NAME = 'verification_status'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `agent_evidence` ADD COLUMN `verification_status` varchar(32) DEFAULT NULL AFTER `content_format`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_evidence' AND COLUMN_NAME = 'failure_code'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `agent_evidence` ADD COLUMN `failure_code` varchar(128) DEFAULT NULL AFTER `verification_status`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
