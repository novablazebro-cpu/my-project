package dev.novablaze.antidupe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegistryTest {
    @Test
    void snapshot_and_loadAll_round_trip() {
        Registry a = new Registry();
        a.record("s1", Holder.player("steve"));
        a.record("s2", Holder.container("world", 1, 2, 3));
        Registry b = new Registry();
        b.loadAll(a.snapshot());
        assertEquals(a.snapshot(), b.snapshot());
    }

    @Test
    void forget_removes() {
        Registry r = new Registry();
        r.record("s1", Holder.player("steve"));
        r.forget("s1");
        assertEquals(Optional.empty(), r.lastSeen("s1"));
    }
}
