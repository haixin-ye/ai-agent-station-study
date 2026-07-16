DROP PROCEDURE IF EXISTS `assert_no_duplicate_active_pending_input`;

DELIMITER $$

CREATE PROCEDURE `assert_no_duplicate_active_pending_input`()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM `agent_pending_input`
        WHERE `status` = 'PENDING'
        GROUP BY `run_id`
        HAVING COUNT(*) > 1
    ) THEN
        SELECT `run_id`, COUNT(*) AS `pending_count`
        FROM `agent_pending_input`
        WHERE `status` = 'PENDING'
        GROUP BY `run_id`
        HAVING COUNT(*) > 1;
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate PENDING rows exist; resolve listed run_id values before adding the active-run guard.';
    END IF;
END$$

DELIMITER ;

CALL `assert_no_duplicate_active_pending_input`();
DROP PROCEDURE `assert_no_duplicate_active_pending_input`;

ALTER TABLE `agent_pending_input`
    ADD COLUMN `active_run_id` varchar(64)
        GENERATED ALWAYS AS (CASE WHEN `status` = 'PENDING' THEN `run_id` ELSE NULL END) STORED,
    ADD UNIQUE KEY `uk_agent_pending_input_active_run` (`active_run_id`);
