package dev.novablaze.antidupe.core;

import java.time.Instant;

/**
 * A removed duplicate, kept so an admin can give it back.
 *
 * @param item the complete item as bytes (Paper's {@code ItemStack.serializeAsBytes()})
 * @param holder where the copy was found
 * @param culprit the player who made the copy, or null if unknown
 */
public record QuarantineEntry(
        long id, String serial, byte[] item, Holder holder, String culprit, String reason, Instant at) {}
