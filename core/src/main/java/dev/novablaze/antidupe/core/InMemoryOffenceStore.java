package dev.novablaze.antidupe.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Offence store that forgets everything on restart. Used in tests. */
public final class InMemoryOffenceStore implements OffenceStore {
    private final Map<String, List<Instant>> byName = new ConcurrentHashMap<>();

    @Override
    public List<Instant> offences(String name) {
        return List.copyOf(byName.getOrDefault(name, List.of()));
    }

    @Override
    public void addOffence(String name, Instant at) {
        byName.computeIfAbsent(name, k -> new ArrayList<>()).add(at);
    }

    @Override
    public void clear(String name) {
        byName.remove(name);
    }
}
