package dev.novablaze.antidupe.plugin;

import dev.novablaze.antidupe.core.Action;
import dev.novablaze.antidupe.core.Holder;
import dev.novablaze.antidupe.core.PunishmentLadder;
import dev.novablaze.antidupe.core.Storage;
import java.time.Instant;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Acts on a proven duplicate: saves the copy to quarantine, removes it from the world, tells staff,
 * and punishes the culprit if there is one.
 */
public final class Enforcer {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final Storage storage;
    private final Supplier<Settings> settings;
    private final Supplier<PunishmentLadder> ladder;

    public Enforcer(Plugin plugin, Storage storage, Supplier<Settings> settings,
            Supplier<PunishmentLadder> ladder) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
        this.ladder = ladder;
    }

    /**
     * Must be called on the main thread, in the same tick the copy was found.
     *
     * @param copy the duplicate item as it is right now
     * @param at where the copy was found
     * @param original where the original is
     * @param culprit lower-cased name of the player who made the copy appear, or null
     * @param remove deletes the copy from the world
     */
    public void duplicate(String serial, ItemStack copy, Holder at, Holder original, String culprit,
            String reason, Runnable remove) {
        Settings s = settings.get();
        String itemName = copy.getType().getKey().getKey();
        String who = culprit == null ? "unknown" : culprit;
        plugin.getLogger().warning("Duplicate " + itemName + " serial=" + serial + " found at "
                + at.encode() + ", original at " + original.encode() + ", culprit=" + who
                + ", reason=" + reason + (s.enforce() ? "" : " [monitor mode: not removed]"));
        Bukkit.broadcast(msg(s.messages().alert(),
                Placeholder.unparsed("item", itemName),
                Placeholder.unparsed("player", who),
                Placeholder.unparsed("where", at.encode()),
                Placeholder.unparsed("action", s.enforce() ? "removed" : "NOT removed (monitor mode)")),
                "antidupe.alerts");
        if (!s.enforce()) return;

        byte[] bytes = copy.serializeAsBytes();
        remove.run();

        Player offender = culprit == null ? null : Bukkit.getPlayerExact(culprit);
        boolean punish = culprit != null && (offender == null || !offender.hasPermission("antidupe.bypass"));
        if (!punish) {
            Holders.player(at).filter(p -> at.kind() == Holder.Kind.PLAYER || at.kind() == Holder.Kind.ENDER_CHEST)
                    .ifPresent(p -> p.sendMessage(msg(s.messages().innocent(),
                            Placeholder.unparsed("item", itemName))));
        }
        Instant now = Instant.now();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            storage.quarantine(serial, bytes, at, culprit, reason, now);
            if (!punish) return;
            Action action = ladder.get().punish(culprit);
            if (action instanceof Action.Ban ban) storage.ban(culprit, now.plus(ban.length()));
            Bukkit.getScheduler().runTask(plugin, () -> apply(culprit, action, itemName));
        });
    }

    private void apply(String name, Action action, String itemName) {
        Settings.Messages m = settings.get().messages();
        Player p = Bukkit.getPlayerExact(name);
        plugin.getLogger().warning("Punishing " + name + ": " + action);
        if (p == null) return;
        TagResolver item = Placeholder.unparsed("item", itemName);
        switch (action) {
            case Action.Warn w -> p.sendMessage(msg(m.warn(), item));
            case Action.Kick k -> p.kick(msg(m.kick(), item));
            case Action.Ban b -> p.kick(msg(m.ban(), item,
                    Placeholder.unparsed("length", Settings.describe(b.length()))));
        }
    }

    static Component msg(String template, TagResolver... tags) {
        return MM.deserialize(template == null ? "" : template, tags);
    }
}
