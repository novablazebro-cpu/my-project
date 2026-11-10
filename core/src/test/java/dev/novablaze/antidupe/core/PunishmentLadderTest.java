package dev.novablaze.antidupe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PunishmentLadderTest {

    /** A clock the test can move forward. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    TestClock clock;
    PunishmentLadder ladder;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        ladder = new PunishmentLadder(PunishmentLadder.defaultSteps(),
                PunishmentLadder.DEFAULT_EXPIRY, new InMemoryOffenceStore(), clock);
    }

    /** Each call is a separate incident, 1 minute apart. */
    Action next(String name) {
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        return ladder.punish(name).orElseThrow();
    }

    @Test
    void warn_then_kick_then_two_hour_ban_then_ban_again() {
        assertEquals(new Action.Warn(), next("steve"));
        assertEquals(new Action.Kick(), next("steve"));
        assertEquals(new Action.Ban(Duration.ofHours(2)), next("steve"));
        assertEquals(new Action.Ban(Duration.ofHours(2)), next("steve"));
        assertEquals(4, ladder.activeOffences("steve"));
    }

    @Test
    void names_are_case_insensitive() {
        next("Steve");
        assertEquals(new Action.Kick(), next("STEVE"));
    }

    @Test
    void offence_older_than_thirty_days_is_forgotten() {
        next("steve");
        clock.now = clock.now.plus(Duration.ofDays(31));
        assertEquals(0, ladder.activeOffences("steve"));
        assertEquals(new Action.Warn(), next("steve"));
    }

    @Test
    void pardon_resets_to_warning() {
        next("steve");
        next("steve");
        ladder.pardon("steve");
        assertEquals(new Action.Warn(), next("steve"));
    }

    @Test
    void players_are_counted_separately() {
        next("steve");
        assertEquals(new Action.Warn(), next("alex"));
    }

    @Test
    void empty_ladder_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new PunishmentLadder(
                List.of(), Duration.ofDays(30), new InMemoryOffenceStore(), clock));
    }

    @Test
    void removals_within_ten_seconds_are_one_offence() {
        assertEquals(java.util.Optional.of(new Action.Warn()), ladder.punish("steve"));
        clock.now = clock.now.plusSeconds(3);
        assertEquals(java.util.Optional.empty(), ladder.punish("steve"), "same incident");
        assertEquals(1, ladder.activeOffences("steve"));
        clock.now = clock.now.plusSeconds(11);
        assertEquals(java.util.Optional.of(new Action.Kick()), ladder.punish("steve"));
    }

    @Test
    void simultaneous_removals_from_many_threads_give_one_warning() throws Exception {
        int threads = 16;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<java.util.Optional<Action>>> results = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> { go.await(); return ladder.punish("steve"); }));
        }
        go.countDown();
        int actions = 0;
        for (var r : results) {
            java.util.Optional<Action> a = r.get();
            if (a.isPresent()) {
                actions++;
                assertEquals(new Action.Warn(), a.get());
            }
        }
        pool.shutdown();
        assertEquals(1, actions);
        assertEquals(1, ladder.activeOffences("steve"));
    }
}
