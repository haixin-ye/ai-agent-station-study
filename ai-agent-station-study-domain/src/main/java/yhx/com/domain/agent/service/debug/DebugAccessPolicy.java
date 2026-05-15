package yhx.com.domain.agent.service.debug;

public class DebugAccessPolicy {

    private final boolean debugApiEnabled;
    private final boolean debugSseEnabled;
    private final boolean payloadPreviewEnabled;

    public DebugAccessPolicy(boolean debugApiEnabled, boolean debugSseEnabled, boolean payloadPreviewEnabled) {
        this.debugApiEnabled = debugApiEnabled;
        this.debugSseEnabled = debugSseEnabled;
        this.payloadPreviewEnabled = payloadPreviewEnabled;
    }

    public void requireDebugApiEnabled() {
        if (!debugApiEnabled) {
            throw new IllegalStateException("Debug API is disabled.");
        }
    }

    public void requireDebugSseEnabled() {
        if (!debugSseEnabled) {
            throw new IllegalStateException("Debug SSE is disabled.");
        }
    }

    public void requirePayloadPreviewEnabled() {
        requireDebugApiEnabled();
        if (!payloadPreviewEnabled) {
            throw new IllegalStateException("Debug payload preview is disabled.");
        }
    }

    public boolean isDebugApiEnabled() {
        return debugApiEnabled;
    }

    public boolean isDebugSseEnabled() {
        return debugSseEnabled;
    }
}

