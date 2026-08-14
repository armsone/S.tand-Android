package com.armsone.stand.boyiso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one live participant per stable source ID while collecting all currently live transports.
 * An already-seen event must not refresh a participant: mesh echoes are common and otherwise can
 * keep a departed source visible indefinitely.
 */
final class ParticipantTracker {
    private final EventDeduplicator deduplicator = new EventDeduplicator();
    private final Map<String, Long> sourceLastSeen = new ConcurrentHashMap<>();
    private final Map<String, BoyisoEvent> sourceLatest = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sourcePaths = new ConcurrentHashMap<>();

    boolean recordIfNew(BoyisoEvent event, String path, long nowMillis) {
        if (!deduplicator.accept(event.id, nowMillis)) return false;
        sourceLastSeen.put(event.sourceId, nowMillis);
        sourceLatest.put(event.sourceId, event);
        sourcePaths.computeIfAbsent(event.sourceId, ignored -> ConcurrentHashMap.newKeySet()).add(path);
        return true;
    }

    boolean expireStale(long nowMillis, long staleMillis) {
        return sourceLastSeen.entrySet().removeIf(entry -> {
            boolean stale = nowMillis - entry.getValue() > staleMillis;
            if (stale) {
                sourceLatest.remove(entry.getKey());
                sourcePaths.remove(entry.getKey());
            }
            return stale;
        });
    }

    int size() {
        return sourceLastSeen.size();
    }

    List<BoyisoEvent> latestEvents() {
        return new ArrayList<>(sourceLatest.values());
    }

    Set<String> pathsFor(String sourceId) {
        return sourcePaths.getOrDefault(sourceId, Collections.emptySet());
    }

    long lastSeenFor(String sourceId) {
        return sourceLastSeen.getOrDefault(sourceId, 0L);
    }

    void clear() {
        sourceLastSeen.clear();
        sourceLatest.clear();
        sourcePaths.clear();
    }
}
