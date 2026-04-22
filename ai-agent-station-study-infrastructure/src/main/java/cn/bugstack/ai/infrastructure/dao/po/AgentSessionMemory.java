package cn.bugstack.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionMemory {

    private Long id;

    private String sessionId;

    private Integer roundNo;

    private String userMessage;

    private String finalAnswer;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
