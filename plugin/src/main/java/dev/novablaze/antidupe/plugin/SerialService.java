package dev.novablaze.antidupe.plugin;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Reads and writes the hidden serial number on items.
 *
 * <p>The serial lives in the item's persistent data container under {@code antidupe:serial}. It
 * travels with the item through dropping, chests, trading and relogging. Only items that cannot
 * stack get one, because two stacks with different serials would refuse to stack together.
 */
public final class SerialService {
    private final NamespacedKey key;
    private volatile Set<Material> excluded = Set.of();

    public SerialService(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "serial");
    }

    public NamespacedKey key() {
        return key;
    }

    public void setExcluded(Set<Material> excluded) {
        this.excluded = Set.copyOf(excluded);
    }

    /** True for items that should carry a serial: unstackable and not excluded in the config. */
    public boolean covers(ItemStack item) {
        return item != null && !item.isEmpty() && item.getMaxStackSize() == 1
                && !excluded.contains(item.getType());
    }

    public Optional<String> serial(ItemStack item) {
        if (item == null || item.isEmpty()) return Optional.empty();
        return Optional.ofNullable(item.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    /** Gives the item a new random serial if it is covered and has none. Returns true if it changed. */
    public boolean ensureSerial(ItemStack item) {
        if (!covers(item) || serial(item).isPresent()) return false;
        setSerial(item, UUID.randomUUID().toString());
        return true;
    }

    public void setSerial(ItemStack item, String serial) {
        item.editPersistentDataContainer(pdc -> pdc.set(key, PersistentDataType.STRING, serial));
    }

    public void stripSerial(ItemStack item) {
        if (item != null && !item.isEmpty()) item.editPersistentDataContainer(pdc -> pdc.remove(key));
    }

    /** Removes the serial from the item and from everything inside it (for a shulker box). */
    public void stripAll(ItemStack item) {
        stripSerial(item);
        if (!hasContents(item)) return;
        List<ItemStack> contents = contents(item);
        for (ItemStack inner : contents) stripSerial(inner);
        setContents(item, contents);
    }

    /** True for items that store other items inside them, such as a shulker box. */
    public boolean hasContents(ItemStack item) {
        return item != null && !item.isEmpty() && item.getData(DataComponentTypes.CONTAINER) != null;
    }

    /** A modifiable copy of the items inside, in slot order. Empty slots are empty stacks. */
    public List<ItemStack> contents(ItemStack box) {
        ItemContainerContents data = box.getData(DataComponentTypes.CONTAINER);
        return data == null ? new ArrayList<>() : new ArrayList<>(data.contents());
    }

    public void setContents(ItemStack box, List<ItemStack> contents) {
        box.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(contents));
    }
}
