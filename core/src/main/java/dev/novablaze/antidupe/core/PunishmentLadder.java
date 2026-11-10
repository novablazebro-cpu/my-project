package dev.novablaze.antidupe.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Counts a player's recent offences and picks the punishment for the next one: by default a
 * warning, then a kick, then a 2-hour ban for every offence after that. Offences older than the
 * expiry no longer count.
 *
 * <p>One dupe can leave many copies (a shulker box full of gear is the box plus every item in it),
 * all found within moments. Removals for the same player within {@link #INCIDENT_WINDOW} are one
 * offence, so a first dupe is a warning, not a ban.
 */
public final class PunishmentLadder {
    public static final Duration DEFAULT_EXPIRY = Duration.ofDays(30);
    public static final Duration INCIDENT_WINDOW = Duration.ofSeconds(10);

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

    /**
     * Records an offence now and returns what should happen to the player, or empty if this is
     * part of an offence already punished in the last {@link #INCIDENT_WINDOW}.
     *
     * <p>Synchronized: removals are handled on several background threads at once, and without
     * the lock two of them could both count the same offences and skip a step of the ladder.
     */
    public synchronized Optional<Action> punish(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        Instant incidentStart = now.minus(INCIDENT_WINDOW);
        boolean sameIncident = store.offences(key).stream().anyMatch(at -> at.isAfter(incidentStart));
        if (sameIncident) return Optional.empty();
        store.addOffence(key, now);
        int count = activeOffences(key);
        return Optional.of(steps.get(Math.min(count, steps.size()) - 1));
    }

    public int activeOffences(String name) {
        Instant cutoff = clock.instant().minus(expiry);
        return (int) store.offences(name.toLowerCase(Locale.ROOT)).stream()
                .filter(at -> at.isAfter(cutoff))
                .count();
    }

    public synchronized void pardon(String name) {
        store.clear(name.toLowerCase(Locale.ROOT));
    }
}
