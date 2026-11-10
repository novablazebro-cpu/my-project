package dev.novablaze.antidupe.plugin.guards;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Talks to AuthMe without needing it at build time (reflection), so AntiDupe still runs on
 * servers without AuthMe.
 */
public final class AuthMeHook implements Listener {
    private final Object api;
    private final Method isAuthenticated;
    private final Plugin plugin;
    private volatile boolean warned;

    private AuthMeHook(Plugin plugin, Object api, Method isAuthenticated) {
        this.plugin = plugin;
        this.api = api;
        this.isAuthenticated = isAuthenticated;
    }

    public static Optional<AuthMeHook> connect(Plugin plugin, Consumer<Player> onLogin) {
        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authMe == null || !authMe.isEnabled()) return Optional.empty();
        try {
            ClassLoader cl = authMe.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, cl);
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method isAuth = apiClass.getMethod("isAuthenticated", Player.class);
            AuthMeHook hook = new AuthMeHook(plugin, api, isAuth);

            Class<? extends Event> loginEvent =
                    Class.forName("fr.xephi.authme.events.LoginEvent", true, cl).asSubclass(Event.class);
            Method getPlayer = loginEvent.getMethod("getPlayer");
            Bukkit.getPluginManager().registerEvent(loginEvent, hook, EventPriority.MONITOR, (l, ev) -> {
                if (!loginEvent.isInstance(ev)) return;
                try {
                    Player p = (Player) getPlayer.invoke(ev);
                    Bukkit.getScheduler().runTask(plugin, () -> onLogin.accept(p));
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Could not read AuthMe login event: " + ex);
                }
            }, plugin, false);
            plugin.getLogger().info("Connected to AuthMe: items are locked until players log in.");
            return Optional.of(hook);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("AuthMe found but its API did not match; login-gap guard is off. " + e);
            return Optional.empty();
        }
    }

    /** If AuthMe cannot be asked, the player is treated as logged in so nobody is locked out. */
    public boolean isLoggedIn(Player p) {
        try {
            return (boolean) isAuthenticated.invoke(api, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("AuthMe check failed; login-gap guard is not blocking. " + e);
            }
            return true;
        }
    }
}
