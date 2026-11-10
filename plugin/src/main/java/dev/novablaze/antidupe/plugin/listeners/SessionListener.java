package dev.novablaze.antidupe.plugin.listeners;

import dev.novablaze.antidupe.core.Holder;
import dev.novablaze.antidupe.core.Sighting;
import dev.novablaze.antidupe.core.Storage;
import dev.novablaze.antidupe.plugin.Holders;
import dev.novablaze.antidupe.plugin.Scanner;
import dev.novablaze.antidupe.plugin.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Joining and leaving: bans, second logins, the join check, and the relog "suspected" alert. */
public final class SessionListener implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final Storage storage;
    private final Scanner scanner;
    private final Supplier<Settings> settings;
    private final Set<String> online = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<Material, Integer>> countsAtQuit = new ConcurrentHashMap<>();

    public SessionListener(Plugin plugin, Storage storage, Scanner scanner, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.scanner = scanner;
        this.settings = settings;
        for (Player p : Bukkit.getOnlinePlayers()) online.add(key(p.getName()));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Runs before the player is let in, and before AuthMe: banned players never reach the login
     * gap, and a second connection under a name already online is refused instead of kicking the
     * first one (kicking the first one mid-save is a classic relog dupe).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        Settings s = settings.get();
        String name = key(e.getName());
        Optional<Instant> until = storage.bannedUntil(name, Instant.now());
        if (until.isPresent()) {
            Duration left = Duration.between(Instant.now(), until.get());
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, MM.deserialize(s.messages().banned(),
                    Placeholder.unparsed("length", Settings.describe(left.plusMinutes(1)))));
            return;
        }
        if (s.refuseSecondLogin() && online.contains(name)) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, MM.deserialize(s.messages().secondLogin()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        online.add(key(p.getName()));
        Bukkit.getScheduler().runTask(plugin, () -> joinCheck(p));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        online.remove(key(p.getName()));
        if (Tracking.tracked(p)) countsAtQuit.put(key(p.getName()), stackableCounts(p));
    }

    /**
     * Checks everything the player brings in. Items they carry are blamed on them if the original
     * is still somewhere else, because a copy that appears in your inventory as you join is what a
     * relog dupe looks like. Also called again when AuthMe login completes.
     */
    public void joinCheck(Player p) {
        if (!p.isOnline() || !Tracking.tracked(p)) return;
        String name = key(p.getName());
        scanner.scan(Holder.player(name), p.getInventory(), s -> name, true, Set.of());
        scanner.scan(Holder.enderChest(name), p.getEnderChest(), s -> name, true, Set.of());
        for (Holder h : new Holder[] {Holder.player(name), Holder.enderChest(name)}) {
            for (Sighting waiting : scanner.takePending(h)) recheck(waiting);
        }
        suspectCheck(p, name);
    }

    /** A copy was seen elsewhere while this player was offline: look at that place again now. */
    private void recheck(Sighting s) {
        Set<String> only = Set.of(s.serial());
        if (s.at().kind() == Holder.Kind.GROUND) {
            Holders.entity(s.at()).filter(Item.class::isInstance).map(Item.class::cast)
                    .ifPresent(it -> scanner.scanEntity(it, x -> x.equals(s.serial()) ? s.actor() : null));
            return;
        }
        Holders.inventory(s.at()).ifPresent(inv ->
                scanner.scan(s.at(), inv, x -> x.equals(s.serial()) ? s.actor() : null, false, only));
    }

    /**
     * Stackable items cannot carry serials, so a relog dupe of, say, diamonds cannot be proven. If
     * a player rejoins with many more of something than they left with, staff are told. Nobody is
     * punished for this.
     */
    private void suspectCheck(Player p, String name) {
        Map<Material, Integer> before = countsAtQuit.remove(name);
        if (before == null) return;
        int threshold = settings.get().suspectThreshold();
        Map<Material, Integer> now = stackableCounts(p);
        StringBuilder gained = new StringBuilder();
        for (Map.Entry<Material, Integer> e : now.entrySet()) {
            int diff = e.getValue() - before.getOrDefault(e.getKey(), 0);
            if (diff >= threshold) gained.append(e.getKey().getKey().getKey()).append(" +").append(diff).append(' ');
        }
        if (gained.isEmpty()) return;
        String what = gained.toString().trim();
        plugin.getLogger().warning("Suspected relog dupe: " + name + " rejoined with " + what);
        Bukkit.broadcast(MM.deserialize(settings.get().messages().suspect(),
                Placeholder.unparsed("player", name), Placeholder.unparsed("items", what)), "antidupe.alerts");
    }

    private static Map<Material, Integer> stackableCounts(Player p) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && !item.isEmpty() && item.getMaxStackSize() > 1) {
                counts.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }
        return counts;
    }
}
