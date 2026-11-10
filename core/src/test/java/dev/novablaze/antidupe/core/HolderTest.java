package dev.novablaze.antidupe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HolderTest {
    @Test
    void player_names_are_case_insensitive() {
        assertEquals(Holder.player("Steve"), Holder.player("steve"));
    }

    @Test
    void container_key_format() {
        assertEquals("world:1:2:3", Holder.container("world", 1, 2, 3).key());
    }

    @Test
    void encode_decode_round_trips_every_kind() {
        for (Holder h : List.of(
                Holder.player("Alex"),
                Holder.enderChest("Alex"),
                Holder.container("world_nether", -5, 64, 10),
                Holder.placedShulker("world", 0, 70, 0),
                Holder.ground(UUID.randomUUID()),
                Holder.entity(UUID.randomUUID()),
                Holder.inShulker("box-serial"))) {
            assertEquals(h, Holder.decode(h.encode()));
        }
    }

    @Test
    void decode_rejects_garbage() {
        assertThrows(IllegalArgumentException.class, () -> Holder.decode("bogus"));
    }
}
