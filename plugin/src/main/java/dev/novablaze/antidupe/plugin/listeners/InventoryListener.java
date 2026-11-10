package dev.novablaze.antidupe.plugin.listeners;

import dev.novablaze.antidupe.core.Holder;
import dev.novablaze.antidupe.plugin.Holders;
import dev.novablaze.antidupe.plugin.Scanner;
import dev.novablaze.antidupe.plugin.SerialService;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Watches items move between inventories. Every check runs one tick later, once the move has
 * finished, so the scanner sees where things really ended up.
 */
public final class InventoryListener implements Listener {
    private final Plugin plugin;
    private final SerialService serials;
    private final Scanner scanner;

    public InventoryListener(Plugin plugin, SerialService serials, Scanner scanner) {
        this.plugin = plugin;
        this.serials = serials;
        this.scanner = scanner;
    }

    /** Opening a chest checks everything in it, including inside shulker boxes. Nobody is blamed. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        Inventory inv = e.getInventory();
        Holders.of(inv).filter(h -> h.kind() != Holder.Kind.PLAYER).ifPresent(h ->
                later(() -> scanner.scan(h, inv, s -> null, true, Set.of())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !Tracking.tracked(p)) return;
        Set<String> moved = new HashSet<>();
        addSerial(moved, e.getCurrentItem());
        addSerial(moved, e.getCursor());
        if (e.getHotbarButton() >= 0) addSerial(moved, p.getInventory().getItem(e.getHotbarButton()));
        if (e.getClick() == ClickType.SWAP_OFFHAND) addSerial(moved, p.getInventory().getItemInOffHand());
        later(() -> scanPlayerAndTop(p, moved));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !Tracking.tracked(p)) return;
        Set<String> moved = new HashSet<>();
        addSerial(moved, e.getOldCursor());
        later(() -> scanPlayerAndTop(p, moved));
    }

    /**
     * Only the items the player actually moved are blamed on them. Anything else found wrong in
     * the same inventories is removed without punishing anyone.
     */
    private void scanPlayerAndTop(Player p, Set<String> moved) {
        if (!p.isOnline()) return;
        String name = p.getName().toLowerCase(Locale.ROOT);
        scanner.scan(Holder.player(name), p.getInventory(),
                s -> moved.contains(s) ? name : null, false, moved);
        Inventory top = p.getOpenInventory().getTopInventory();
        Holders.of(top).filter(h -> h.kind() != Holder.Kind.PLAYER).ifPresent(h ->
                scanner.scan(h, top, s -> moved.contains(s) ? name : null, false, moved));
    }

    /** Hoppers, droppers and similar moving an unstackable item: check where it landed. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        if (!serials.covers(e.getItem())) return;
        Inventory dest = e.getDestination();
        Set<String> moved = new HashSet<>();
        addSerial(moved, e.getItem());
        Holders.of(dest).ifPresent(h -> later(() -> scanner.scan(h, dest, s -> null, false, moved)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        ItemStack item = e.getItem().getItemStack();
        if (!serials.covers(item)) return;
        Inventory dest = e.getInventory();
        Set<String> moved = new HashSet<>();
        addSerial(moved, item);
        Holders.of(dest).ifPresent(h -> later(() -> scanner.scan(h, dest, s -> null, false, moved)));
    }

    /** Creative mode can clone items. Clones lose their serial so they never look like dupes. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent e) {
        ItemStack cursor = e.getCursor();
        if (serials.serial(cursor).isPresent() || serials.hasContents(cursor)) {
            serials.stripAll(cursor);
            e.setCursor(cursor);
        }
    }

    /**
     * Leaving creative: the player's items may include clones with copied serials, so all of them
     * get fresh serials before they are tracked again.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        boolean leavingCreative = p.getGameMode() == GameMode.CREATIVE
                || p.getGameMode() == GameMode.SPECTATOR;
        GameMode next = e.getNewGameMode();
        if (!leavingCreative || (next != GameMode.SURVIVAL && next != GameMode.ADVENTURE)) return;
        later(() -> {
            if (!p.isOnline()) return;
            restamp(p.getInventory());
            restamp(p.getEnderChest());
        });
    }

    private void restamp(Inventory inv) {
        ItemStack[] slots = inv.getContents();
        for (int i = 0; i < slots.length; i++) {
            ItemStack item = slots[i];
            if (item == null || (serials.serial(item).isEmpty() && !serials.hasContents(item))) continue;
            serials.stripAll(item);
            inv.setItem(i, item);
        }
    }

    private void addSerial(Set<String> into, ItemStack item) {
        serials.serial(item).ifPresent(into::add);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }
}
