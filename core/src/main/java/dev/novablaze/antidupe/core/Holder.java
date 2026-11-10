package dev.novablaze.antidupe.core;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Where an item with a serial currently is: a player's inventory, a chest at a position, an item
 * lying on the ground, or inside a shulker box (identified by the box's own serial).
 *
 * <p>Granularity is the whole holder, never a slot, so moving an item around inside one inventory
 * never looks like a move.
 */
public record Holder(Kind kind, String key) {

    public enum Kind { PLAYER, ENDER_CHEST, CONTAINER, PLACED_SHULKER, GROUND, ENTITY, IN_SHULKER }

    public Holder {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(key, "key");
    }

    public static Holder player(String name) {
        return new Holder(Kind.PLAYER, name.toLowerCase(Locale.ROOT));
    }

    public static Holder enderChest(String name) {
        return new Holder(Kind.ENDER_CHEST, name.toLowerCase(Locale.ROOT));
    }

    public static Holder container(String world, int x, int y, int z) {
        return new Holder(Kind.CONTAINER, blockKey(world, x, y, z));
    }

    public static Holder placedShulker(String world, int x, int y, int z) {
        return new Holder(Kind.PLACED_SHULKER, blockKey(world, x, y, z));
    }

    public static Holder ground(UUID entityId) {
        return new Holder(Kind.GROUND, entityId.toString());
    }

    /** A storage entity: donkey, mule, llama, chest minecart, chest boat. */
    public static Holder entity(UUID entityId) {
        return new Holder(Kind.ENTITY, entityId.toString());
    }

    public static Holder inShulker(String boxSerial) {
        return new Holder(Kind.IN_SHULKER, boxSerial);
    }

    private static String blockKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    /** {@code KIND|key}, for storage. */
    public String encode() {
        return kind.name() + "|" + key;
    }

    public static Holder decode(String encoded) {
        int bar = encoded.indexOf('|');
        if (bar < 0) throw new IllegalArgumentException("Not an encoded holder: " + encoded);
        try {
            return new Holder(Kind.valueOf(encoded.substring(0, bar)), encoded.substring(bar + 1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown holder kind in: " + encoded, e);
        }
    }
}
