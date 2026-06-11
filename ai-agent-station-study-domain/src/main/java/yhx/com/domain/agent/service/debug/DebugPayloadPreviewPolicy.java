package yhx.com.domain.agent.service.debug;

import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;

public class DebugPayloadPreviewPolicy {

    private final int maxPreviewChars;
    private final boolean includeRawContent;

    public DebugPayloadPreviewPolicy(int maxPreviewChars, boolean includeRawContent) {
        this.maxPreviewChars = Math.max(0, maxPreviewChars);
        this.includeRawContent = includeRawContent;
    }

    public AgentPayloadEntity applyPreviewPolicy(AgentPayloadEntity payload) {
        if (payload == null) {
            return null;
        }
        String content = payload.getContent();
        String preview = payload.getPreview();
        if ((preview == null || preview.isBlank()) && content != null) {
            preview = content.substring(0, Math.min(maxPreviewChars, content.length()));
        }
        if (preview != null && preview.length() > maxPreviewChars) {
            preview = preview.substring(0, maxPreviewChars);
        }
        return AgentPayloadEntity.builder()
                .payloadId(payload.getPayloadId())
                .payloadType(payload.getPayloadType())
                .content(includeRawContent ? content : null)
                .contentSha256(payload.getContentSha256())
                .preview(preview)
                .createdAt(payload.getCreatedAt())
                .build();
    }

    public boolean isIncludeRawContent() {
        return includeRawContent;
    }
}

