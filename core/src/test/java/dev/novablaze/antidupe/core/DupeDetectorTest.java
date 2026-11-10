package dev.novablaze.antidupe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DupeDetectorTest {

    /** A pretend world: which holders are loaded, and which serials each one contains. */
    static final class FakeProbe implements HolderProbe {
        final Set<Holder> unloaded = new HashSet<>();
        final Map<Holder, Set<String>> contents = new HashMap<>();

        void put(Holder h, String serial) {
            contents.computeIfAbsent(h, k -> new HashSet<>()).add(serial);
        }

        void take(Holder h, String serial) {
            contents.getOrDefault(h, new HashSet<>()).remove(serial);
        }

        @Override public boolean isLoaded(Holder h) { return !unloaded.contains(h); }
        @Override public boolean holds(Holder h, String serial) {
            return contents.getOrDefault(h, Set.of()).contains(serial);
        }
    }

    static final Holder STEVE = Holder.player("Steve");
    static final Holder CHEST = Holder.container("world", 10, 64, 10);

    Registry registry;
    FakeProbe probe;
    DupeDetector detector;

    @BeforeEach
    void setUp() {
        registry = new Registry();
        probe = new FakeProbe();
        detector = new DupeDetector(registry, probe);
    }

    @Test
    void first_sighting_is_new() {
        assertEquals(new Verdict.New(), detector.check(new Sighting("s1", STEVE, "steve")));
        assertEquals(Optional.of(STEVE), registry.lastSeen("s1"));
    }

    @Test
    void moving_within_same_holder_is_same() {
        registry.record("s1", STEVE);
        probe.put(STEVE, "s1");
        assertEquals(new Verdict.Same(), detector.check(new Sighting("s1", STEVE, "steve")));
    }

    @Test
    void old_holder_unloaded_defers_and_keeps_record() {
        registry.record("s1", CHEST);
        probe.put(CHEST, "s1");
        probe.unloaded.add(CHEST);
        assertEquals(new Verdict.Deferred(CHEST), detector.check(new Sighting("s1", STEVE, "steve")));
        assertEquals(Optional.of(CHEST), registry.lastSeen("s1"));
    }

    @Test
    void old_holder_still_has_it_is_duplicate_with_actor_as_culprit() {
        registry.record("s1", CHEST);
        probe.put(CHEST, "s1");
        probe.put(STEVE, "s1");
        assertEquals(new Verdict.Duplicate(CHEST, "steve"),
                detector.check(new Sighting("s1", STEVE, "steve")));
        assertEquals(Optional.of(CHEST), registry.lastSeen("s1"), "the original keeps its record");
    }

    @Test
    void duplicate_without_actor_has_null_culprit() {
        registry.record("s1", CHEST);
        probe.put(CHEST, "s1");
        assertEquals(new Verdict.Duplicate(CHEST, null), detector.check(new Sighting("s1", STEVE, null)));
    }

    @Test
    void old_holder_no_longer_has_it_is_moved() {
        registry.record("s1", CHEST);
        probe.put(STEVE, "s1");
        assertEquals(new Verdict.Moved(CHEST), detector.check(new Sighting("s1", STEVE, "steve")));
        assertEquals(Optional.of(STEVE), registry.lastSeen("s1"));
    }

    @Test
    void item_handed_back_and_forth_is_never_flagged() {
        Holder alex = Holder.player("Alex");
        detector.check(new Sighting("s1", STEVE, "steve"));
        probe.put(STEVE, "s1");
        for (int i = 0; i < 5; i++) {
            probe.take(STEVE, "s1"); probe.put(alex, "s1");
            assertInstanceOf(Verdict.Moved.class, detector.check(new Sighting("s1", alex, "alex")));
            probe.take(alex, "s1"); probe.put(STEVE, "s1");
            assertInstanceOf(Verdict.Moved.class, detector.check(new Sighting("s1", STEVE, "steve")));
        }
    }

    @Test
    void internal_duplicates_returns_later_slots() {
        assertEquals(List.of(3, 4), detector.internalDuplicates(Arrays.asList("a", null, "b", "a", "a")));
        assertEquals(List.of(), detector.internalDuplicates(Arrays.asList("a", "b", null, null)));
    }
}
