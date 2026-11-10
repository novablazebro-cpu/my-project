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

    @Test
    void warn_then_kick_then_two_hour_ban_then_ban_again() {
        assertEquals(new Action.Warn(), ladder.punish("steve"));
        assertEquals(new Action.Kick(), ladder.punish("steve"));
        assertEquals(new Action.Ban(Duration.ofHours(2)), ladder.punish("steve"));
        assertEquals(new Action.Ban(Duration.ofHours(2)), ladder.punish("steve"));
        assertEquals(4, ladder.activeOffences("steve"));
    }

    @Test
    void names_are_case_insensitive() {
        ladder.punish("Steve");
        assertEquals(new Action.Kick(), ladder.punish("STEVE"));
    }

    @Test
    void offence_older_than_thirty_days_is_forgotten() {
        ladder.punish("steve");
        clock.now = clock.now.plus(Duration.ofDays(31));
        assertEquals(0, ladder.activeOffences("steve"));
        assertEquals(new Action.Warn(), ladder.punish("steve"));
    }

    @Test
    void pardon_resets_to_warning() {
        ladder.punish("steve");
        ladder.punish("steve");
        ladder.pardon("steve");
        assertEquals(new Action.Warn(), ladder.punish("steve"));
    }

    @Test
    void players_are_counted_separately() {
        ladder.punish("steve");
        assertEquals(new Action.Warn(), ladder.punish("alex"));
    }

    @Test
    void empty_ladder_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new PunishmentLadder(
                List.of(), Duration.ofDays(30), new InMemoryOffenceStore(), clock));
    }
}
