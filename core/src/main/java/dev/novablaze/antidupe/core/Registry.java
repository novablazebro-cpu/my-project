package dev.novablaze.antidupe.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Remembers, for each serial, the last holder it was seen in. Safe to use from several threads. */
public final class Registry {
    private final ConcurrentHashMap<String, Holder> lastSeen = new ConcurrentHashMap<>();

    public Optional<Holder> lastSeen(String serial) {
        return Optional.ofNullable(lastSeen.get(serial));
    }

    public void record(String serial, Holder at) {
        lastSeen.put(serial, at);
    }

    public void forget(String serial) {
        lastSeen.remove(serial);
    }

    /** A copy, so it can be saved on another thread while the game keeps changing the live map. */
    public Map<String, Holder> snapshot() {
        return Map.copyOf(lastSeen);
    }

    public void loadAll(Map<String, Holder> entries) {
        lastSeen.putAll(entries);
    }
}
