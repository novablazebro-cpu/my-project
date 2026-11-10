package dev.novablaze.antidupe.core;

import java.time.Duration;

/** What happens to a player who made a duplicate. */
public sealed interface Action {
    record Warn() implements Action {}

    record Kick() implements Action {}

    record Ban(Duration length) implements Action {}
}
