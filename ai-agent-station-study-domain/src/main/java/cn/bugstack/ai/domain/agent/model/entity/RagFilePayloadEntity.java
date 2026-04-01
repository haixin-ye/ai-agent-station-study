package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG file payload.
 *
 * @author yhx
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagFilePayloadEntity {

    private String fileName;

    private byte[] content;

}
