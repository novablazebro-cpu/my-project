package dev.novablaze.antidupe.plugin;

import dev.novablaze.antidupe.core.DupeDetector;
import dev.novablaze.antidupe.core.Holder;
import dev.novablaze.antidupe.core.Sighting;
import dev.novablaze.antidupe.core.Verdict;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Looks through an inventory or item entity, gives new items a serial, and asks the detector
 * about every item it sees. Proven duplicates go to the {@link Enforcer}.
 */
public final class Scanner {
    private final SerialService serials;
    private final DupeDetector detector;
    private final Enforcer enforcer;

    /** Sightings that could not be decided because the old holder (a player) was offline. */
    private final Map<Holder, Set<Sighting>> pending = new ConcurrentHashMap<>();

    public Scanner(SerialService serials, DupeDetector detector, Enforcer enforcer) {
        this.serials = serials;
        this.detector = detector;
        this.enforcer = enforcer;
    }

    /**
     * @param actorFor for each serial, the player whose action put it here, or null
     * @param deep also look inside every shulker box; when false, only inside boxes in {@code deepFor}
     */
    public void scan(Holder holder, Inventory inv, Function<String, String> actorFor, boolean deep,
            Set<String> deepFor) {
        ItemStack[] slots = inv.getContents();
        List<String> serialBySlot = new ArrayList<>(slots.length);
        for (int i = 0; i < slots.length; i++) {
            ItemStack item = slots[i];
            boolean wasNew = item != null && serials.ensureSerial(item);
            if (wasNew) inv.setItem(i, item);
            serialBySlot.add(item == null ? null : serials.serial(item).orElse(null));
        }
        Set<Integer> removed = new HashSet<>();
        for (int slot : detector.internalDuplicates(serialBySlot)) {
            int s = slot;
            String serial = serialBySlot.get(s);
            enforcer.duplicate(serial, slots[s], holder, holder, actorFor.apply(serial),
                    "two copies in one inventory", () -> inv.setItem(s, null));
            removed.add(s);
        }
        for (int i = 0; i < slots.length; i++) {
            String serial = serialBySlot.get(i);
            if (serial == null || removed.contains(i)) continue;
            int s = i;
            boolean gone = sight(new Sighting(serial, holder, actorFor.apply(serial)), slots[s],
                    () -> inv.setItem(s, null));
            if (!gone && serials.hasContents(slots[s]) && (deep || deepFor.contains(serial))) {
                ItemStack box = slots[s];
                scanBox(serial, box, actorFor, updated -> inv.setItem(s, updated));
            }
        }
    }

    /** A dropped item lying on the ground. */
    public void scanEntity(Item entity, Function<String, String> actorFor) {
        ItemStack item = entity.getItemStack();
        if (serials.ensureSerial(item)) entity.setItemStack(item);
        String serial = serials.serial(item).orElse(null);
        if (serial == null) return;
        Holder holder = Holder.ground(entity.getUniqueId());
        boolean gone = sight(new Sighting(serial, holder, actorFor.apply(serial)), item, entity::remove);
        if (!gone && serials.hasContents(item)) scanBox(serial, item, actorFor, entity::setItemStack);
    }

    /** The items inside a shulker box item. Changes are written back through {@code writeBack}. */
    private void scanBox(String boxSerial, ItemStack box, Function<String, String> actorFor,
            Consumer<ItemStack> writeBack) {
        List<ItemStack> contents = serials.contents(box);
        boolean changed = false;
        List<String> serialBySlot = new ArrayList<>(contents.size());
        for (ItemStack inner : contents) {
            if (serials.ensureSerial(inner)) changed = true;
            serialBySlot.add(serials.serial(inner).orElse(null));
        }
        Holder holder = Holder.inShulker(boxSerial);
        Set<Integer> extras = new HashSet<>(detector.internalDuplicates(serialBySlot));
        List<Runnable> removals = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            String serial = serialBySlot.get(i);
            if (serial == null) continue;
            int s = i;
            Runnable remove = () -> contents.set(s, ItemStack.empty());
            if (extras.contains(i)) {
                enforcer.duplicate(serial, contents.get(i), holder, holder, actorFor.apply(serial),
                        "two copies in one shulker box", () -> removals.add(remove));
                continue;
            }
            sight(new Sighting(serial, holder, actorFor.apply(serial)), contents.get(i),
                    () -> removals.add(remove));
        }
        removals.forEach(Runnable::run);
        if (changed || !removals.isEmpty()) {
            serials.setContents(box, contents);
            writeBack.accept(box);
        }
    }

    /** Returns true if the item was found to be a duplicate (and removed, in enforce mode). */
    private boolean sight(Sighting s, ItemStack item, Runnable remove) {
        Verdict v = detector.check(s);
        switch (v) {
            case Verdict.Duplicate d -> {
                enforcer.duplicate(s.serial(), item, s.at(), d.original(), d.culprit(),
                        "original still at " + d.original().encode(), remove);
                return true;
            }
            case Verdict.Deferred d -> {
                if (d.from().kind() == Holder.Kind.PLAYER || d.from().kind() == Holder.Kind.ENDER_CHEST) {
                    pending.computeIfAbsent(d.from(), k -> ConcurrentHashMap.newKeySet()).add(s);
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Sightings that waited for this holder's player to come online. Each one's holder is scanned
     * again now that both places can be checked.
     */
    public Set<Sighting> takePending(Holder nowOnline) {
        Set<Sighting> waiting = pending.remove(nowOnline);
        return waiting == null ? Set.of() : waiting;
    }
}
