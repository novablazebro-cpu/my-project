package dev.novablaze.antidupe.selftest;

import dev.novablaze.antidupe.core.Holder;
import dev.novablaze.antidupe.core.QuarantineEntry;
import dev.novablaze.antidupe.plugin.AntiDupePlugin;
import dev.novablaze.antidupe.plugin.Holders;
import dev.novablaze.antidupe.plugin.Scanner;
import dev.novablaze.antidupe.plugin.SerialService;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SelfTestPlugin extends JavaPlugin {
    private int pass, fail;
    private AntiDupePlugin ad;
    private SerialService serials;
    private Scanner scanner;
    private World w;
    private int nextX = 0;

    @Override
    public void onEnable() {
        ad = (AntiDupePlugin) Bukkit.getPluginManager().getPlugin("AntiDupe");
        Bukkit.getScheduler().runTaskLater(this, this::runAll, 40L);
    }

    private void check(String name, boolean ok) {
        if (ok) pass++; else fail++;
        getLogger().info((ok ? "PASS " : "FAIL ") + name);
    }

    private Block chestAt() {
        Block b = w.getBlockAt(nextX, 100, 0);
        nextX += 2;
        b.setType(Material.CHEST);
        return b;
    }

    private Inventory inv(Block b) {
        return ((Chest) b.getState(false)).getInventory();
    }

    private void scan(Block b) {
        Inventory inv = inv(b);
        scanner.scan(Holders.of(inv).orElseThrow(), inv, s -> "duper", true, Set.of());
    }

    private String serialOf(ItemStack i) {
        return serials.serial(i).orElse(null);
    }

    private int count(Inventory inv, Material m) {
        int n = 0;
        for (ItemStack i : inv.getContents()) if (i != null && i.getType() == m) n++;
        return n;
    }

    private void runAll() {
        serials = ad.serials();
        scanner = ad.scanner();
        w = Bukkit.getWorlds().getFirst();
        w.getChunkAt(0, 0).load();
        try {
            scenarios();
        } catch (Throwable t) {
            fail++;
            getLogger().severe("FAIL exception: " + t);
            t.printStackTrace();
        }
        // Quarantine and offences are saved on a background thread: check them a little later.
        Bukkit.getScheduler().runTaskLater(this, this::afterSaves, 40L);
    }

    private void afterSaves() {
        List<QuarantineEntry> q = ad.storage().quarantinedFor("duper");
        check("quarantine has all 5 removed copies (" + q.size() + ")", q.size() == 5);
        boolean rebuilt = q.stream().allMatch(x -> ItemStack.deserializeBytes(x.item()).getType() != Material.AIR);
        check("quarantined items can be rebuilt", rebuilt);
        int offences = ad.storage().offences("duper").size();
        check("5 removals in one moment = 1 offence (" + offences + ")", offences == 1);
        getLogger().info("SELFTEST DONE pass=" + pass + " fail=" + fail);
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
    }

    private void scenarios() throws Exception {
        // 1. A new item gets a serial and is registered where it is.
        Block a = chestAt();
        inv(a).setItem(0, new ItemStack(Material.ELYTRA));
        scan(a);
        String s1 = serialOf(inv(a).getItem(0));
        check("new item gets a serial", s1 != null);
        check("registry records the chest", ad.registry().lastSeen(s1).equals(java.util.Optional.of(Holders.container(a))));

        // 2. An exact copy in another chest is removed; the original stays.
        Block b = chestAt();
        inv(b).setItem(5, inv(a).getItem(0).clone());
        scan(b);
        check("copy in second chest removed", count(inv(b), Material.ELYTRA) == 0);
        check("original kept", count(inv(a), Material.ELYTRA) == 1);

        // 3. Moving the item (gone from A, now in B) is not a dupe.
        ItemStack moving = inv(a).getItem(0);
        inv(a).setItem(0, null);
        inv(b).setItem(3, moving);
        scan(b);
        check("moved item kept", count(inv(b), Material.ELYTRA) == 1);
        check("registry follows the move", ad.registry().lastSeen(s1).equals(java.util.Optional.of(Holders.container(b))));

        // 4. Two copies inside one chest: one removed.
        Block c = chestAt();
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        inv(c).setItem(0, sword);
        scan(c);
        inv(c).setItem(1, inv(c).getItem(0).clone());
        scan(c);
        check("two copies in one chest -> one left", count(inv(c), Material.NETHERITE_SWORD) == 1);

        // 5. Copy hidden inside a shulker box item is removed from the box only.
        Block d = chestAt();
        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        serials.setContents(box, List.of(inv(c).getItem(0).clone(), new ItemStack(Material.DIAMOND, 64)));
        inv(d).setItem(0, box);
        scan(d);
        List<ItemStack> after = serials.contents(inv(d).getItem(0));
        long swords = after.stream().filter(i -> i.getType() == Material.NETHERITE_SWORD).count();
        long diamonds = after.stream().filter(i -> i.getType() == Material.DIAMOND).count();
        check("copy inside shulker removed", swords == 0);
        check("other shulker contents untouched", diamonds == 1);
        check("box itself kept", inv(d).getItem(0) != null && inv(d).getItem(0).getType() == Material.SHULKER_BOX);

        // 6. A duplicated shulker box (same serial) is removed whole.
        Block e = chestAt();
        inv(e).setItem(0, inv(d).getItem(0).clone());
        scan(e);
        check("duplicated shulker box removed", count(inv(e), Material.SHULKER_BOX) == 0);
        check("original box kept", count(inv(d), Material.SHULKER_BOX) == 1);

        // 7. Old holder unloaded: never treated as proof.
        Block f = chestAt();
        ItemStack trident = new ItemStack(Material.TRIDENT);
        serials.setSerial(trident, "far-away-serial");
        ad.registry().record("far-away-serial", Holder.container(w.getName(), 30000, 64, 30000));
        inv(f).setItem(0, trident);
        scan(f);
        check("unloaded original -> nothing removed", count(inv(f), Material.TRIDENT) == 1);

        // 8. Original lying on the ground (relog dupe): copy in a chest is removed.
        ItemStack bow = new ItemStack(Material.BOW);
        serials.setSerial(bow, "ground-serial");
        Item onGround = w.dropItem(new Location(w, 0.5, 101, 5.5), bow.clone());
        scanner.scanEntity(onGround, s -> "duper");
        Block g = chestAt();
        inv(g).setItem(0, bow.clone());
        scan(g);
        check("copy of item on the ground removed", count(inv(g), Material.BOW) == 0);
        check("ground item kept", onGround.isValid());

        // 11. Does the game itself keep a block's serial when a shulker box is broken? (Information.)
        Block sb = w.getBlockAt(nextX, 100, 4);
        sb.setType(Material.SHULKER_BOX);
        ShulkerBox state = (ShulkerBox) sb.getState();
        state.getPersistentDataContainer().set(serials.key(), PersistentDataType.STRING, "block-serial");
        state.update(true, false);
        java.util.Collection<ItemStack> drops = sb.getDrops();
        boolean kept = drops.stream().anyMatch(i -> "block-serial".equals(serialOf(i)));
        getLogger().info("INFO vanilla keeps block serial on shulker drop: " + kept);
        check("placed shulker block stores serial", "block-serial".equals(((ShulkerBox) sb.getState())
                .getPersistentDataContainer().get(serials.key(), PersistentDataType.STRING)));
    }
}
