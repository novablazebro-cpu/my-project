package dev.novablaze.antidupe.plugin;

import dev.novablaze.antidupe.core.Holder;
import dev.novablaze.antidupe.core.HolderProbe;
import dev.novablaze.antidupe.core.Registry;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Looks at the real world for the detector.
 *
 * <p>Every answer leans towards "not a duplicate" when unsure: an unknown or unloaded place is
 * reported as not loaded (so the detector waits), never as "still holds it".
 */
public final class PaperHolderProbe implements HolderProbe {
    private final SerialService serials;
    private final Registry registry;

    public PaperHolderProbe(SerialService serials, Registry registry) {
        this.serials = serials;
        this.registry = registry;
    }

    @Override
    public boolean isLoaded(Holder h) {
        return switch (h.kind()) {
            case PLAYER, ENDER_CHEST -> Holders.player(h).isPresent();
            case CONTAINER, PLACED_SHULKER -> Holders.block(h).isPresent();
            // A vanished item entity was picked up, burnt or despawned far more often than it was
            // unloaded, and treating it as "gone" can only miss a duplicate, never invent one.
            case GROUND -> true;
            case ENTITY -> Holders.entity(h).isPresent();
            case IN_SHULKER -> registry.lastSeen(h.key())
                    .filter(box -> box.kind() != Holder.Kind.IN_SHULKER)
                    .map(this::isLoaded)
                    .orElse(false);
        };
    }

    @Override
    public boolean holds(Holder h, String serial) {
        return switch (h.kind()) {
            case PLAYER -> Holders.player(h).map(p -> playerHolds(p, serial)).orElse(false);
            case ENDER_CHEST, CONTAINER, ENTITY ->
                    Holders.inventory(h).map(inv -> contains(inv, serial)).orElse(false);
            case PLACED_SHULKER -> Holders.block(h).map(b -> b.getState(false))
                    .filter(ShulkerBox.class::isInstance).map(ShulkerBox.class::cast)
                    .map(box -> serial.equals(box.getPersistentDataContainer()
                                    .get(serials.key(), PersistentDataType.STRING))
                            || contains(box.getInventory(), serial))
                    .orElse(false);
            case GROUND -> Holders.entity(h).filter(Item.class::isInstance).map(Item.class::cast)
                    .filter(Item::isValid)
                    .map(it -> serial.equals(serials.serial(it.getItemStack()).orElse(null)))
                    .orElse(false);
            case IN_SHULKER -> findBox(h.key())
                    .map(box -> serials.contents(box).stream()
                            .anyMatch(inner -> serial.equals(serials.serial(inner).orElse(null))))
                    .orElse(false);
        };
    }

    /** Inventory, armour, off hand, the item on the cursor, and a crafting grid or anvil in use. */
    private boolean playerHolds(Player p, String serial) {
        if (contains(p.getInventory(), serial)) return true;
        if (serial.equals(serials.serial(p.getItemOnCursor()).orElse(null))) return true;
        Inventory top = p.getOpenInventory().getTopInventory();
        return Holders.of(top).isEmpty() && contains(top, serial);
    }

    private boolean contains(Inventory inv, String serial) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && serial.equals(serials.serial(item).orElse(null))) return true;
        }
        return false;
    }

    /**
     * The shulker box item with this serial, wherever the registry last saw it. A box that is
     * placed as a block is not an item: its contents now belong to the block, so this is empty.
     */
    private Optional<ItemStack> findBox(String boxSerial) {
        Optional<Holder> where = registry.lastSeen(boxSerial);
        if (where.isEmpty()) return Optional.empty();
        Holder h = where.get();
        return switch (h.kind()) {
            case GROUND -> Holders.entity(h).filter(Item.class::isInstance)
                    .map(e -> ((Item) e).getItemStack())
                    .filter(it -> boxSerial.equals(serials.serial(it).orElse(null)));
            case PLAYER -> Holders.player(h).flatMap(p -> {
                ItemStack cursor = p.getItemOnCursor();
                if (boxSerial.equals(serials.serial(cursor).orElse(null))) return Optional.of(cursor);
                return find(p.getInventory(), boxSerial);
            });
            case ENDER_CHEST, CONTAINER, ENTITY -> Holders.inventory(h).flatMap(inv -> find(inv, boxSerial));
            case PLACED_SHULKER, IN_SHULKER -> Optional.empty();
        };
    }

    private Optional<ItemStack> find(Inventory inv, String serial) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && Objects.equals(serial, serials.serial(item).orElse(null))) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
