package dev.novablaze.antidupe.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether a sighted item is the same item that moved, or a second copy.
 *
 * <p>The rule is "verify before delete": an item is only called a duplicate when the place it was
 * last seen can be inspected right now and still contains it. A holder that cannot be inspected is
 * never treated as proof.
 */
public final class DupeDetector {
    private final Registry registry;
    private final HolderProbe probe;

    public DupeDetector(Registry registry, HolderProbe probe) {
        this.registry = registry;
        this.probe = probe;
    }

    public Verdict check(Sighting s) {
        Optional<Holder> previous = registry.lastSeen(s.serial());
        if (previous.isEmpty()) {
            registry.record(s.serial(), s.at());
            return new Verdict.New();
        }
        Holder old = previous.get();
        if (old.equals(s.at())) return new Verdict.Same();
        if (!probe.isLoaded(old)) return new Verdict.Deferred(old);
        if (probe.holds(old, s.serial())) return new Verdict.Duplicate(old, s.actor());
        registry.record(s.serial(), s.at());
        return new Verdict.Moved(old);
    }

    /**
     * Within one inventory, the slot indexes holding the 2nd, 3rd ... occurrence of a serial. Two
     * copies in the same chest share a holder, so {@link #check} alone cannot see them.
     *
     * @param serialsBySlot the serial in each slot, or null for slots without one
     */
    public List<Integer> internalDuplicates(List<String> serialsBySlot) {
        Set<String> seen = new HashSet<>();
        List<Integer> extra = new ArrayList<>();
        for (int slot = 0; slot < serialsBySlot.size(); slot++) {
            String serial = serialsBySlot.get(slot);
            if (serial != null && !seen.add(serial)) extra.add(slot);
        }
        return extra;
    }
}
