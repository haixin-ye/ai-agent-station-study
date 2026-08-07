package yhx.com.trigger.http.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Set;

@Component
public class SseEmitterRegistry {

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Set<String> activeStreamWorkers = ConcurrentHashMap.newKeySet();

    public SseEmitter open(String streamKey, Long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs == null ? 300_000L : timeoutMs);
        emitters.computeIfAbsent(streamKey, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(streamKey, emitter));
        emitter.onTimeout(() -> remove(streamKey, emitter));
        emitter.onError(error -> remove(streamKey, emitter));
        return emitter;
    }

    public boolean send(String streamKey, String eventName, String eventId, Object payload) {
        List<SseEmitter> streamEmitters = emitters.get(streamKey);
        if (streamEmitters == null || streamEmitters.isEmpty()) {
            return false;
        }
        boolean delivered = false;
        for (SseEmitter emitter : streamEmitters) {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .name(eventName)
                        .data(payload);
                if (eventId != null) {
                    event.id(eventId);
                }
                emitter.send(event);
                delivered = true;
            } catch (Exception ex) {
                remove(streamKey, emitter);
                safeComplete(emitter);
            }
        }
        return delivered && hasEmitters(streamKey);
    }

    public void complete(String streamKey) {
        List<SseEmitter> streamEmitters = emitters.remove(streamKey);
        if (streamEmitters == null) {
            return;
        }
        streamEmitters.forEach(this::safeComplete);
    }

    public void completeWithError(String streamKey, Throwable error) {
        List<SseEmitter> streamEmitters = emitters.remove(streamKey);
        if (streamEmitters == null) {
            return;
        }
        streamEmitters.forEach(emitter -> {
            try {
                emitter.completeWithError(error);
            } catch (Exception ignored) {
                safeComplete(emitter);
            }
        });
    }

    public boolean hasEmitters(String streamKey) {
        List<SseEmitter> streamEmitters = emitters.get(streamKey);
        return streamEmitters != null && !streamEmitters.isEmpty();
    }

    /**
     * A stream key owns one database polling worker, even when the browser
     * reconnects or several studio windows observe the same run. The worker
     * broadcasts each event to every emitter registered under that key.
     */
    public boolean tryAcquireStreamWorker(String streamKey) {
        return activeStreamWorkers.add(streamKey);
    }

    public void releaseStreamWorker(String streamKey) {
        activeStreamWorkers.remove(streamKey);
    }

    private void remove(String streamKey, SseEmitter emitter) {
        List<SseEmitter> streamEmitters = emitters.get(streamKey);
        if (streamEmitters == null) {
            return;
        }
        streamEmitters.remove(emitter);
        if (streamEmitters.isEmpty()) {
            emitters.remove(streamKey);
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // The client may already have disconnected.
        }
    }
}
