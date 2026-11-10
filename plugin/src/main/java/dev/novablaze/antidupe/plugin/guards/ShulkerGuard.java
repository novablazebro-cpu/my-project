package dev.novablaze.antidupe.plugin.guards;

import dev.novablaze.antidupe.core.Holder;
import dev.novablaze.antidupe.core.Registry;
import dev.novablaze.antidupe.plugin.Holders;
import dev.novablaze.antidupe.plugin.SerialService;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Shulker boxes: carries the serial from the item onto the placed block and back onto the item
 * when it is broken, and blocks breaking or moving a box that someone has open.
 */
public final class ShulkerGuard implements Listener {
    private final Plugin plugin;
    private final SerialService serials;
    private final Registry registry;

    public ShulkerGuard(Plugin plugin, SerialService serials, Registry registry) {
        this.plugin = plugin;
        this.serials = serials;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block block = e.getBlockPlaced();
        if (!Tag.SHULKER_BOXES.isTagged(block.getType())) return;
        String serial = serials.serial(e.getItemInHand()).orElse(null);
        if (serial == null) return;
        // One tick later the block entity is fully set up with the box's contents.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(block.getState() instanceof ShulkerBox box)) return;
            box.getPersistentDataContainer().set(serials.key(), PersistentDataType.STRING, serial);
            box.update(true, false);
            registry.record(serial, Holders.placedShulker(block));
        });
    }

    /** Copies the block's serial onto the dropped box, whether or not the game kept it. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        if (!(e.getBlockState() instanceof ShulkerBox box)) return;
        String serial = box.getPersistentDataContainer().get(serials.key(), PersistentDataType.STRING);
        if (serial == null) return;
        List<Item> boxes = e.getItems().stream()
                .filter(it -> Tag.SHULKER_BOXES.isTagged(it.getItemStack().getType())).toList();
        if (boxes.size() != 1) {
            plugin.getLogger().warning("Shulker box at " + Holders.placedShulker(e.getBlock()).encode()
                    + " dropped " + boxes.size() + " boxes; not copying its serial.");
            return;
        }
        Item drop = boxes.getFirst();
        ItemStack stack = drop.getItemStack();
        serials.setSerial(stack, serial);
        drop.setItemStack(stack);
        registry.record(serial, Holder.ground(drop.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isOpenShulker(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(this::isOpenShulker)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(this::isOpenShulker)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (e.getSource().getHolder(false) instanceof ShulkerBox && !e.getSource().getViewers().isEmpty()) {
            e.setCancelled(true);
        }
    }

    private boolean isOpenShulker(Block b) {
        if (!Tag.SHULKER_BOXES.isTagged(b.getType())) return false;
        BlockState state = b.getState(false);
        return state instanceof ShulkerBox box && !box.getInventory().getViewers().isEmpty();
    }
}
