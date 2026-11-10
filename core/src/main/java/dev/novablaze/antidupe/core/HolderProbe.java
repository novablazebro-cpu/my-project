package dev.novablaze.antidupe.core;

/** Lets the detector look at the real world. The plugin implements it with Paper's API. */
public interface HolderProbe {
    /** True when the holder can be inspected right now (player online, chunk loaded, entity alive). */
    boolean isLoaded(Holder holder);

    /** True when the holder currently contains an item with this serial. Only called if loaded. */
    boolean holds(Holder holder, String serial);
}
