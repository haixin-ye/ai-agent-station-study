package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMemoryEntity {

    private Long id;

    private String sessionId;

    private Integer roundNo;

    private String userMessage;

    private String finalAnswer;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
