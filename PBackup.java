package pl.pbackup;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import pl.pbackup.commands.PBackupCommand;
import pl.pbackup.database.DatabaseManager;
import pl.pbackup.gui.AbstractGui;
import pl.pbackup.gui.GuiNavigator;
import pl.pbackup.listeners.InventoryListener;
import pl.pbackup.listeners.PlayerDeathListener;
import pl.pbackup.managers.BackupManager;
import pl.pbackup.managers.DeathHistoryManager;
import pl.pbackup.utils.Messages;

import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class PBackup extends JavaPlugin {

    private Messages messages;
    private ZoneId zoneId;
    private DatabaseManager database;
    private DeathHistoryManager deathHistory;
    private BackupManager backupManager;
    private GuiNavigator navigator;

    @Override
    public void onLoad() {
        // Folder pluginu (plugins/PBackup) powstaje od razu przy wczytaniu pluginu.
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Nie można utworzyć folderu pluginu: " + getDataFolder().getAbsolutePath());
        }
    }

    @Override
    public void onEnable() {
        try {
            startUp();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Krytyczny błąd podczas uruchamiania PBackup - plugin zostaje wyłączony.", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void startUp() {
        saveDefaultConfig();
        messages = new Messages(getConfig().getString("messages.prefix", "&8[&6PBackup&8] &r"));
        zoneId = resolveZone(getConfig().getString("settings.timezone", "Europe/Warsaw"));

        database = new DatabaseManager(this);
        try {
            database.connect();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Nie można otworzyć bazy SQLite - plugin zostaje wyłączony.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        deathHistory = new DeathHistoryManager(this, database);
        backupManager = new BackupManager(this, database);
        navigator = new GuiNavigator(this);

        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

        PluginCommand command = getCommand("pbackup");
        if (command == null) {
            getLogger().severe("Brak komendy /pbackup w plugin.yml - plugin zostaje wyłączony.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        PBackupCommand handler = new PBackupCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        getLogger().info("PBackup włączony. Baza danych: " + getDataFolder().getName() + "/pbackup.db");
    }

    @Override
    public void onDisable() {
        // Zamknij otwarte GUI pluginu (przy reloadzie/wyłączeniu nie zostaną "martwe" okna)
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof AbstractGui) {
                player.closeInventory();
            }
        }
        if (database != null) {
            database.close();
        }
    }

    /** Uruchamia kod na głównym wątku serwera (bezpiecznie także przy wyłączaniu pluginu). */
    public void sync(Runnable task) {
        if (!isEnabled()) {
            return;
        }
        try {
            getServer().getScheduler().runTask(this, task);
        } catch (IllegalPluginAccessException ignored) {
            // plugin właśnie się wyłącza
        }
    }

    /** Zwraca future, który kończy się na głównym wątku serwera, gdy skończy się {@code source}. */
    public <T> CompletableFuture<T> toMain(CompletableFuture<T> source) {
        CompletableFuture<T> result = new CompletableFuture<>();
        source.whenComplete((value, error) -> {
            if (!isEnabled()) {
                result.completeExceptionally(error != null ? error
                        : new IllegalStateException("Plugin jest wyłączony"));
                return;
            }
            try {
                getServer().getScheduler().runTask(this, () -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        result.complete(value);
                    }
                });
            } catch (IllegalPluginAccessException ex) {
                result.completeExceptionally(ex);
            }
        });
        return result;
    }

    private ZoneId resolveZone(String name) {
        try {
            return ZoneId.of(name);
        } catch (DateTimeException e) {
            getLogger().warning("Nieprawidłowa strefa czasowa '" + name + "' w config.yml - używam strefy systemowej.");
            return ZoneId.systemDefault();
        }
    }

    public Messages getMessages() {
        return messages;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public DatabaseManager getDatabase() {
        return database;
    }

    public DeathHistoryManager getDeathHistory() {
        return deathHistory;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public GuiNavigator getNavigator() {
        return navigator;
    }
}
