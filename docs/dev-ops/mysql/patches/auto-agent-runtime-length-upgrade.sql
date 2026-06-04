ALTER TABLE agent_run
    MODIFY COLUMN failure_message TEXT NULL;

ALTER TABLE agent_rag_query
    MODIFY COLUMN failure_message TEXT NULL;
