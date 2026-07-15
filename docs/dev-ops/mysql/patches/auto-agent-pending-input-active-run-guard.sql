ALTER TABLE `agent_pending_input`
    ADD COLUMN `active_run_id` varchar(64)
        GENERATED ALWAYS AS (CASE WHEN `status` = 'PENDING' THEN `run_id` ELSE NULL END) STORED,
    ADD UNIQUE KEY `uk_agent_pending_input_active_run` (`active_run_id`);
