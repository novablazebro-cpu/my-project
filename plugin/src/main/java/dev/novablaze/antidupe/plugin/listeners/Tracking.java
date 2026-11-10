package dev.novablaze.antidupe.plugin.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

final class Tracking {
    private Tracking() {}

    /**
     * Creative and spectator players are not tracked: creative mode can clone items legitimately,
     * so their copies would look like dupes.
     */
    static boolean tracked(Player p) {
        GameMode m = p.getGameMode();
        return m == GameMode.SURVIVAL || m == GameMode.ADVENTURE;
    }
}
