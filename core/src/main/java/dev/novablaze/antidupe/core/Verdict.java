package dev.novablaze.antidupe.core;

/** What the detector concluded about one sighting. */
public sealed interface Verdict {
    /** Never seen before; now registered. */
    record New() implements Verdict {}

    /** Seen in the same holder as last time (moved between slots). */
    record Same() implements Verdict {}

    /** Left its old holder and arrived here. Registry updated. */
    record Moved(Holder from) implements Verdict {}

    /** The old holder cannot be checked right now, so nothing can be concluded. Not proof. */
    record Deferred(Holder from) implements Verdict {}

    /**
     * Proof of a duplicate: the original is still in {@code original}, so the sighted item is an
     * extra copy. {@code culprit} is the player who made it appear, or null if unknown.
     */
    record Duplicate(Holder original, String culprit) implements Verdict {}
}
