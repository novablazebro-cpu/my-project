package dev.novablaze.antidupe.plugin.listeners;

import dev.novablaze.antidupe.core.Holder;
import dev.novablaze.antidupe.core.Registry;
import dev.novablaze.antidupe.plugin.Scanner;
import dev.novablaze.antidupe.plugin.SerialService;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Items dropped, picked up, and destroyed. */
public final class ItemEntityListener implements Listener {
    private final Plugin plugin;
    private final SerialService serials;
    private final Scanner scanner;
    private final Registry registry;

    public ItemEntityListener(Plugin plugin, SerialService serials, Scanner scanner, Registry registry) {
        this.plugin = plugin;
        this.serials = serials;
        this.scanner = scanner;
        this.registry = registry;
    }

    /**
     * A drop is recorded on the ground. This is what catches the classic relog dupe: drop an item,
     * disconnect before the server saves, and rejoin with it still in your inventory.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        Item entity = e.getItemDrop();
        if (!Tracking.tracked(p) || !serials.covers(entity.getItemStack())) return;
        String name = p.getName().toLowerCase(Locale.ROOT);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid()) scanner.scanEntity(entity, s -> name);
        });
    }

    /** Picking up is never blamed on the player: they may have been handed a copy someone else made. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !Tracking.tracked(p)) return;
        ItemStack item = e.getItem().getItemStack();
        if (!serials.covers(item)) return;
        Set<String> picked = serials.serial(item).map(Set::of).orElse(Set.of());
        String name = p.getName().toLowerCase(Locale.ROOT);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) scanner.scan(Holder.player(name), p.getInventory(), s -> null, false, picked);
        });
    }

    /** An item burnt, fell into the void or despawned: forget it so the registry stays small. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent e) {
        if (!(e.getEntity() instanceof Item item)) return;
        switch (e.getCause()) {
            case DEATH, DESPAWN, OUT_OF_WORLD -> forget(item.getItemStack());
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(PlayerItemBreakEvent e) {
        forget(e.getBrokenItem());
    }

    /** Potions drunk and food eaten are gone. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        forget(e.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        forget(e.getPlayer().getInventory().getItem(e.getHand()));
    }

    private void forget(ItemStack item) {
        serials.serial(item).ifPresent(registry::forget);
        if (serials.hasContents(item)) {
            for (ItemStack inner : serials.contents(item)) serials.serial(inner).ifPresent(registry::forget);
        }
    }
}
