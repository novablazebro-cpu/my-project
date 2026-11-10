package dev.novablaze.antidupe.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Counts a player's recent offences and picks the punishment for the next one: by default a
 * warning, then a kick, then a 2-hour ban for every offence after that. Offences older than the
 * expiry no longer count.
 */
public final class PunishmentLadder {
    public static final Duration DEFAULT_EXPIRY = Duration.ofDays(30);

    private final List<Action> steps;
    private final Duration expiry;
    private final OffenceStore store;
    private final Clock clock;

    public PunishmentLadder(List<Action> steps, Duration expiry, OffenceStore store, Clock clock) {
        if (steps.isEmpty()) throw new IllegalArgumentException("The ladder needs at least one step");
        this.steps = List.copyOf(steps);
        this.expiry = expiry;
        this.store = store;
        this.clock = clock;
    }

    public static List<Action> defaultSteps() {
        return List.of(new Action.Warn(), new Action.Kick(), new Action.Ban(Duration.ofHours(2)));
    }

    /** Records an offence now and returns what should happen to the player. */
    public Action punish(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        store.addOffence(key, clock.instant());
        int count = activeOffences(key);
        return steps.get(Math.min(count, steps.size()) - 1);
    }

    public int activeOffences(String name) {
        Instant cutoff = clock.instant().minus(expiry);
        return (int) store.offences(name.toLowerCase(Locale.ROOT)).stream()
                .filter(at -> at.isAfter(cutoff))
                .count();
    }

    public void pardon(String name) {
        store.clear(name.toLowerCase(Locale.ROOT));
    }
}
