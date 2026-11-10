package dev.novablaze.antidupe.plugin.commands;

import dev.novablaze.antidupe.core.Holder;
import dev.novablaze.antidupe.core.PunishmentLadder;
import dev.novablaze.antidupe.core.QuarantineEntry;
import dev.novablaze.antidupe.core.Registry;
import dev.novablaze.antidupe.core.Storage;
import dev.novablaze.antidupe.plugin.SerialService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** /antidupe history|inspect|pardon|restore|reload. Needs antidupe.admin. */
public final class AntiDupeCommand implements BasicCommand {
    private static final List<String> SUBS = List.of("history", "inspect", "pardon", "restore", "reload");

    private final Plugin plugin;
    private final Storage storage;
    private final Registry registry;
    private final SerialService serials;
    private final Supplier<PunishmentLadder> ladder;
    private final Runnable reload;

    public AntiDupeCommand(Plugin plugin, Storage storage, Registry registry, SerialService serials,
            Supplier<PunishmentLadder> ladder, Runnable reload) {
        this.plugin = plugin;
        this.storage = storage;
        this.registry = registry;
        this.serials = serials;
        this.ladder = ladder;
        this.reload = reload;
    }

    @Override
    public String permission() {
        return "antidupe.admin";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender to = source.getSender();
        if (args.length == 0) {
            usage(to);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "history" -> needName(to, args, this::history);
            case "pardon" -> needName(to, args, this::pardon);
            case "inspect" -> inspect(to);
            case "restore" -> restore(to, args);
            case "reload" -> {
                reload.run();
                say(to, "Config reloaded.", NamedTextColor.GREEN);
            }
            default -> usage(to);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            String start = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(start)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("pardon"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }

    private void usage(CommandSender to) {
        say(to, "/antidupe history <player> | inspect | pardon <player> | restore <id> [player] | reload",
                NamedTextColor.YELLOW);
    }

    private interface NameAction { void run(CommandSender to, String name); }

    private void needName(CommandSender to, String[] args, NameAction action) {
        if (args.length < 2) {
            usage(to);
            return;
        }
        action.run(to, args[1].toLowerCase(Locale.ROOT));
    }

    private void history(CommandSender to, String name) {
        async(() -> {
            int offences = ladder.get().activeOffences(name);
            Optional<Instant> ban = storage.bannedUntil(name, Instant.now());
            List<QuarantineEntry> items = storage.quarantinedFor(name);
            sync(() -> {
                say(to, name + ": " + offences + " active offence(s)"
                        + ban.map(u -> ", banned until " + u).orElse(""), NamedTextColor.GOLD);
                if (items.isEmpty()) say(to, "No quarantined items.", NamedTextColor.GRAY);
                for (QuarantineEntry q : items) {
                    String item = ItemStack.deserializeBytes(q.item()).getType().getKey().getKey();
                    say(to, "#" + q.id() + " " + item + " at " + q.at() + " from " + q.holder().encode()
                            + " culprit=" + (q.culprit() == null ? "none" : q.culprit()), NamedTextColor.GRAY);
                }
            });
        });
    }

    private void pardon(CommandSender to, String name) {
        async(() -> {
            ladder.get().pardon(name);
            storage.unban(name);
            sync(() -> say(to, "Pardoned " + name + ": offences cleared and any ban lifted.", NamedTextColor.GREEN));
        });
    }

    private void inspect(CommandSender to) {
        if (!(to instanceof Player p)) {
            say(to, "Only players can inspect the item in their hand.", NamedTextColor.RED);
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        Optional<String> serial = serials.serial(hand);
        if (serial.isEmpty()) {
            say(to, serials.covers(hand) ? "No serial yet (it gets one on the next check)."
                    : "This item is not tracked (it can stack, or it is excluded).", NamedTextColor.GRAY);
            return;
        }
        say(to, "Serial " + serial.get() + ", last seen: "
                + registry.lastSeen(serial.get()).map(Holder::encode).orElse("unknown"), NamedTextColor.GOLD);
    }

    /** Gives a quarantined item back with fresh serials, so it does not count as a copy again. */
    private void restore(CommandSender to, String[] args) {
        if (args.length < 2) {
            usage(to);
            return;
        }
        long id;
        try {
            id = Long.parseLong(args[1].replace("#", ""));
        } catch (NumberFormatException e) {
            say(to, "Not a quarantine id: " + args[1], NamedTextColor.RED);
            return;
        }
        String targetName = args.length >= 3 ? args[2] : (to instanceof Player p ? p.getName() : null);
        if (targetName == null) {
            say(to, "From the console, name the player: /antidupe restore <id> <player>", NamedTextColor.RED);
            return;
        }
        async(() -> {
            Optional<QuarantineEntry> entry = storage.quarantined(id);
            sync(() -> {
                if (entry.isEmpty()) {
                    say(to, "No quarantined item #" + id, NamedTextColor.RED);
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    say(to, targetName + " is not online.", NamedTextColor.RED);
                    return;
                }
                ItemStack item = ItemStack.deserializeBytes(entry.get().item());
                serials.stripAll(item);
                target.getInventory().addItem(item).values()
                        .forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
                async(() -> storage.release(id));
                say(to, "Restored #" + id + " to " + target.getName() + ".", NamedTextColor.GREEN);
            });
        });
    }

    private void say(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }

    private void async(Runnable r) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }
}
