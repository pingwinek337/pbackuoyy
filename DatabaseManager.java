package pl.pbackup.database;

import pl.pbackup.PBackup;
import pl.pbackup.models.BackupRecord;
import pl.pbackup.models.DeathPage;
import pl.pbackup.models.DeathRecord;
import pl.pbackup.models.TargetPlayer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Cała obsługa SQLite. Jedno połączenie, jeden wątek roboczy - wszystkie zapytania wykonują się
 * po kolei poza głównym wątkiem serwera, więc nie ma wyścigów ani lagów.
 * <p>
 * Metody publiczne zwracają {@link CompletableFuture}, które kończą się na wątku bazy danych.
 * Menedżerowie opakowują je tak, aby wynik wracał na główny wątek serwera.
 */
public final class DatabaseManager {

    /** Zadanie SQL wykonywane na wątku bazy danych. */
    @FunctionalInterface
    public interface SqlTask<T> {
        T run(Connection connection) throws SQLException;
    }

    /** Śmierć wraz z surowymi (jeszcze nieodczytanymi) bajtami ekwipunku. */
    public record StoredDeath(DeathRecord meta, byte[] storage, byte[] armor, byte[] offhand) {
    }

    private static final String DEATH_COLUMNS =
            "id, player_uuid, player_name, death_time, world, x, y, z, cause, killer_name, killer_uuid, "
                    + "xp_level, xp_progress, xp_points";
    private static final String GRANT_COLUMNS =
            "id, death_id, given_by_name, given_by_uuid, target_uuid, grant_time, items_given, items_dropped, xp_restored";

    private final PBackup plugin;
    private final ExecutorService executor;
    private Connection connection;
    private boolean closed;

