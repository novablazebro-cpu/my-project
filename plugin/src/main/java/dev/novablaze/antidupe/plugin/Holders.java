package dev.novablaze.antidupe.plugin;

import dev.novablaze.antidupe.core.Holder;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Translates between game inventories and {@link Holder}s.
 *
 * <p>Only real storage maps to a holder: player inventories, ender chests, container blocks and
 * storage entities. Crafting grids, anvils and plugin menus do not, so items shown in them are
 * never treated as stored there.
 */
public final class Holders {
    private Holders() {}

    public static Optional<Holder> of(Inventory inv) {
        if (inv == null) return Optional.empty();
        InventoryHolder h = inv.getHolder(false);
        if (h instanceof HumanEntity human) {
            if (inv.getType() == InventoryType.PLAYER) return Optional.of(Holder.player(human.getName()));
            if (inv.getType() == InventoryType.ENDER_CHEST) return Optional.of(Holder.enderChest(human.getName()));
            return Optional.empty();
        }
        if (h instanceof DoubleChest dc) return leftHalf(dc);
        if (h instanceof ShulkerBox box) return Optional.of(placedShulker(box.getBlock()));
        if (h instanceof Chest chest && chest.getInventory().getHolder(false) instanceof DoubleChest dc) {
            return leftHalf(dc);
        }
        if (h instanceof Container c) return Optional.of(container(c.getBlock()));
        if (h instanceof Entity e) return Optional.of(Holder.entity(e.getUniqueId()));
        return Optional.empty();
    }

    /** A double chest is one holder, named after its left half, so both halves agree. */
    private static Optional<Holder> leftHalf(DoubleChest dc) {
        return dc.getLeftSide(false) instanceof BlockState left
                ? Optional.of(container(left.getBlock()))
                : Optional.empty();
    }

    public static Holder container(Block b) {
        return Holder.container(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public static Holder placedShulker(Block b) {
        return Holder.placedShulker(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    /** The online player behind a PLAYER or ENDER_CHEST holder. */
    public static Optional<Player> player(Holder h) {
        return Optional.ofNullable(Bukkit.getPlayerExact(h.key()));
    }

    /** The block behind a CONTAINER or PLACED_SHULKER holder, only if its chunk is loaded. */
    public static Optional<Block> block(Holder h) {
        String key = h.key();
        int c3 = key.lastIndexOf(':');
        int c2 = key.lastIndexOf(':', c3 - 1);
        int c1 = key.lastIndexOf(':', c2 - 1);
        if (c1 < 0) return Optional.empty();
        World w = Bukkit.getWorld(key.substring(0, c1));
        if (w == null) return Optional.empty();
        int x = Integer.parseInt(key.substring(c1 + 1, c2));
        int y = Integer.parseInt(key.substring(c2 + 1, c3));
        int z = Integer.parseInt(key.substring(c3 + 1));
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return Optional.empty();
        return Optional.of(w.getBlockAt(x, y, z));
    }

    public static Optional<Entity> entity(Holder h) {
        try {
            return Optional.ofNullable(Bukkit.getEntity(UUID.fromString(h.key())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The live inventory behind a holder, if it is loaded and has one. */
    public static Optional<Inventory> inventory(Holder h) {
        return switch (h.kind()) {
            case PLAYER -> player(h).map(p -> (Inventory) p.getInventory());
            case ENDER_CHEST -> player(h).map(Player::getEnderChest);
            case CONTAINER -> block(h).map(b -> b.getState(false))
                    .filter(Container.class::isInstance).map(s -> ((Container) s).getInventory());
            case PLACED_SHULKER -> block(h).map(b -> b.getState(false))
                    .filter(ShulkerBox.class::isInstance).map(s -> ((ShulkerBox) s).getInventory());
            case ENTITY -> entity(h).filter(InventoryHolder.class::isInstance)
                    .map(e -> ((InventoryHolder) e).getInventory());
            case GROUND, IN_SHULKER -> Optional.empty();
        };
    }
}
