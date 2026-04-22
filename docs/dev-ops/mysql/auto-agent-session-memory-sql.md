# Auto Agent Session Memory SQL 操作文档

## 1. 目的

本文档用于说明 Auto Agent 会话多轮记忆功能对应的数据库变更。

本次数据库设计目标：

- 按 `session_id` 保存用户级多轮问答记忆
- 每条记录只保存一轮 `user_message + final_answer`
- 通过 `round_no` 标识该 `session_id` 下的第几轮问答
- 供 `AutoAgentExecuteStrategy` 在新一轮开始时加载最近 5 轮历史
- 供 `Step4LogExecutionSummaryNode` 在本轮结束后写入最终问答记忆

## 2. 表设计

表名：

- `agent_session_memory`

字段说明：

- `id`：主键
- `session_id`：会话 ID
- `round_no`：该会话下的用户问答轮次
- `user_message`：用户本轮原始输入
- `final_answer`：Node4 最终汇总回答
- `create_time`：创建时间
- `update_time`：更新时间

索引设计：

- 主键：`id`
- 唯一索引：`uk_session_round(session_id, round_no)`
- 普通索引：`idx_session_id(session_id)`

## 3. 建表 SQL

```sql
CREATE TABLE IF NOT EXISTS `agent_session_memory` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`   VARCHAR(64) NOT NULL COMMENT '会话ID',
    `round_no`     INT NOT NULL COMMENT '该session下的用户问答轮次',
    `user_message` TEXT COMMENT '用户本轮原始输入',
    `final_answer` TEXT COMMENT 'Agent最终回答(Node4 summary)',
    `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_round` (`session_id`, `round_no`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent会话多轮记忆表';
```

## 4. 初始化脚本追加位置

如果你使用项目内初始化脚本，建议将建表 SQL 追加到：

- [docs/dev-ops/mysql/sql/ai-agent-station-study.sql](/E:/javaProject/ai-agent-station-study/docs/dev-ops/mysql/sql/ai-agent-station-study.sql)

建议放在现有业务表定义区域末尾，保证数据库初始化时能一并创建。

## 5. 验证 SQL

### 5.1 查看某个 session 的全部历史

```sql
SELECT id, session_id, round_no, user_message, final_answer, create_time, update_time
FROM agent_session_memory
WHERE session_id = 'your-session-id'
ORDER BY round_no ASC;
```

### 5.2 查看某个 session 最近 5 轮

```sql
SELECT id, session_id, round_no, user_message, final_answer, create_time, update_time
FROM agent_session_memory
WHERE session_id = 'your-session-id'
ORDER BY round_no DESC
LIMIT 5;
```

### 5.3 查看某个 session 当前最大轮次

```sql
SELECT MAX(round_no) AS max_round_no
FROM agent_session_memory
WHERE session_id = 'your-session-id';
```

## 6. 测试插入 SQL

```sql
INSERT INTO agent_session_memory (
    session_id, round_no, user_message, final_answer, create_time, update_time
) VALUES (
    'session-test-001',
    1,
    '帮我总结一下这个项目的 AutoAgent 流程',
    '这个项目的 AutoAgent 分为 Node1 规划、Node2 执行、Node3 监督、Node4 总结四个阶段。',
    NOW(),
    NOW()
);
```

## 7. 回滚 SQL

如果需要回滚本次表结构变更，可执行：

```sql
DROP TABLE IF EXISTS `agent_session_memory`;
```

## 8. 代码对应关系

本表的读写链路如下：

- 读取入口：
  - [AutoAgentExecuteStrategy.java](/E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/AutoAgentExecuteStrategy.java)
  - 按 `sessionId` 查询最近 5 轮历史并写入 `DynamicContext`

- Node1 注入：
  - [Step1AnalyzerNode.java](/E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java)
  - 将 `sessionHistory` 注入 planning prompt 的 `context`

- 写入出口：
  - [Step4LogExecutionSummaryNode.java](/E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step4LogExecutionSummaryNode.java)
  - 使用 `sessionId + roundNo + userMessage + finalAnswer` 写库

- Repository：
  - [ISessionMemoryRepository.java](/E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/adapter/repository/ISessionMemoryRepository.java)
  - [SessionMemoryRepository.java](/E:/javaProject/ai-agent-station-study/ai-agent-station-study-infrastructure/src/main/java/cn/bugstack/ai/infrastructure/adapter/repository/SessionMemoryRepository.java)

- MyBatis Mapper：
  - [agent_session_memory_mapper.xml](/E:/javaProject/ai-agent-station-study/ai-agent-station-study-app/src/main/resources/mybatis/mapper/agent_session_memory_mapper.xml)

## 9. 使用说明

当用户在同一个 `sessionId` 下连续提问时：

1. 第一次提问结束后，Node4 将最终回答写入 `agent_session_memory`
2. 第二次提问开始时，系统会读取该 `sessionId` 最近 5 轮记录
3. 这些记录会被格式化为 `Session History` 注入 Node1
4. Node1 由此具备跨轮用户问答记忆

第一版设计说明：

- 默认只注入最近 5 轮
- 只注入 Node1
- 不写入 Node1/Node2/Node3 中间过程
- 不替代现有 `MemoryAdvisor`
