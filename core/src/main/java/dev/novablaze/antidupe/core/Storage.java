package dev.novablaze.antidupe.core;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Everything AntiDupe must remember across restarts, in one SQLite file.
 *
 * <p>Methods are synchronized because one connection is shared; the plugin calls them from a
 * background thread so the game never waits on the disk.
 */
public final class Storage implements OffenceStore, BanStore, AutoCloseable {
    private final Connection db;

    public Storage(Path file) throws SQLException {
        db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement st = db.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("CREATE TABLE IF NOT EXISTS registry (serial TEXT PRIMARY KEY, holder TEXT NOT NULL)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS quarantine (
                      id INTEGER PRIMARY KEY AUTOINCREMENT, serial TEXT NOT NULL, item BLOB NOT NULL,
                      holder TEXT NOT NULL, culprit TEXT, reason TEXT NOT NULL, at INTEGER NOT NULL)""");
            st.execute("CREATE TABLE IF NOT EXISTS offences (name TEXT NOT NULL, at INTEGER NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS offences_name ON offences(name)");
            st.execute("CREATE TABLE IF NOT EXISTS bans (name TEXT PRIMARY KEY, until INTEGER NOT NULL)");
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ---- registry ----

    public synchronized Map<String, Holder> loadRegistry() throws SQLException {
        Map<String, Holder> out = new HashMap<>();
        try (Statement st = db.createStatement();
                ResultSet rs = st.executeQuery("SELECT serial, holder FROM registry")) {
            while (rs.next()) out.put(rs.getString(1), Holder.decode(rs.getString(2)));
        }
        return out;
    }

    /** Replaces the whole saved registry in one transaction, so a crash never leaves half of it. */
    public synchronized void saveRegistry(Map<String, Holder> entries) throws SQLException {
        db.setAutoCommit(false);
        try (Statement clear = db.createStatement();
                PreparedStatement ins = db.prepareStatement("INSERT INTO registry VALUES (?, ?)")) {
            clear.execute("DELETE FROM registry");
            for (Map.Entry<String, Holder> e : entries.entrySet()) {
                ins.setString(1, e.getKey());
                ins.setString(2, e.getValue().encode());
                ins.addBatch();
            }
            ins.executeBatch();
            db.commit();
        } catch (SQLException e) {
            db.rollback();
            throw e;
        } finally {
            db.setAutoCommit(true);
        }
    }

    // ---- quarantine ----

    public synchronized long quarantine(
            String serial, byte[] item, Holder holder, String culprit, String reason, Instant at) {
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO quarantine (serial, item, holder, culprit, reason, at) VALUES (?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, serial);
            ps.setBytes(2, item);
            ps.setString(3, holder.encode());
            ps.setString(4, culprit == null ? null : key(culprit));
            ps.setString(5, reason);
            ps.setLong(6, at.toEpochMilli());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    public synchronized Optional<QuarantineEntry> quarantined(long id) {
        List<QuarantineEntry> found = queryQuarantine("WHERE id = ?", Long.toString(id));
        return found.stream().findFirst();
    }

    /** Entries where the player was the culprit or held the copy. */
    public synchronized List<QuarantineEntry> quarantinedFor(String name) {
        String n = key(name);
        return queryQuarantine("WHERE culprit = ? OR holder = ? OR holder = ? ORDER BY id",
                n, Holder.player(n).encode(), Holder.enderChest(n).encode());
    }

    public synchronized void release(long id) {
        update("DELETE FROM quarantine WHERE id = ?", id);
    }

    public synchronized int purgeQuarantineBefore(Instant cutoff) {
        return update("DELETE FROM quarantine WHERE at < ?", cutoff.toEpochMilli());
    }

    private List<QuarantineEntry> queryQuarantine(String where, String... args) {
        List<QuarantineEntry> out = new ArrayList<>();
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT id, serial, item, holder, culprit, reason, at FROM quarantine " + where)) {
            for (int i = 0; i < args.length; i++) ps.setString(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new QuarantineEntry(rs.getLong(1), rs.getString(2), rs.getBytes(3),
                            Holder.decode(rs.getString(4)), rs.getString(5), rs.getString(6),
                            Instant.ofEpochMilli(rs.getLong(7))));
                }
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
        return out;
    }

    // ---- offences ----

    @Override
    public synchronized List<Instant> offences(String name) {
        List<Instant> out = new ArrayList<>();
        try (PreparedStatement ps = db.prepareStatement("SELECT at FROM offences WHERE name = ?")) {
            ps.setString(1, key(name));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Instant.ofEpochMilli(rs.getLong(1)));
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
        return out;
    }

    @Override
    public synchronized void addOffence(String name, Instant at) {
        update("INSERT INTO offences VALUES (?, ?)", key(name), at.toEpochMilli());
    }

    @Override
    public synchronized void clear(String name) {
        update("DELETE FROM offences WHERE name = ?", key(name));
    }

    // ---- bans ----

    @Override
    public synchronized void ban(String name, Instant until) {
        update("INSERT OR REPLACE INTO bans VALUES (?, ?)", key(name), until.toEpochMilli());
    }

    @Override
    public synchronized Optional<Instant> bannedUntil(String name, Instant now) {
        try (PreparedStatement ps = db.prepareStatement("SELECT until FROM bans WHERE name = ?")) {
            ps.setString(1, key(name));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Instant until = Instant.ofEpochMilli(rs.getLong(1));
                return until.isAfter(now) ? Optional.of(until) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public synchronized void unban(String name) {
        update("DELETE FROM bans WHERE name = ?", key(name));
    }

    // ---- helpers ----

    private int update(String sql, Object... args) {
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        db.close();
    }

    /** A database error, unchecked so callers inside event handlers are not forced to catch it. */
    public static final class StorageException extends RuntimeException {
        StorageException(SQLException cause) {
            super(cause.getMessage(), cause);
        }
    }
}
