package dev.novablaze.antidupe.core;

import java.time.Instant;
import java.util.List;

/** Where offences are kept. Names are lower-cased by the caller. */
public interface OffenceStore {
    List<Instant> offences(String name);

    void addOffence(String name, Instant at);

    void clear(String name);
}
