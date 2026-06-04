-- AutoAgent main-loop harness schema patch.
-- Execute this file in the same database used by the Spring Boot datasource.
-- It is safe to execute more than once.

SET @schema_name = DATABASE();

SET @agent_evidence_confidence_ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_evidence ADD COLUMN confidence decimal(10,6) DEFAULT NULL AFTER summary',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'agent_evidence'
      AND column_name = 'confidence'
);

PREPARE agent_evidence_confidence_stmt FROM @agent_evidence_confidence_ddl;
EXECUTE agent_evidence_confidence_stmt;
DEALLOCATE PREPARE agent_evidence_confidence_stmt;
