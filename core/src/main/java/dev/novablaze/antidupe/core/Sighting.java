package dev.novablaze.antidupe.core;

/**
 * "Serial X was just seen in holder Y."
 *
 * @param actor lower-cased name of the player whose action produced this sighting (their click,
 *     drop, pickup or join), or {@code null} when nobody caused it (for example a hopper). Only an
 *     actor can be punished.
 */
public record Sighting(String serial, Holder at, String actor) {}