    public DatabaseManager(PBackup plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PBackup-Database");
            thread.setDaemon(false);
            return thread;
        });
    }

    // ------------------------------------------------------------------ połączenie

    /** Otwiera plik bazy i tworzy tabele. Wywoływane raz, przy starcie pluginu. */
    public void connect() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Brak sterownika SQLite (org.sqlite.JDBC) na serwerze.", e);
        }
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new SQLException("Nie można utworzyć folderu danych pluginu: " + folder.getAbsolutePath());
        }
        File file = new File(folder, "pbackup.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA busy_timeout = 5000");

            st.execute("CREATE TABLE IF NOT EXISTS deaths ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "player_uuid TEXT NOT NULL,"
                    + "player_name TEXT NOT NULL,"
                    + "death_time INTEGER NOT NULL,"
                    + "world TEXT NOT NULL,"
                    + "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,"
                    + "cause TEXT NOT NULL,"
                    + "killer_name TEXT,"
                    + "killer_uuid TEXT,"
                    + "xp_level INTEGER NOT NULL,"
                    + "xp_progress REAL NOT NULL,"
                    + "xp_points INTEGER NOT NULL,"
                    + "storage BLOB NOT NULL,"
                    + "armor BLOB NOT NULL,"
                    + "offhand BLOB NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_deaths_player ON deaths(player_uuid, death_time DESC)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_deaths_name ON deaths(player_name COLLATE NOCASE)");

            st.execute("CREATE TABLE IF NOT EXISTS backup_grants ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "death_id INTEGER NOT NULL,"
                    + "given_by_name TEXT NOT NULL,"
                    + "given_by_uuid TEXT NOT NULL,"
                    + "target_uuid TEXT NOT NULL,"
                    + "grant_time INTEGER NOT NULL,"
                    + "items_given INTEGER NOT NULL,"
                    + "items_dropped INTEGER NOT NULL,"
                    + "xp_restored INTEGER NOT NULL,"
                    + "FOREIGN KEY (death_id) REFERENCES deaths(id) ON DELETE CASCADE)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_grants_death ON backup_grants(death_id)");
        }
    }

    /** Czeka na dokończenie zapytań i bezpiecznie zamyka połączenie. */
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Zapytania do bazy nie zakończyły się w 20 s - wymuszam zamknięcie.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Błąd podczas zamykania połączenia z bazą.", e);
            }
            connection = null;
        }
    }

    private <T> CompletableFuture<T> submit(SqlTask<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    if (connection == null) {
                        throw new SQLException("Połączenie z bazą jest zamknięte.");
                    }
                    future.complete(task.run(connection));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private static <T> T transaction(Connection c, SqlTask<T> task) throws SQLException {
        boolean previous = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            T result = task.run(c);
            c.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        } finally {
            c.setAutoCommit(previous);
        }
    }

    // ------------------------------------------------------------------ śmierci

    public CompletableFuture<Long> insertDeath(DeathRecord d, byte[] storage, byte[] armor, byte[] offhand) {
        return submit(c -> {
            String sql = "INSERT INTO deaths (player_uuid, player_name, death_time, world, x, y, z, cause, "
                    + "killer_name, killer_uuid, xp_level, xp_progress, xp_points, storage, armor, offhand) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, d.getPlayerUuid().toString());
                ps.setString(2, d.getPlayerName());
                ps.setLong(3, d.getDeathTime());
                ps.setString(4, d.getWorld());
                ps.setDouble(5, d.getX());
                ps.setDouble(6, d.getY());
                ps.setDouble(7, d.getZ());
                ps.setString(8, d.getCause());
                ps.setString(9, d.getKillerName());
                ps.setString(10, d.getKillerUuid() == null ? null : d.getKillerUuid().toString());
                ps.setInt(11, d.getXpLevel());
                ps.setDouble(12, d.getXpProgress());
                ps.setInt(13, d.getXpPoints());
                ps.setBytes(14, storage);
                ps.setBytes(15, armor);
                ps.setBytes(16, offhand);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            throw new SQLException("Baza nie zwróciła identyfikatora nowego wpisu śmierci.");
        });
    }

    public CompletableFuture<Integer> countDeaths(UUID owner) {
        return submit(c -> countDeaths(c, owner));
    }

    private static int countDeaths(Connection c, UUID owner) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM deaths WHERE player_uuid = ?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public CompletableFuture<DeathPage> loadPage(UUID owner, int requestedPage, int pageSize) {
        return submit(c -> {
            int total = countDeaths(c, owner);
            int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
            int page = Math.min(Math.max(requestedPage, 0), totalPages - 1);

            List<DeathRecord> records = new ArrayList<>();
            String sql = "SELECT " + DEATH_COLUMNS + " FROM deaths WHERE player_uuid = ? "
                    + "ORDER BY death_time DESC, id DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, owner.toString());
                ps.setInt(2, pageSize);
                ps.setInt(3, page * pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        records.add(mapDeath(rs));
                    }
                }
            }
            attachGrants(c, records);
            return new DeathPage(List.copyOf(records), page, totalPages, total);
        });
    }

    public CompletableFuture<Optional<StoredDeath>> loadDeath(long id) {
        return submit(c -> {
            String sql = "SELECT " + DEATH_COLUMNS + ", storage, armor, offhand FROM deaths WHERE id = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<StoredDeath>empty();
                    }
                    DeathRecord meta = mapDeath(rs);
                    byte[] storage = rs.getBytes("storage");
                    byte[] armor = rs.getBytes("armor");
                    byte[] offhand = rs.getBytes("offhand");
                    List<DeathRecord> single = new ArrayList<>(1);
                    single.add(meta);
                    attachGrants(c, single);
                    return Optional.of(new StoredDeath(meta, storage, armor, offhand));
                }
            }
        });
    }

    /** Usuwa jedną śmierć (tylko jeśli należy do podanego UUID) razem z historią jej nadań. */
    public CompletableFuture<Boolean> deleteDeath(long id, UUID owner) {
        return submit(c -> transaction(c, tx -> {
            try (PreparedStatement ps = tx.prepareStatement(
                    "DELETE FROM backup_grants WHERE death_id IN (SELECT id FROM deaths WHERE id = ? AND player_uuid = ?)")) {
                ps.setLong(1, id);
                ps.setString(2, owner.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = tx.prepareStatement("DELETE FROM deaths WHERE id = ? AND player_uuid = ?")) {
                ps.setLong(1, id);
                ps.setString(2, owner.toString());
                return ps.executeUpdate() > 0;
            }
        }));
    }

    /** Usuwa całą historię gracza i zwraca liczbę usuniętych śmierci. */
    public CompletableFuture<Integer> deleteAllDeaths(UUID owner) {
        return submit(c -> transaction(c, tx -> {
            try (PreparedStatement ps = tx.prepareStatement(
                    "DELETE FROM backup_grants WHERE death_id IN (SELECT id FROM deaths WHERE player_uuid = ?)")) {
                ps.setString(1, owner.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = tx.prepareStatement("DELETE FROM deaths WHERE player_uuid = ?")) {
                ps.setString(1, owner.toString());
                return ps.executeUpdate();
            }
        }));
    }

    /** Szuka gracza po nicku w zapisanych śmierciach (najnowszy wpis wygrywa). */
    public CompletableFuture<Optional<TargetPlayer>> findPlayerByName(String name) {
        return submit(c -> {
            String sql = "SELECT player_uuid, player_name FROM deaths WHERE player_name = ? COLLATE NOCASE "
                    + "ORDER BY death_time DESC LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new TargetPlayer(UUID.fromString(rs.getString(1)), rs.getString(2)));
                    }
                    return Optional.<TargetPlayer>empty();
                }
            }
        });
    }

    // ------------------------------------------------------------------ nadania kopii

    public CompletableFuture<Long> insertGrant(BackupRecord g) {
        return submit(c -> {
            String sql = "INSERT INTO backup_grants (death_id, given_by_name, given_by_uuid, target_uuid, grant_time, "
                    + "items_given, items_dropped, xp_restored) VALUES (?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, g.deathId());
                ps.setString(2, g.givenByName());
                ps.setString(3, g.givenByUuid().toString());
                ps.setString(4, g.targetUuid().toString());
                ps.setLong(5, g.grantTime());
                ps.setInt(6, g.itemsGiven());
                ps.setInt(7, g.itemsDropped());
                ps.setInt(8, g.xpRestored());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            throw new SQLException("Baza nie zwróciła identyfikatora wpisu nadania.");
        });
    }

    // ------------------------------------------------------------------ mapowanie

    private static DeathRecord mapDeath(ResultSet rs) throws SQLException {
        String killerUuid = rs.getString("killer_uuid");
        return new DeathRecord(
                rs.getLong("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getLong("death_time"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getString("cause"),
                rs.getString("killer_name"),
                killerUuid == null ? null : UUID.fromString(killerUuid),
                rs.getInt("xp_level"),
                (float) rs.getDouble("xp_progress"),
                rs.getInt("xp_points"),
                null, null, null);
    }

    private static BackupRecord mapGrant(ResultSet rs) throws SQLException {
        return new BackupRecord(
                rs.getLong("id"),
                rs.getLong("death_id"),
                rs.getString("given_by_name"),
                UUID.fromString(rs.getString("given_by_uuid")),
                UUID.fromString(rs.getString("target_uuid")),
                rs.getLong("grant_time"),
                rs.getInt("items_given"),
                rs.getInt("items_dropped"),
                rs.getInt("xp_restored"));
    }

    private static void attachGrants(Connection c, List<DeathRecord> records) throws SQLException {
        if (records.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(GRANT_COLUMNS)
                .append(" FROM backup_grants WHERE death_id IN (");
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(") ORDER BY id DESC");

        Map<Long, List<BackupRecord>> byDeath = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < records.size(); i++) {
                ps.setLong(i + 1, records.get(i).getId());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BackupRecord grant = mapGrant(rs);
                    byDeath.computeIfAbsent(grant.deathId(), k -> new ArrayList<>()).add(grant);
                }
            }
        }
        for (DeathRecord record : records) {
            record.setGrants(byDeath.getOrDefault(record.getId(), List.of()));
        }
    }
}
