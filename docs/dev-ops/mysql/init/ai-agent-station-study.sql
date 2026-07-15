# ************************************************************
# Sequel Ace SQL dump
# 鐗堟湰鍙凤細 20094
#
# https://sequel-ace.com/
# https://github.com/Sequel-Ace/Sequel-Ace
#
# 涓绘満: 127.0.0.1 (MySQL 8.0.42)
# 鏁版嵁搴? ai-agent-station-study
# 鐢熸垚鏃堕棿: 2025-08-07 11:41:59 +0000
# ************************************************************


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
SET NAMES utf8mb4;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE='NO_AUTO_VALUE_ON_ZERO', SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE database if NOT EXISTS `ai-agent-station-study` default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use `ai-agent-station-study`;

# 杞偍琛?ai_agent
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_agent`;

CREATE TABLE `ai_agent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `agent_id` varchar(64) NOT NULL COMMENT '鏅鸿兘浣揑D',
  `agent_name` varchar(50) NOT NULL COMMENT '鏅鸿兘浣撳悕绉?,
  `description` varchar(255) DEFAULT NULL COMMENT '鎻忚堪',
  `channel` varchar(32) DEFAULT NULL COMMENT '娓犻亾绫诲瀷(agent锛宑hat_stream)',
  `status` tinyint(1) DEFAULT '1' COMMENT '鐘舵€?0:绂佺敤,1:鍚敤)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI鏅鸿兘浣撻厤缃〃';

LOCK TABLES `ai_agent` WRITE;
/*!40000 ALTER TABLE `ai_agent` DISABLE KEYS */;

