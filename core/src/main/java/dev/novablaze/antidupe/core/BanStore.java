package dev.novablaze.antidupe.core;

import java.time.Instant;
import java.util.Optional;

/** Temporary bans issued by AntiDupe. Names are lower-cased. */
public interface BanStore {
    void ban(String name, Instant until);

    /** When the ban ends, or empty if the player is not banned at {@code now}. */
    Optional<Instant> bannedUntil(String name, Instant now);

    void unban(String name);
}
