package dev.novablaze.antidupe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {
    static final Instant T0 = Instant.parse("2026-11-10T12:00:00Z");

    @TempDir Path dir;

    Storage open() throws Exception {
        return new Storage(dir.resolve("antidupe.db"));
    }

    @Test
    void registry_survives_reopen() throws Exception {
        Map<String, Holder> entries = Map.of(
                "s1", Holder.player("steve"),
                "s2", Holder.inShulker("box"));
        try (Storage s = open()) { s.saveRegistry(entries); }
        try (Storage s = open()) { assertEquals(entries, s.loadRegistry()); }
    }

    @Test
    void save_registry_replaces_old_entries() throws Exception {
        try (Storage s = open()) {
            s.saveRegistry(Map.of("old", Holder.player("steve")));
            s.saveRegistry(Map.of("new", Holder.player("alex")));
            assertEquals(Map.of("new", Holder.player("alex")), s.loadRegistry());
        }
    }

    @Test
    void quarantine_keeps_exact_item_bytes() throws Exception {
        byte[] item = {1, 2, 3, -128, 127, 0};
        try (Storage s = open()) {
            long id = s.quarantine("s1", item, Holder.player("alex"), "steve", "duplicate", T0);
            QuarantineEntry e = s.quarantined(id).orElseThrow();
            assertArrayEquals(item, e.item());
            assertEquals("s1", e.serial());
            assertEquals(Holder.player("alex"), e.holder());
            assertEquals("steve", e.culprit());
            assertEquals(T0, e.at());
        }
    }

    @Test
    void quarantined_for_matches_culprit_or_holder() throws Exception {
        try (Storage s = open()) {
            s.quarantine("a", new byte[] {1}, Holder.player("alex"), "steve", "dup", T0);
            s.quarantine("b", new byte[] {2}, Holder.player("bob"), null, "dup", T0);
            s.quarantine("c", new byte[] {3}, Holder.container("world", 0, 0, 0), "carl", "dup", T0);
            assertEquals(1, s.quarantinedFor("steve").size());
            assertEquals(1, s.quarantinedFor("alex").size());
            assertEquals(1, s.quarantinedFor("Bob").size());
            assertEquals(0, s.quarantinedFor("nobody").size());
        }
    }

    @Test
    void release_removes_entry() throws Exception {
        try (Storage s = open()) {
            long id = s.quarantine("a", new byte[] {1}, Holder.player("alex"), null, "dup", T0);
            s.release(id);
            assertEquals(Optional.empty(), s.quarantined(id));
        }
    }

    @Test
    void purge_removes_only_older_entries() throws Exception {
        try (Storage s = open()) {
            long old = s.quarantine("a", new byte[] {1}, Holder.player("alex"), null, "dup", T0);
            long recent = s.quarantine("b", new byte[] {2}, Holder.player("alex"), null, "dup",
                    T0.plus(Duration.ofDays(40)));
            assertEquals(1, s.purgeQuarantineBefore(T0.plus(Duration.ofDays(10))));
            assertTrue(s.quarantined(old).isEmpty());
            assertTrue(s.quarantined(recent).isPresent());
        }
    }

    @Test
    void offences_survive_reopen_and_clear() throws Exception {
        try (Storage s = open()) {
            s.addOffence("steve", T0);
            s.addOffence("steve", T0.plusSeconds(60));
        }
        try (Storage s = open()) {
            assertEquals(2, s.offences("steve").size());
            s.clear("steve");
            assertEquals(0, s.offences("steve").size());
        }
    }

    @Test
    void ban_active_until_expiry_then_gone() throws Exception {
        Instant until = T0.plus(Duration.ofHours(2));
        try (Storage s = open()) {
            s.ban("steve", until);
            assertEquals(Optional.of(until), s.bannedUntil("steve", T0));
            assertEquals(Optional.empty(), s.bannedUntil("steve", until));
            assertEquals(Optional.empty(), s.bannedUntil("alex", T0));
        }
    }

    @Test
    void unban_clears() throws Exception {
        try (Storage s = open()) {
            s.ban("steve", T0.plus(Duration.ofHours(2)));
            s.unban("steve");
            assertEquals(Optional.empty(), s.bannedUntil("steve", T0));
        }
    }

    @Test
    void ladder_works_on_top_of_storage() throws Exception {
        try (Storage s = open()) {
            PunishmentLadder ladder = new PunishmentLadder(PunishmentLadder.defaultSteps(),
                    PunishmentLadder.DEFAULT_EXPIRY, s, java.time.Clock.systemUTC());
            assertEquals(new Action.Warn(), ladder.punish("Steve"));
            assertEquals(new Action.Kick(), ladder.punish("steve"));
        }
    }
}