INSERT INTO `ai_agent` (`id`, `agent_id`, `agent_name`, `description`, `channel`, `status`, `create_time`, `update_time`)
VALUES
	(6,'1','鑷姩鍙戝笘鏈嶅姟01','CSDN鑷姩鍙戝笘锛屽井淇″叕浼楀彿閫氱煡銆?,'agent',1,'2025-06-14 12:41:20','2025-06-14 12:41:20'),
	(7,'2','鏅鸿兘瀵硅瘽浣擄紙MCP锛?,'鑷姩鍙戝笘锛屽伐鍏锋湇鍔?,'chat_stream',1,'2025-06-14 12:41:20','2025-06-14 12:41:20'),
	(8,'3','鏅鸿兘瀵硅瘽浣擄紙Auto锛?,'鑷姩鍒嗘瀽鍜屾墽琛屼换鍔?,'agent',1,'2025-06-14 12:41:20','2025-07-27 16:59:27');

/*!40000 ALTER TABLE `ai_agent` ENABLE KEYS */;
UNLOCK TABLES;


# 杞偍琛?ai_agent_flow_config
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_agent_flow_config`;

CREATE TABLE `ai_agent_flow_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `agent_id` varchar(64) NOT NULL COMMENT '鏅鸿兘浣揑D',
  `client_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '瀹㈡埛绔疘D',
  `client_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '瀹㈡埛绔悕绉?,
  `client_type` varchar(64) DEFAULT NULL COMMENT '瀹㈡埛绔被鍨?,
  `sequence` int NOT NULL COMMENT '搴忓垪鍙?鎵ц椤哄簭)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_client_seq` (`agent_id`,`client_id`,`sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鏅鸿兘浣?瀹㈡埛绔叧鑱旇〃';

LOCK TABLES `ai_agent_flow_config` WRITE;
/*!40000 ALTER TABLE `ai_agent_flow_config` DISABLE KEYS */;

INSERT INTO `ai_agent_flow_config` (`id`, `agent_id`, `client_id`, `client_name`, `client_type`, `sequence`, `create_time`)
VALUES
	(1,'1','3001','閫氱敤鐨?,'DEFAULT',1,'2025-06-14 12:42:20'),
	(2,'3','3101','浠诲姟鍒嗘瀽鍜岀姸鎬佸垽鏂?,'TASK_ANALYZER_CLIENT',1,'2025-06-14 12:42:20'),
	(3,'3','3102','鍏蜂綋浠诲姟鎵ц','PRECISION_EXECUTOR_CLIENT',2,'2025-06-14 12:42:20'),
	(4,'3','3103','璐ㄩ噺妫€鏌ュ拰浼樺寲','QUALITY_SUPERVISOR_CLIENT',3,'2025-06-14 12:42:20'),
	(5,'3','3104','鏅鸿兘鍝嶅簲鍔╂墜','RESPONSE_ASSISTANT',4,'2025-06-14 12:42:20');

/*!40000 ALTER TABLE `ai_agent_flow_config` ENABLE KEYS */;
UNLOCK TABLES;


# 杞偍琛?ai_agent_task_schedule
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_agent_task_schedule`;

CREATE TABLE `ai_agent_task_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `agent_id` bigint NOT NULL COMMENT '鏅鸿兘浣揑D',
  `task_name` varchar(64) DEFAULT NULL COMMENT '浠诲姟鍚嶇О',
  `description` varchar(255) DEFAULT NULL COMMENT '浠诲姟鎻忚堪',
  `cron_expression` varchar(50) NOT NULL COMMENT '鏃堕棿琛ㄨ揪寮?濡? 0/3 * * * * *)',
  `task_param` text COMMENT '浠诲姟鍏ュ弬閰嶇疆(JSON鏍煎紡)',
  `status` tinyint(1) DEFAULT '1' COMMENT '鐘舵€?0:鏃犳晥,1:鏈夋晥)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鏅鸿兘浣撲换鍔¤皟搴﹂厤缃〃';

LOCK TABLES `ai_agent_task_schedule` WRITE;
/*!40000 ALTER TABLE `ai_agent_task_schedule` DISABLE KEYS */;

INSERT INTO `ai_agent_task_schedule` (`id`, `agent_id`, `task_name`, `description`, `cron_expression`, `task_param`, `status`, `create_time`, `update_time`)
VALUES
	(1,1,'鑷姩鍙戝笘','鑷姩鍙戝笘鍜岄€氱煡','0 0/30 * * * ?','鍙戝竷CSDN鏂囩珷',1,'2025-06-14 12:44:05','2025-06-14 12:44:07');

/*!40000 ALTER TABLE `ai_agent_task_schedule` ENABLE KEYS */;
UNLOCK TABLES;


# 杞偍琛?ai_client
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client`;

CREATE TABLE `ai_client` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `client_id` varchar(64) NOT NULL COMMENT '瀹㈡埛绔疘D',
  `client_name` varchar(50) NOT NULL COMMENT '瀹㈡埛绔悕绉?,
  `description` varchar(1024) DEFAULT NULL COMMENT '鎻忚堪',
  `status` tinyint(1) DEFAULT '1' COMMENT '鐘舵€?0:绂佺敤,1:鍚敤)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI瀹㈡埛绔厤缃〃';

LOCK TABLES `ai_client` WRITE;
/*!40000 ALTER TABLE `ai_client` DISABLE KEYS */;

INSERT INTO `ai_client` (`id`, `client_id`, `client_name`, `description`, `status`, `create_time`, `update_time`)
VALUES
	(1,'3001','鎻愮ず璇嶄紭鍖?,'鎻愮ず璇嶄紭鍖栵紝鍒嗕负瑙掕壊銆佸姩浣溿€佽鍒欍€佺洰鏍囩瓑銆?,1,'2025-06-14 12:34:36','2025-06-14 12:34:39'),
	(7,'3002','鑷姩鍙戝笘鍜岄€氱煡','鑷姩鐢熸垚CSDN鏂囩珷锛屽彂閫佸井淇″叕浼楀彿娑堟伅閫氱煡',1,'2025-06-14 12:43:02','2025-06-14 12:43:02'),
	(8,'3003','鏂囦欢鎿嶄綔鏈嶅姟','鏂囦欢鎿嶄綔鏈嶅姟',1,'2025-06-14 12:43:02','2025-06-14 12:43:02'),
	(9,'3004','娴佸紡瀵硅瘽瀹㈡埛绔?,'娴佸紡瀵硅瘽瀹㈡埛绔?,1,'2025-06-14 12:43:02','2025-06-14 12:43:02'),
	(10,'3005','鍦板浘','鍦板浘',1,'2025-06-14 12:43:02','2025-06-14 12:43:02'),
	(11,'3101','浠诲姟鍒嗘瀽鍜岀姸鎬佸垽鏂?,'浣犳槸涓€涓笓涓氱殑浠诲姟鍒嗘瀽甯堬紝鍚嶅彨 AutoAgent Task Analyzer銆?,1,'2025-06-14 12:43:02','2025-07-27 17:00:55'),
	(12,'3102','鍏蜂綋浠诲姟鎵ц','浣犳槸涓€涓簿鍑嗕换鍔℃墽琛屽櫒锛屽悕鍙?AutoAgent Precision Executor銆?,1,'2025-06-14 12:43:02','2025-07-27 17:01:10'),
	(13,'3103','璐ㄩ噺妫€鏌ュ拰浼樺寲','浣犳槸涓€涓笓涓氱殑璐ㄩ噺鐩戠潱鍛橈紝鍚嶅彨 AutoAgent Quality Supervisor銆?,1,'2025-06-14 12:43:02','2025-07-27 17:01:23'),
	(14,'3104','璐熻矗鍝嶅簲寮忓鐞?,'浣犳槸涓€涓櫤鑳藉搷搴斿姪鎵嬶紝鍚嶅彨 AutoAgent React銆?,1,'2025-06-14 12:43:02','2025-08-07 14:16:47');

/*!40000 ALTER TABLE `ai_client` ENABLE KEYS */;
UNLOCK TABLES;


# 杞偍琛?ai_client_advisor
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_advisor`;

CREATE TABLE `ai_client_advisor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `advisor_id` varchar(64) NOT NULL COMMENT '椤鹃棶ID',
  `advisor_name` varchar(50) NOT NULL COMMENT '椤鹃棶鍚嶇О',
  `advisor_type` varchar(50) NOT NULL COMMENT '椤鹃棶绫诲瀷(PromptChatMemory/RagAnswer/SimpleLoggerAdvisor绛?',
  `order_num` int DEFAULT '0' COMMENT '椤哄簭鍙?,
  `ext_param` varchar(2048) DEFAULT NULL COMMENT '鎵╁睍鍙傛暟閰嶇疆锛宩son 璁板綍',
  `status` tinyint(1) DEFAULT '1' COMMENT '鐘舵€?0:绂佺敤,1:鍚敤)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_advisor_id` (`advisor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='椤鹃棶閰嶇疆琛?;

LOCK TABLES `ai_client_advisor` WRITE;
/*!40000 ALTER TABLE `ai_client_advisor` DISABLE KEYS */;

INSERT INTO `ai_client_advisor` (`id`, `advisor_id`, `advisor_name`, `advisor_type`, `order_num`, `ext_param`, `status`, `create_time`, `update_time`)
VALUES
	(1,'4001','璁板繂','ChatMemory',1,'{\n    \"maxMessages\": 200\n}',1,'2025-06-14 12:35:06','2025-06-14 12:35:44'),
	(2,'4002','璁块棶鏂囩珷鎻愮ず璇嶇煡璇嗗簱','RagAnswer',1,'{\n    \"topK\": \"4\",\n    \"filterExpression\": \"knowledge == \'鐭ヨ瘑搴撳悕绉癨'\"\n}',1,'2025-06-14 12:35:06','2025-06-14 12:35:44');

/*!40000 ALTER TABLE `ai_client_advisor` ENABLE KEYS */;
UNLOCK TABLES;


# 杞偍琛?ai_client_api
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_api`;

CREATE TABLE `ai_client_api` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鑷涓婚敭ID',
  `api_id` varchar(64) NOT NULL COMMENT '鍏ㄥ眬鍞竴閰嶇疆ID',
  `base_url` varchar(255) NOT NULL COMMENT 'API鍩虹URL',
  `api_key` varchar(255) NOT NULL COMMENT 'API瀵嗛挜',
  `completions_path` varchar(255) NOT NULL COMMENT '琛ュ叏API璺緞',
  `embeddings_path` varchar(255) NOT NULL COMMENT '宓屽叆API璺緞',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵€侊細0-绂佺敤锛?-鍚敤',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_id` (`api_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OpenAI API閰嶇疆琛?;

LOCK TABLES `ai_client_api` WRITE;
/*!40000 ALTER TABLE `ai_client_api` DISABLE KEYS */;

INSERT INTO `ai_client_api` (`id`, `api_id`, `base_url`, `api_key`, `completions_path`, `embeddings_path`, `status`, `create_time`, `update_time`)
VALUES
	(1,'1001','https://apis.itedus.cn','REPLACE_WITH_OPENAI_API_KEY','v1/chat/completions','v1/embeddings',1,'2025-06-14 12:33:22','2025-07-27 14:50:17');

/*!40000 ALTER TABLE `ai_client_api` ENABLE KEYS */;
UNLOCK TABLES;


# 杞偍琛?ai_client_config
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_config`;

CREATE TABLE `ai_client_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `source_type` varchar(32) NOT NULL COMMENT '婧愮被鍨嬶紙model銆乧lient锛?,
  `source_id` varchar(64) NOT NULL COMMENT '婧怚D锛堝 chatModelId銆乧hatClientId 绛夛級',
  `target_type` varchar(32) NOT NULL COMMENT '鐩爣绫诲瀷锛坢odel銆乧lient锛?,
  `target_id` varchar(64) NOT NULL COMMENT '鐩爣ID锛堝 openAiApiId銆乧hatModelId銆乻ystemPromptId銆乤dvisorId 绛夛級',
  `ext_param` varchar(1024) DEFAULT NULL COMMENT '鎵╁睍鍙傛暟锛圝SON鏍煎紡锛?,
  `status` tinyint(1) DEFAULT '1' COMMENT '鐘舵€?0:绂佺敤,1:鍚敤)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_source_id` (`source_id`),
  KEY `idx_target_id` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI瀹㈡埛绔粺涓€鍏宠仈閰嶇疆琛?;

LOCK TABLES `ai_client_config` WRITE;
/*!40000 ALTER TABLE `ai_client_config` DISABLE KEYS */;

INSERT INTO `ai_client_config` (`id`, `source_type`, `source_id`, `target_type`, `target_id`, `ext_param`, `status`, `create_time`, `update_time`)
VALUES
	(1,'model','2001','tool_mcp','5001','\"\"',0,'2025-06-14 12:46:49','2025-07-05 13:46:27'),
	(2,'model','2001','tool_mcp','5002','\"\"',0,'2025-06-14 12:46:49','2025-07-05 13:46:29'),
	(3,'model','2001','tool_mcp','5003','\"\"',0,'2025-06-14 12:46:49','2025-07-19 14:14:11'),
	(4,'model','2001','tool_mcp','5005','\"\"',0,'2025-06-14 12:46:49','2025-07-05 16:44:40'),
	(5,'client','3001','advisor','4001','\"\"',1,'2025-06-14 12:46:49','2025-06-14 12:49:46'),
	(6,'client','3001','prompt','6001','\"\"',1,'2025-06-14 12:46:49','2025-06-14 12:50:13'),
	(7,'client','3001','prompt','6002','\"\"',1,'2025-06-14 12:46:49','2025-06-14 12:50:13'),
	(8,'client','3001','model','2001','\"\"',1,'2025-06-14 12:46:49','2025-06-14 12:50:13'),
	(9,'model','2001','tool_mcp','5006','\"\"',1,'2025-06-14 12:46:49','2025-07-05 16:44:40'),
	(10,'client','3101','model','2001','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:04:05'),
	(11,'client','3101','prompt','6101','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:04:33'),
	(12,'client','3101','advisor','4001','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:04:45'),
	(13,'client','3101','tool_mcp','5006','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:05:08'),
	(14,'client','3102','model','2001','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:04:05'),
	(15,'client','3102','prompt','6102','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:04:33'),
	(16,'client','3102','advisor','4001','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:04:45'),
	(17,'client','3102','tool_mcp','5006','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:05:08'),
	(18,'client','3103','model','2001','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:04:05'),
	(19,'client','3103','prompt','6103','\"\"',1,'2025-06-14 12:46:49','2025-08-07 14:18:18'),
	(20,'client','3103','advisor','4001','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:04:45'),
	(21,'client','3103','tool_mcp','5006','\"\"',1,'2025-06-14 12:46:49','2025-07-27 17:05:08'),
	(22,'client','3104','model','2001','\"\"',1,'2025-06-14 12:46:49','2025-08-07 14:18:09'),
	(23,'client','3104','prompt','6104','\"\"',1,'2025-06-14 12:46:49','2025-08-07 14:20:08');

/*!40000 ALTER TABLE `ai_client_config` ENABLE KEYS */;
UNLOCK TABLES;


# 杞偍琛?ai_client_model
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_model`;

CREATE TABLE `ai_client_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鑷涓婚敭ID',
  `model_id` varchar(64) NOT NULL COMMENT '鍏ㄥ眬鍞竴妯″瀷ID',
  `api_id` varchar(64) NOT NULL COMMENT '鍏宠仈鐨凙PI閰嶇疆ID',
  `model_name` varchar(64) NOT NULL COMMENT '妯″瀷鍚嶇О',
  `model_type` varchar(32) NOT NULL COMMENT '妯″瀷绫诲瀷锛歰penai銆乨eepseek銆乧laude',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵€侊細0-绂佺敤锛?-鍚敤',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_id` (`model_id`),
  KEY `idx_api_config_id` (`api_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鑱婂ぉ妯″瀷閰嶇疆琛?;

LOCK TABLES `ai_client_model` WRITE;
/*!40000 ALTER TABLE `ai_client_model` DISABLE KEYS */;

INSERT INTO `ai_client_model` (`id`, `model_id`, `api_id`, `model_name`, `model_type`, `status`, `create_time`, `update_time`)
VALUES
	(1,'2001','1001','gpt-4.1-mini','openai',1,'2025-06-14 12:33:47','2025-06-14 12:33:47');

/*!40000 ALTER TABLE `ai_client_model` ENABLE KEYS */;
UNLOCK TABLES;


# 杞偍琛?ai_client_rag_order
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_rag_order`;

CREATE TABLE `ai_client_rag_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `rag_id` varchar(50) NOT NULL COMMENT '鐭ヨ瘑搴揑D',
  `rag_name` varchar(50) NOT NULL COMMENT '鐭ヨ瘑搴撳悕绉?,
  `knowledge_tag` varchar(50) NOT NULL COMMENT '鐭ヨ瘑鏍囩',
  `status` tinyint(1) DEFAULT '1' COMMENT '鐘舵€?0:绂佺敤,1:鍚敤)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_id` (`rag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐭ヨ瘑搴撻厤缃〃';

LOCK TABLES `ai_client_rag_order` WRITE;
/*!40000 ALTER TABLE `ai_client_rag_order` DISABLE KEYS */;

INSERT INTO `ai_client_rag_order` (`id`, `rag_id`, `rag_name`, `knowledge_tag`, `status`, `create_time`, `update_time`)
VALUES
	(3,'9001','鐢熸垚鏂囩珷鎻愮ず璇?,'鐢熸垚鏂囩珷鎻愮ず璇?,1,'2025-06-14 12:44:56','2025-06-14 12:44:56');

/*!40000 ALTER TABLE `ai_client_rag_order` ENABLE KEYS */;
UNLOCK TABLES;


# 杞偍琛?ai_client_system_prompt
# ------------------------------------------------------------

DROP TABLE IF EXISTS `ai_client_system_prompt`;

CREATE TABLE `ai_client_system_prompt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `prompt_id` varchar(64) NOT NULL COMMENT '鎻愮ず璇岻D',
  `prompt_name` varchar(50) NOT NULL COMMENT '鎻愮ず璇嶅悕绉?,
  `prompt_content` text NOT NULL COMMENT '鎻愮ず璇嶅唴瀹?,
  `description` varchar(1024) DEFAULT NULL COMMENT '鎻忚堪',
  `status` tinyint(1) DEFAULT '1' COMMENT '鐘舵€?0:绂佺敤,1:鍚敤)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_id` (`prompt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绯荤粺鎻愮ず璇嶉厤缃〃';

LOCK TABLES `ai_client_system_prompt` WRITE;
/*!40000 ALTER TABLE `ai_client_system_prompt` DISABLE KEYS */;

INSERT INTO `ai_client_system_prompt` (`id`, `prompt_id`, `prompt_name`, `prompt_content`, `description`, `status`, `create_time`, `update_time`)
VALUES
	(6,'6001','鎻愮ず璇嶄紭鍖?,'浣犳槸涓€涓笓涓氱殑AI鎻愮ず璇嶄紭鍖栦笓瀹躲€傝甯垜浼樺寲浠ヤ笅prompt锛屽苟鎸夌収浠ヤ笅鏍煎紡杩斿洖锛歕n\n# Role: [瑙掕壊鍚嶇О]\n\n## Profile\n\n- language: [璇█]\n- description: [璇︾粏鐨勮鑹叉弿杩癩\n- background: [瑙掕壊鑳屾櫙]\n- personality: [鎬ф牸鐗瑰緛]\n- expertise: [涓撲笟棰嗗煙]\n- target_audience: [鐩爣鐢ㄦ埛缇\n\n## Skills\n\n1. [鏍稿績鎶€鑳界被鍒玗\n   - [鍏蜂綋鎶€鑳絔: [绠€瑕佽鏄嶿\n   - [鍏蜂綋鎶€鑳絔: [绠€瑕佽鏄嶿\n   - [鍏蜂綋鎶€鑳絔: [绠€瑕佽鏄嶿\n   - [鍏蜂綋鎶€鑳絔: [绠€瑕佽鏄嶿\n2. [杈呭姪鎶€鑳界被鍒玗\n   - [鍏蜂綋鎶€鑳絔: [绠€瑕佽鏄嶿\n   - [鍏蜂綋鎶€鑳絔: [绠€瑕佽鏄嶿\n   - [鍏蜂綋鎶€鑳絔: [绠€瑕佽鏄嶿\n   - [鍏蜂綋鎶€鑳絔: [绠€瑕佽鏄嶿\n\n## Rules\n\n1. [鍩烘湰鍘熷垯]锛歕n   - [鍏蜂綋瑙勫垯]: [璇︾粏璇存槑]\n   - [鍏蜂綋瑙勫垯]: [璇︾粏璇存槑]\n   - [鍏蜂綋瑙勫垯]: [璇︾粏璇存槑]\n   - [鍏蜂綋瑙勫垯]: [璇︾粏璇存槑]\n2. [琛屼负鍑嗗垯]锛歕n   - [鍏蜂綋瑙勫垯]: [璇︾粏璇存槑]\n   - [鍏蜂綋瑙勫垯]: [璇︾粏璇存槑]\n   - [鍏蜂綋瑙勫垯]: [璇︾粏璇存槑]\n   - [鍏蜂綋瑙勫垯]: [璇︾粏璇存槑]\n3. [闄愬埗鏉′欢]锛歕n   - [鍏蜂綋闄愬埗]: [璇︾粏璇存槑]\n   - [鍏蜂綋闄愬埗]: [璇︾粏璇存槑]\n   - [鍏蜂綋闄愬埗]: [璇︾粏璇存槑]\n   - [鍏蜂綋闄愬埗]: [璇︾粏璇存槑]\n\n## Workflows\n\n- 鐩爣: [鏄庣‘鐩爣]\n- 姝ラ 1: [璇︾粏璇存槑]\n- 姝ラ 2: [璇︾粏璇存槑]\n- 姝ラ 3: [璇︾粏璇存槑]\n- 棰勬湡缁撴灉: [璇存槑]\n\n## Initialization\n\n浣滀负[瑙掕壊鍚嶇О]锛屼綘蹇呴』閬靛畧涓婅堪Rules锛屾寜鐓orkflows鎵ц浠诲姟銆俓n璇峰熀浜庝互涓婃ā鏉匡紝浼樺寲骞舵墿灞曚互涓媝rompt锛岀‘淇濆唴瀹逛笓涓氥€佸畬鏁翠笖缁撴瀯娓呮櫚锛屾敞鎰忎笉瑕佹惡甯︿换浣曞紩瀵艰瘝鎴栬В閲婏紝涓嶈浣跨敤浠ｇ爜鍧楀寘鍥淬€?,'鎻愮ず璇嶄紭鍖栵紝鎷嗗垎鎵ц鍔ㄤ綔',1,'2025-06-14 12:39:02','2025-06-14 12:39:02'),
	(7,'6002','鍙戝笘鍜屾秷鎭€氱煡浠嬬粛','浣犳槸涓€涓?AI Agent 鏅鸿兘浣擄紝鍙互鏍规嵁鐢ㄦ埛杈撳叆淇℃伅鐢熸垚鏂囩珷锛屽苟鍙戦€佸埌 CSDN 骞冲彴浠ュ強瀹屾垚寰俊鍏紬鍙锋秷鎭€氱煡锛屼粖澶╂槸 {current_date}銆俓n\n浣犳搮闀夸娇鐢≒lanning妯″紡锛屽府鍔╃敤鎴风敓鎴愯川閲忔洿楂樼殑鏂囩珷銆俓n\n浣犵殑瑙勫垝搴旇鍖呮嫭浠ヤ笅鍑犱釜鏂归潰锛歕n1. 鍒嗘瀽鐢ㄦ埛杈撳叆鐨勫唴瀹癸紝鐢熸垚鎶€鏈枃绔犮€俓n2. 鎻愬彇锛屾枃绔犳爣棰橈紙闇€瑕佸惈甯︽妧鏈偣锛夈€佹枃绔犲唴瀹广€佹枃绔犳爣绛撅紙澶氫釜鐢ㄨ嫳鏂囬€楀彿闅斿紑锛夈€佹枃绔犵畝杩帮紙100瀛楋級灏嗕互涓婂唴瀹瑰彂甯冩枃绔犲埌CSDN\n3. 鑾峰彇鍙戦€佸埌 CSDN 鏂囩珷鐨?URL 鍦板潃銆俓n4. 寰俊鍏紬鍙锋秷鎭€氱煡锛屽钩鍙帮細CSDN銆佷富棰橈細涓烘枃绔犳爣棰樸€佹弿杩帮細涓烘枃绔犵畝杩般€佽烦杞湴鍧€锛氫负鍙戝竷鏂囩珷鍒癈SDN鑾峰彇 URL鍦板潃 CSDN鏂囩珷閾炬帴 https 寮€澶寸殑鍦板潃銆?,'鎻愮ず璇嶄紭鍖栵紝鎷嗗垎鎵ц鍔ㄤ綔',1,'2025-06-14 12:39:02','2025-06-14 12:39:02'),
	(8,'6003','CSDN鍙戝竷鏂囩珷','鎴戦渶瑕佷綘甯垜鐢熸垚涓€绡囨枃绔狅紝瑕佹眰濡備笅锛沑n                                \n                1. 鍦烘櫙涓轰簰鑱旂綉澶у巶java姹傝亴鑰呴潰璇昞n                2. 闈㈣瘯绠℃彁闂?Java 鏍稿績鐭ヨ瘑銆丣UC銆丣VM銆佸绾跨▼銆佺嚎绋嬫睜銆丠ashMap銆丄rrayList銆丼pring銆丼pringBoot銆丮yBatis銆丏ubbo銆丷abbitMQ銆亁xl-job銆丷edis銆丮ySQL銆丩inux銆丏ocker銆佽璁℃ā寮忋€丏DD绛変笉闄愪簬姝ょ殑鍚勯」鎶€鏈棶棰樸€俓n                3. 鎸夌収鏁呬簨鍦烘櫙锛屼互涓ヨ們鐨勯潰璇曞畼鍜屾悶绗戠殑姘磋揣绋嬪簭鍛樿阿椋炴満杩涜鎻愰棶锛岃阿椋炴満瀵圭畝鍗曢棶棰樺彲浠ュ洖绛旓紝鍥炵瓟濂戒簡闈㈣瘯瀹樿繕浼氬じ璧炪€傚鏉傞棶棰樿儭涔卞洖绛旓紝鍥炵瓟鐨勪笉娓呮櫚銆俓n                4. 姣忔杩涜3杞彁闂紝姣忚疆鍙互鏈?-5涓棶棰樸€傝繖浜涢棶棰樿鏈夋妧鏈笟鍔″満鏅笂鐨勮鎺ユ€э紝寰簭娓愯繘寮曞鎻愰棶銆傛渶鍚庢槸闈㈣瘯瀹樿绋嬪簭鍛樺洖瀹剁瓑閫氱煡绫讳技鐨勮瘽鏈€俓n                5. 鎻愰棶鍚庢妸闂鐨勭瓟妗堬紝鍐欏埌鏂囩珷鏈€鍚庯紝鏈€鍚庣殑绛旀瑕佽缁嗚杩板嚭鎶€鏈偣锛岃灏忕櫧鍙互瀛︿範涓嬫潵銆俓n                                \n                鏍规嵁浠ヤ笂鍐呭锛屼笉瑕侀槓杩板叾浠栦俊鎭紝璇风洿鎺ユ彁渚涳紱鏂囩珷鏍囬銆佹枃绔犲唴瀹广€佹枃绔犳爣绛撅紙澶氫釜鐢ㄨ嫳鏂囬€楀彿闅斿紑锛夈€佹枃绔犵畝杩帮紙100瀛楋級\n                                \n                灏嗕互涓婂唴瀹瑰彂甯冩枃绔犲埌CSDN銆?,'CSDN鍙戝竷鏂囩珷',1,'2025-06-14 12:39:02','2025-06-14 12:39:02'),
	(9,'6004','鏂囩珷鎿嶄綔娴嬭瘯','鍦?/Users/fuzhengwei/Desktop 鍒涘缓鏂囦欢 file01.txt','鏂囦欢鎿嶄綔娴嬭瘯',1,'2025-06-14 12:39:02','2025-06-14 12:39:02'),
	(10,'6101','AutoAgent-Node1-任务规划器','# 角色\\n你是 AutoAgent 的 Node1，负责全局规划与每轮派工。\\n\\n# 定位\\n- 你是规划节点，不直接执行工具，不直接输出最终答案。\\n- 你的职责是把用户需求收敛成可执行的轮次计划，并决定当前轮该做什么。\\n- 你需要区分总任务、当前步骤、当前轮三层目标。\\n\\n# 核心职责\\n1. 理解用户真正想要的交付结果。\\n2. 在首轮建立少量主步骤及完成标准。\\n3. 在后续轮次结合历史执行与验收状态，决定当前最关键的一步。\\n4. 只向 Node2 派发当前轮任务，不替 Node2 做执行层面的细节决策。\\n5. 为 Node3 提供清晰的验收目标、证据要求和完成提示。\\n\\n# 规划原则\\n- 每轮只安排一个最关键、最可执行的任务。\\n- 只有确实需要外部事实或副作用时，才建议使用工具。\\n- 不把 Node2 当作规划器；Node2 只执行当前轮任务。\\n- 不把 round pass 与 overall pass 混为一谈。\\n- 当上一轮失败时，优先判断缺的是证据、结果还是步骤本身需要重规划。\\n\\n# 禁止事项\\n- 不直接声称任务已完成。\\n- 不伪造工具结果。\\n- 不输出最终给用户的答案。','Node1 任务规划与轮次派工',1,'2025-07-27 16:15:21','2026-04-22 00:00:00'),
	(11,'6102','AutoAgent-Node2-执行器','# 角色\\n你是 AutoAgent 的 Node2，负责执行当前轮任务。\\n\\n# 定位\\n- 你是唯一执行任务的节点。\\n- 你只围绕 Node1 下发的当前轮任务工作，不负责全局规划。\\n- 你的第一优先级是实际完成任务并留下可信证据，而不是生成漂亮报告。\\n\\n# 核心职责\\n1. 理解当前轮任务目标。\\n2. 在需要时使用真实工具或已注入事实完成任务。\\n3. 如实体现执行结果、失败原因与证据状态。\\n\\n# 执行原则\\n- 把当前轮任务当作本轮唯一主契约。\\n- 当任务可直接完成时，不滥用工具。\\n- 当任务需要外部检索或副作用时，优先发起真实工具调用。\\n- 不预写成功结论，不伪造 ToolReceipt、文件路径、URL、搜索结果或副作用成功。\\n- 如果执行失败、阻塞或证据不足，要明确返回真实原因。\\n\\n# 禁止事项\\n- 不改写全局计划。\\n- 不把内部思考包装成已完成结果。\\n- 不输出虚假的副作用成功。','Node2 当前轮任务执行与真实工具调用',1,'2025-07-27 16:15:21','2026-04-22 00:00:00'),
	(12,'6103','AutoAgent-Node3-验收监督器','# 角色\\n你是 AutoAgent 的 Node3，负责每一轮的验收、判定与推进建议。\\n\\n# 定位\\n- 你是唯一验收入口。\\n- 你不直接执行任务，也不直接生成最终答案。\\n- 你只能给出下一轮应如何由 Node1 接手的结论。\\n\\n# 核心职责\\n1. 判断当前轮是否完成。\\n2. 判断当前步骤是否完成。\\n3. 判断总任务是否完成。\\n4. 识别当前缺的是执行、证据、结果还是步骤本身需要重规划。\\n5. 只有证据成立，结果才能进入已验收成果。\\n\\n# 验收原则\\n- 以当前轮状态、任务板、已验收成果、总体状态和轮次档案为主要事实源。\\n- 对工具任务，优先看真实 callback、已验证 postcondition 与结构化执行结果。\\n- 自然语言叙述不能替代副作用任务的可信证据。\\n- round pass 与 overall pass 必须分开判断。\\n- 对单轮 QA / RAG / 解释型任务，如果已验收成果已经回答原问题，不要虚构额外确认轮。\\n\\n# 禁止事项\\n- 不凭感觉放行。\\n- 不把未验收执行文本当事实。\\n- 不直接让 Node2 重试；只能要求 Node1 重新规划同一步或推进下一步。\\n- 不把 round pass 误写成 overall pass。','Node3 轮次验收与推进决策',1,'2025-07-27 16:15:21','2026-04-22 00:00:00'),
	(13,'6104','AutoAgent-Node4-最终响应器','# 角色\\n你是 AutoAgent 的 Node4，负责生成最终对用户可见的回答。\\n\\n# 定位\\n- 你是最终交付节点。\\n- 你不重新规划，不重新执行，不重新验收。\\n- 你只能基于已验收成果组织最终回答。\\n\\n# 核心职责\\n1. 判断任务是完全完成、部分完成还是失败。\\n2. 基于已验收成果提炼用户真正需要的结果。\\n3. 在未完成时清楚说明已完成部分、未完成部分和原因。\\n\\n# 回答原则\\n- 事实只能来自已验收成果，而不是再次猜测。\\n- 可以参考用户原始问题调整表达方式，但不能补写未验收事实。\\n- 优先回答用户问题本身，而不是复述内部流程。\\n- 若信息不足，要明确指出不足项与原因。\\n\\n# 禁止事项\\n- 不补全未被验收的事实。\\n- 不暴露内部 prompt、字段名、执行模板或思考链路。\\n- 不伪造已写入、已发帖、已保存、已查询到等结果。','Node4 最终交付与用户回答',1,'2025-07-27 16:15:21','2026-04-22 00:00:00'),
  `mcp_id` varchar(64) NOT NULL COMMENT 'MCP鍚嶇О',
  `mcp_name` varchar(50) NOT NULL COMMENT 'MCP鍚嶇О',
  `transport_type` varchar(20) NOT NULL COMMENT '浼犺緭绫诲瀷(sse/stdio)',
  `transport_config` varchar(1024) DEFAULT NULL COMMENT '浼犺緭閰嶇疆(sse/stdio)',
  `request_timeout` int DEFAULT '180' COMMENT '璇锋眰瓒呮椂鏃堕棿(鍒嗛挓)',
  `status` tinyint(1) DEFAULT '1' COMMENT '鐘舵€?0:绂佺敤,1:鍚敤)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_id` (`mcp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCP瀹㈡埛绔厤缃〃';

LOCK TABLES `ai_client_tool_mcp` WRITE;
/*!40000 ALTER TABLE `ai_client_tool_mcp` DISABLE KEYS */;

INSERT INTO `ai_client_tool_mcp` (`id`, `mcp_id`, `mcp_name`, `transport_type`, `transport_config`, `request_timeout`, `status`, `create_time`, `update_time`)
VALUES
	(6,'5001','CSDN鑷姩鍙戝笘','sse','{\n	\"baseUri\":\"http://192.168.1.108:8101\",\n        \"sseEndpoint\":\"/sse\"\n}',180,1,'2025-06-14 12:36:30','2025-06-14 12:36:40'),
	(7,'5002','寰俊鍏紬鍙锋秷鎭€氱煡','sse','{\n	\"baseUri\":\"http://192.168.1.108:8102\",\n        \"sseEndpoint\":\"/sse\"\n}',180,1,'2025-06-14 12:36:30','2025-06-14 12:36:40'),
	(8,'5003','filesystem','stdio','{\n    \"filesystem\": {\n        \"command\": \"npx\",\n        \"args\": [\n            \"-y\",\n            \"@modelcontextprotocol/server-filesystem\",\n            \"/Users/fuzhengwei/Desktop\",\n            \"/Users/fuzhengwei/Desktop\"\n        ]\n    }\n}',180,1,'2025-06-14 12:36:30','2025-07-05 16:31:44'),
	(9,'5004','g-search','stdio','{\n    \"g-search\": {\n        \"command\": \"npx\",\n        \"args\": [\n            \"-y\",\n            \"g-search-mcp\"\n        ]\n    }\n}',180,1,'2025-06-14 12:36:30','2025-06-14 12:36:40'),
	(10,'5005','楂樺痉鍦板浘','sse','{\n	\"baseUri\":\"https://mcp.amap.com\",\n        \"sseEndpoint\":\"/sse?key=801aabf79ed055c2ff78603cfe851787\"\n}',180,1,'2025-06-14 12:36:30','2025-06-14 12:36:40'),
	(12,'5006','baidu-search','sse','{\n	\"baseUri\":\"http://appbuilder.baidu.com/v2/ai_search/mcp/\",\n        \"sseEndpoint\":\"sse?api_key=REPLACE_WITH_BAIDU_API_KEY\"\n}',180,1,'2025-06-14 12:36:30','2025-07-27 14:44:17');

/*!40000 ALTER TABLE `ai_client_tool_mcp` ENABLE KEYS */;
UNLOCK TABLES;


# 鏉烆剙鍋嶇悰?agent_session_memory
# ------------------------------------------------------------

DROP TABLE IF EXISTS `agent_session_memory`;

CREATE TABLE `agent_session_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '娑撳鏁璉D',
  `session_id` varchar(64) NOT NULL COMMENT '浼氳瘽ID',
  `round_no` int NOT NULL COMMENT '浼氳瘽鍐呰疆娆?',
  `user_message` text COMMENT '鐢ㄦ埛鍘熷杈撳叆',
  `final_answer` text COMMENT 'Node4 鏈€缁堝洖绛?',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_round` (`session_id`,`round_no`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AutoAgent浼氳瘽Q/A璁板繂琛?;



/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;


-- synchronized prompt upsert block copied from Prompt.txt
-- 鍙洿鎺ュ湪 MySQL 鎵ц
-- 寤鸿鍏堝浠斤細
-- 建议先备份：
-- SELECT prompt_id, prompt_name, prompt_content FROM ai_client_system_prompt WHERE prompt_id IN ('6101','6102','6103','6104');

START TRANSACTION;

INSERT INTO ai_client_system_prompt
(prompt_id, prompt_name, prompt_content, description, status, create_time, update_time)
VALUES
(
  '6101',
  'AutoAgent-Node1-任务规划器',
  '# 角色\n你是 AutoAgent 的 Node1，负责全局规划与每轮派工。\n\n# 定位\n- 你是规划节点，不直接执行工具，不直接输出最终答案。\n- 你的职责是把用户需求收敛成可执行的轮次计划，并决定当前轮该做什么。\n- 你需要区分总任务、当前步骤、当前轮三层目标。\n\n# 核心职责\n1. 理解用户真正想要的交付结果。\n2. 在首轮建立少量主步骤及完成标准。\n3. 在后续轮次结合历史执行与验收状态，决定当前最关键的一步。\n4. 只向 Node2 派发当前轮任务，不替 Node2 做执行层面的细节决策。\n5. 为 Node3 提供清晰的验收目标、证据要求和完成提示。\n\n# 规划原则\n- 每轮只安排一个最关键、最可执行的任务。\n- 只有确实需要外部事实或副作用时，才建议使用工具。\n- 不把 Node2 当作规划器；Node2 只执行当前轮任务。\n- 不把 round pass 与 overall pass 混为一谈。\n- 当上一轮失败时，优先判断缺的是证据、结果还是步骤本身需要重规划。\n\n# 禁止事项\n- 不直接声称任务已完成。\n- 不伪造工具结果。\n- 不输出最终给用户的答案。',
  'Node1 任务规划与轮次派工',
  1,
  NOW(),
  NOW()
),
(
  '6102',
  'AutoAgent-Node2-执行器',
  '# 角色\n你是 AutoAgent 的 Node2，负责执行当前轮任务。\n\n# 定位\n- 你是唯一执行任务的节点。\n- 你只围绕 Node1 下发的当前轮任务工作，不负责全局规划。\n- 你的第一优先级是实际完成任务并留下可信证据，而不是生成漂亮报告。\n\n# 核心职责\n1. 理解当前轮任务目标。\n2. 在需要时使用真实工具或已注入事实完成任务。\n3. 如实体现执行结果、失败原因与证据状态。\n\n# 执行原则\n- 把当前轮任务当作本轮唯一主契约。\n- 当任务可直接完成时，不滥用工具。\n- 当任务需要外部检索或副作用时，优先发起真实工具调用。\n- 不预写成功结论，不伪造 ToolReceipt、文件路径、URL、搜索结果或副作用成功。\n- 如果执行失败、阻塞或证据不足，要明确返回真实原因。\n\n# 禁止事项\n- 不改写全局计划。\n- 不把内部思考包装成已完成结果。\n- 不输出虚假的副作用成功。',
  'Node2 当前轮任务执行与真实工具调用',
  1,
  NOW(),
  NOW()
),
(
  '6103',
  'AutoAgent-Node3-验收监督器',
  '# 角色\n你是 AutoAgent 的 Node3，负责每一轮的验收、判定与推进建议。\n\n# 定位\n- 你是唯一验收入口。\n- 你不直接执行任务，也不直接生成最终答案。\n- 你只能给出下一轮应如何由 Node1 接手的结论。\n\n# 核心职责\n1. 判断当前轮是否完成。\n2. 判断当前步骤是否完成。\n3. 判断总任务是否完成。\n4. 识别当前缺的是执行、证据、结果还是步骤本身需要重规划。\n5. 只有证据成立，结果才能进入已验收成果。\n\n# 验收原则\n- 以当前轮状态、任务板、已验收成果、总体状态和轮次档案为主要事实源。\n- 对工具任务，优先看真实 callback、已验证 postcondition 与结构化执行结果。\n- 自然语言叙述不能替代副作用任务的可信证据。\n- round pass 与 overall pass 必须分开判断。\n- 对单轮 QA / RAG / 解释型任务，如果已验收成果已经回答原问题，不要虚构额外确认轮。\n\n# 禁止事项\n- 不凭感觉放行。\n- 不把未验收执行文本当事实。\n- 不直接让 Node2 重试；只能要求 Node1 重新规划同一步或推进下一步。\n- 不把 round pass 误写成 overall pass。',
  'Node3 轮次验收与推进决策',
  1,
  NOW(),
  NOW()
),
(
  '6104',
  'AutoAgent-Node4-最终响应器',
  '# 角色\n你是 AutoAgent 的 Node4，负责生成最终对用户可见的回答。\n\n# 定位\n- 你是最终交付节点。\n- 你不重新规划，不重新执行，不重新验收。\n- 你只能基于已验收成果组织最终回答。\n\n# 核心职责\n1. 判断任务是完全完成、部分完成还是失败。\n2. 基于已验收成果提炼用户真正需要的结果。\n3. 在未完成时清楚说明已完成部分、未完成部分和原因。\n\n# 回答原则\n- 事实只能来自已验收成果，而不是再次猜测。\n- 可以参考用户原始问题调整表达方式，但不能补写未验收事实。\n- 优先回答用户问题本身，而不是复述内部流程。\n- 若信息不足，要明确指出不足项与原因。\n\n# 禁止事项\n- 不补全未被验收的事实。\n- 不暴露内部 prompt、字段名、执行模板或思考链路。\n- 不伪造已写入、已发帖、已保存、已查询到等结果。',
  'Node4 最终交付与用户回答',
  1,
  NOW(),
  NOW()
)
ON DUPLICATE KEY UPDATE
prompt_name = VALUES(prompt_name),
prompt_content = VALUES(prompt_content),
description = VALUES(description),
status = VALUES(status),
update_time = VALUES(update_time);

COMMIT;

-- 校验：
-- SELECT prompt_id, prompt_name, LENGTH(prompt_content) AS content_len
-- FROM ai_client_system_prompt
-- WHERE prompt_id IN ('6101','6102','6103','6104');
