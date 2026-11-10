package dev.novablaze.antidupe.plugin;

import dev.novablaze.antidupe.core.Action;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/** The values from config.yml, read once on start and on /antidupe reload. */
public record Settings(
        boolean enforce,
        List<Action> ladder,
        Duration offenceExpiry,
        Duration quarantineRetention,
        Set<Material> excluded,
        boolean refuseSecondLogin,
        int suspectThreshold,
        Messages messages) {

    public record Messages(
            String warn, String kick, String ban, String banned, String innocent,
            String alert, String secondLogin, String suspect) {}

    public static Settings from(FileConfiguration c, Logger log) {
        List<Action> ladder = new ArrayList<>();
        for (String step : c.getStringList("punishments")) {
            try {
                ladder.add(parseAction(step));
            } catch (IllegalArgumentException e) {
                log.warning("Ignoring punishment step '" + step + "': " + e.getMessage());
            }
        }
        if (ladder.isEmpty()) {
            log.warning("No valid punishment steps; using warn, kick, ban 2h.");
            ladder = dev.novablaze.antidupe.core.PunishmentLadder.defaultSteps();
        }
        Set<Material> excluded = EnumSet.noneOf(Material.class);
        for (String name : c.getStringList("excluded-items")) {
            Material m = Material.matchMaterial(name);
            if (m == null) log.warning("Unknown item in excluded-items: " + name);
            else excluded.add(m);
        }
        String mode = c.getString("mode", "enforce").toLowerCase(Locale.ROOT);
        if (!mode.equals("enforce") && !mode.equals("monitor")) {
            log.warning("mode must be 'enforce' or 'monitor'; using monitor to be safe.");
            mode = "monitor";
        }
        return new Settings(
                mode.equals("enforce"),
                List.copyOf(ladder),
                Duration.ofDays(c.getLong("offence-expiry-days", 30)),
                Duration.ofDays(c.getLong("quarantine-retention-days", 30)),
                excluded,
                c.getBoolean("refuse-second-login", true),
                c.getInt("suspect-alert-threshold", 64),
                new Messages(
                        c.getString("messages.warn"), c.getString("messages.kick"),
                        c.getString("messages.ban"), c.getString("messages.banned"),
                        c.getString("messages.innocent"), c.getString("messages.alert"),
                        c.getString("messages.second-login"), c.getString("messages.suspect")));
    }

    /** "warn", "kick", or "ban 2h" / "ban 30m" / "ban 1d". */
    static Action parseAction(String step) {
        String[] parts = step.trim().toLowerCase(Locale.ROOT).split("\\s+");
        return switch (parts[0]) {
            case "warn" -> new Action.Warn();
            case "kick" -> new Action.Kick();
            case "ban" -> {
                if (parts.length != 2) throw new IllegalArgumentException("ban needs a length, like 'ban 2h'");
                yield new Action.Ban(parseDuration(parts[1]));
            }
            default -> throw new IllegalArgumentException("unknown action " + parts[0]);
        };
    }

    static Duration parseDuration(String s) {
        if (s.length() < 2) throw new IllegalArgumentException("bad length " + s);
        long n;
        try {
            n = Long.parseLong(s.substring(0, s.length() - 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad length " + s);
        }
        return switch (s.charAt(s.length() - 1)) {
            case 'm' -> Duration.ofMinutes(n);
            case 'h' -> Duration.ofHours(n);
            case 'd' -> Duration.ofDays(n);
            default -> throw new IllegalArgumentException("length must end in m, h or d: " + s);
        };
    }

    public static String describe(Duration d) {
        if (d.toDays() > 0 && d.toHours() % 24 == 0) return d.toDays() + " day(s)";
        if (d.toHours() > 0 && d.toMinutes() % 60 == 0) return d.toHours() + " hour(s)";
        return d.toMinutes() + " minute(s)";
    }
}
