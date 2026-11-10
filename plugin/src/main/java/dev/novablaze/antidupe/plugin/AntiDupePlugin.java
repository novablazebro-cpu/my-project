package dev.novablaze.antidupe.plugin;

import dev.novablaze.antidupe.core.DupeDetector;
import dev.novablaze.antidupe.core.PunishmentLadder;
import dev.novablaze.antidupe.core.Registry;
import dev.novablaze.antidupe.core.Storage;
import dev.novablaze.antidupe.plugin.commands.AntiDupeCommand;
import dev.novablaze.antidupe.plugin.guards.AuthMeHook;
import dev.novablaze.antidupe.plugin.guards.LoginGapGuard;
import dev.novablaze.antidupe.plugin.guards.ShulkerGuard;
import dev.novablaze.antidupe.plugin.listeners.InventoryListener;
import dev.novablaze.antidupe.plugin.listeners.ItemEntityListener;
import dev.novablaze.antidupe.plugin.listeners.SessionListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Wires the parts together. Each part is documented in its own file. */
public final class AntiDupePlugin extends JavaPlugin {
    private static final long SAVE_EVERY_TICKS = 20L * 60;
    private static final Duration CRASH_GRACE = Duration.ofMinutes(30);

    private volatile Settings settings;
    private volatile PunishmentLadder ladder;
    private Storage storage;
    private final Registry registry = new Registry();
    private SerialService serials;
    private Scanner scanner;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            getDataFolder().mkdirs();
            storage = new Storage(getDataFolder().toPath().resolve("antidupe.db"));
            registry.loadAll(storage.loadRegistry());
        } catch (SQLException e) {
            getLogger().severe("Could not open the database, AntiDupe is disabled: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        serials = new SerialService(this);
        reloadSettings();
        Instant pausedUntil = crashCheck();

        DupeDetector detector = new DupeDetector(registry, new PaperHolderProbe(serials, registry));
        Enforcer enforcer = new Enforcer(this, storage, () -> settings, () -> ladder, pausedUntil);
        scanner = new Scanner(serials, detector, enforcer);
        SessionListener sessions = new SessionListener(this, storage, scanner, () -> settings);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new InventoryListener(this, serials, scanner), this);
        pm.registerEvents(new ItemEntityListener(this, serials, scanner, registry), this);
        pm.registerEvents(sessions, this);
        pm.registerEvents(new ShulkerGuard(this, serials, registry), this);
        AuthMeHook.connect(this, sessions::joinCheck)
                .ifPresentOrElse(hook -> pm.registerEvents(new LoginGapGuard(hook), this),
                        () -> getLogger().info("AuthMe not found: login-gap guard is off."));

        AntiDupeCommand command = new AntiDupeCommand(this, storage, registry, serials, () -> ladder, () -> {
            reloadConfig();
            reloadSettings();
        });
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("antidupe", "AntiDupe admin commands", command));

        getServer().getScheduler().runTaskTimerAsynchronously(this, this::saveRegistry,
                SAVE_EVERY_TICKS, SAVE_EVERY_TICKS);
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            int purged = storage.purgeQuarantineBefore(Instant.now().minus(settings.quarantineRetention()));
            if (purged > 0) getLogger().info("Deleted " + purged + " expired quarantine entries.");
        });
        getLogger().info("AntiDupe enabled in " + (settings.enforce() ? "ENFORCE" : "MONITOR")
                + " mode, tracking " + registry.snapshot().size() + " items.");
    }

    private Path runningMarker() {
        return getDataFolder().toPath().resolve("running.lock");
    }

    /**
     * A marker file exists while the server runs and is deleted on a clean stop. If it is still
     * there on start, the last run crashed: punishments pause for a while.
     */
    private Instant crashCheck() {
        Path marker = runningMarker();
        boolean crashed = Files.exists(marker);
        try {
            Files.writeString(marker, Instant.now().toString());
        } catch (IOException e) {
            getLogger().warning("Could not write " + marker + ": " + e.getMessage());
        }
        if (!crashed) return Instant.MIN;
        getLogger().warning("The server did not shut down cleanly last time. Duplicates found in the next "
                + CRASH_GRACE.toMinutes() + " minutes are removed but nobody is punished.");
        return Instant.now().plus(CRASH_GRACE);
    }

    /** For the self-test plugin and other integrations. */
    public Scanner scanner() { return scanner; }

    public Registry registry() { return registry; }

    public Storage storage() { return storage; }

    public SerialService serials() { return serials; }

    private void reloadSettings() {
        settings = Settings.from(getConfig(), getLogger());
        serials.setExcluded(settings.excluded());
        ladder = new PunishmentLadder(settings.ladder(), settings.offenceExpiry(), storage, Clock.systemUTC());
    }

    private void saveRegistry() {
        try {
            storage.saveRegistry(registry.snapshot());
        } catch (SQLException | RuntimeException e) {
            getLogger().warning("Could not save the item registry: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (storage == null) return;
        saveRegistry();
        try {
            Files.deleteIfExists(runningMarker());
        } catch (IOException e) {
            getLogger().warning("Could not delete the running marker: " + e.getMessage());
        }
        try {
            storage.close();
        } catch (SQLException e) {
            getLogger().warning("Could not close the database: " + e.getMessage());
        }
    }
}
