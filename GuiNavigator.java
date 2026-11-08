package pl.pbackup.gui;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.pbackup.PBackup;
import pl.pbackup.managers.BackupManager;
import pl.pbackup.managers.DeathHistoryManager;
import pl.pbackup.models.DeathRecord;
import pl.pbackup.models.TargetPlayer;
import pl.pbackup.utils.Messages;
import pl.pbackup.utils.Permissions;
import pl.pbackup.utils.Text;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

/**
 * Nawigacja między oknami oraz przepływy akcji (nadanie kopii, usuwanie).
 * Każda akcja ponownie sprawdza uprawnienia i dane (UUID), niezależnie od tego, jak gracz doszedł do przycisku.
 * Wszystkie callbacki wykonują się na głównym wątku serwera.
 */
public final class GuiNavigator {

    private final PBackup plugin;
    private final Messages msg;
    private final DeathHistoryManager history;
    private final BackupManager backup;

    public GuiNavigator(PBackup plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessages();
        this.history = plugin.getDeathHistory();
        this.backup = plugin.getBackupManager();
    }

    // ------------------------------------------------------------------ wyszukiwanie gracza

    /** Szuka gracza: online -> znany serwerowi (cache) -> zapisany w historii śmierci. */
    public CompletableFuture<Optional<TargetPlayer>> resolveTarget(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(
                    Optional.of(new TargetPlayer(online.getUniqueId(), online.getName())));
        }
        OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(name);
        if (cached != null) {
            String cachedName = cached.getName() != null ? cached.getName() : name;
            return CompletableFuture.completedFuture(Optional.of(new TargetPlayer(cached.getUniqueId(), cachedName)));
        }
        return history.findByName(name);
    }

    // ------------------------------------------------------------------ okna

    public void openMainMenu(Player viewer, TargetPlayer target) {
        if (!viewer.hasPermission(Permissions.USE)) {
            msg.send(viewer, Messages.NO_PERMISSION);
            return;
        }
        history.countDeaths(target.uuid()).whenComplete((count, error) -> {
            if (error != null) {
                fail(viewer, error);
                return;
            }
            if (!viewer.isOnline()) {
                return;
            }
            new MainMenuGUI(plugin, viewer, target, count).open(viewer);
        });
    }

    /**
     * Otwiera stronę historii.
     *
     * @param menuIfEmpty gdy gracz nie ma żadnych śmierci: true = wróć do głównego menu, false = zostań gdzie jesteś
     */
    public void openHistory(Player viewer, TargetPlayer target, int page, boolean menuIfEmpty) {
        if (!viewer.hasPermission(Permissions.VIEW)) {
            msg.send(viewer, Messages.NO_PERMISSION);
            return;
        }
        history.getPage(target.uuid(), page).whenComplete((data, error) -> {
            if (error != null) {
                fail(viewer, error);
                return;
            }
            if (!viewer.isOnline()) {
                return;
            }
            if (data.total() == 0) {
                msg.send(viewer, Messages.NO_DEATHS);
                if (menuIfEmpty) {
                    openMainMenu(viewer, target);
                }
                return;
            }
            new DeathHistoryGUI(plugin, viewer, target, data).open(viewer);
        });
    }

    public void openPreview(Player viewer, TargetPlayer target, long deathId, int returnPage) {
        if (!viewer.hasPermission(Permissions.VIEW)) {
            msg.send(viewer, Messages.NO_PERMISSION);
            return;
        }
        history.getFull(deathId).whenComplete((opt, error) -> {
            if (error != null) {
                fail(viewer, error);
                return;
            }
            if (!viewer.isOnline()) {
                return;
            }
            if (opt.isEmpty()) {
                msg.send(viewer, Messages.DEATH_NOT_FOUND);
                return;
            }
            DeathRecord death = opt.get();
            if (!death.getPlayerUuid().equals(target.uuid())) {
                msg.send(viewer, Messages.UUID_MISMATCH);
                return;
            }
            new InventoryPreviewGUI(plugin, viewer, target, death, returnPage).open(viewer);
        });
    }

    // ------------------------------------------------------------------ nadawanie kopii

    public void confirmGive(Player viewer, TargetPlayer target, long deathId, int returnPage) {
        if (!viewer.hasPermission(Permissions.GIVE)) {
            msg.send(viewer, Messages.NO_PERMISSION);
            return;
        }
        if (plugin.getServer().getPlayer(target.uuid()) == null) {
            msg.send(viewer, Messages.TARGET_OFFLINE, target.name());
            return;
        }
        history.getFull(deathId).whenComplete((opt, error) -> {
            if (error != null) {
                fail(viewer, error);
                return;
            }
            if (!viewer.isOnline()) {
                return;
            }
            if (opt.isEmpty()) {
                msg.send(viewer, Messages.DEATH_NOT_FOUND);
                return;
            }
            DeathRecord death = opt.get();
            if (!death.getPlayerUuid().equals(target.uuid())) {
                msg.send(viewer, Messages.UUID_MISMATCH);
                return;
            }
            List<String> lore = GuiItems.deathLines(death, plugin.getZoneId());
            lore.add("");
            lore.add("&6Gracz &f" + Text.escape(target.name()) + " &6otrzyma przedmioty i XP z tej śmierci.");
            lore.add("&7Kopię można nadać wielokrotnie.");
            ItemStack info = GuiItems.icon(GuiItems.causeMaterial(death),
                    "&eNadać kopię zapasową ze śmierci #" + death.getId() + "?", lore);

            new ConfirmGUI(plugin, "&8Potwierdź nadanie kopii", info, "Nadaj kopię",
                    () -> executeGive(viewer, target, deathId, returnPage),
                    () -> {
                        msg.send(viewer, Messages.CANCELLED);
                        openHistory(viewer, target, returnPage, false);
                    }).open(viewer);
        });
    }

    private void executeGive(Player viewer, TargetPlayer target, long deathId, int returnPage) {
        // Uprawnienia i dane sprawdzamy ponownie w chwili wykonania.
        if (!viewer.isOnline()) {
            return;
        }
        if (!viewer.hasPermission(Permissions.GIVE)) {
            msg.send(viewer, Messages.NO_PERMISSION);
            return;
        }
        history.getFull(deathId).whenComplete((opt, error) -> {
            if (error != null) {
                fail(viewer, error);
                return;
            }
            if (!viewer.isOnline()) {
                return;
            }
            if (opt.isEmpty()) {
                msg.send(viewer, Messages.DEATH_NOT_FOUND);
                return;
            }
            DeathRecord death = opt.get();
            if (!death.getPlayerUuid().equals(target.uuid())) {
                msg.send(viewer, Messages.UUID_MISMATCH);
                return;
            }
            Player online = plugin.getServer().getPlayer(target.uuid());
            if (online == null) {
                msg.send(viewer, Messages.TARGET_OFFLINE, target.name());
                return;
            }

            BackupManager.GiveResult result;
            try {
                result = backup.give(online, death);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Nie udało się nadać kopii zapasowej ze śmierci #" + deathId, e);
                msg.send(viewer, Messages.DB_ERROR);
                return;
            }

            plugin.getLogger().info("Kopia zapasowa: " + viewer.getName() + " (" + viewer.getUniqueId()
                    + ") nadał śmierć #" + death.getId() + " graczowi " + online.getName() + " ("
                    + online.getUniqueId() + "); przedmioty=" + result.itemsGiven()
                    + ", upuszczone=" + result.itemsDropped() + ", xp=" + result.xpRestored());

            msg.send(viewer, Messages.BACKUP_GIVEN_ADMIN, death.getId(), online.getName(),
                    result.itemsGiven(), result.itemsDropped(), result.xpRestored());
            if (result.itemsDropped() > 0) {
                msg.send(viewer, Messages.DROPPED_NOTICE);
            }
            if (plugin.getConfig().getBoolean("settings.notify-target", true) && !online.equals(viewer)) {
                msg.send(online, Messages.BACKUP_GIVEN_TARGET, death.getId(), viewer.getName());
                if (result.itemsDropped() > 0) {
                    msg.send(online, Messages.DROPPED_NOTICE);
                }
            }

            backup.recordGrant(death, viewer, online, result).whenComplete((id, grantError) -> {
                if (grantError != null) {
                    plugin.getLogger().log(Level.SEVERE, "Kopia została nadana (śmierć #" + death.getId()
                            + "), ale nie udało się zapisać wpisu nadania w bazie.", unwrap(grantError));
                    if (viewer.isOnline()) {
                        msg.send(viewer, Messages.GRANT_LOG_FAILED);
                    }
                }
                if (viewer.isOnline()) {
                    openHistory(viewer, target, returnPage, true);
                }
            });
        });
    }

    // ------------------------------------------------------------------ usuwanie

    public void confirmDeleteDeath(Player viewer, TargetPlayer target, long deathId, int returnPage) {
        if (!viewer.hasPermission(Permissions.DELETE)) {
            msg.send(viewer, Messages.NO_PERMISSION);
            return;
        }
        history.getFull(deathId).whenComplete((opt, error) -> {
            if (error != null) {
                fail(viewer, error);
                return;
            }
            if (!viewer.isOnline()) {
                return;
            }
            if (opt.isEmpty()) {
                msg.send(viewer, Messages.DEATH_NOT_FOUND);
                return;
            }
            DeathRecord death = opt.get();
            if (!death.getPlayerUuid().equals(target.uuid())) {
                msg.send(viewer, Messages.UUID_MISMATCH);
                return;
            }
            List<String> lore = GuiItems.deathLines(death, plugin.getZoneId());
            lore.add("");
            lore.add("&cTa operacja jest nieodwracalna!");
            ItemStack info = GuiItems.icon(GuiItems.causeMaterial(death),
                    "&eUsunąć wpis śmierci #" + death.getId() + "?", lore);

            new ConfirmGUI(plugin, "&8Potwierdź usunięcie wpisu", info, "Usuń wpis",
                    () -> executeDeleteDeath(viewer, target, deathId, returnPage),
                    () -> {
                        msg.send(viewer, Messages.CANCELLED);
                        openHistory(viewer, target, returnPage, false);
                    }).open(viewer);
        });
    }

    private void executeDeleteDeath(Player viewer, TargetPlayer target, long deathId, int returnPage) {
        if (!viewer.isOnline()) {
            return;
        }
        if (!viewer.hasPermission(Permissions.DELETE)) {
            msg.send(viewer, Messages.NO_PERMISSION);
            return;
        }
        history.deleteDeath(deathId, target.uuid()).whenComplete((deleted, error) -> {
            if (error != null) {
                fail(viewer, error);
                return;
            }
            if (deleted) {
                plugin.getLogger().info("Usunięcie historii: " + viewer.getName() + " (" + viewer.getUniqueId()
                        + ") usunął śmierć #" + deathId + " gracza " + target.name() + " (" + target.uuid() + ")");
                if (viewer.isOnline()) {
                    msg.send(viewer, Messages.DELETED_ONE, deathId);
                }
            } else if (viewer.isOnline()) {
                msg.send(viewer, Messages.DEATH_NOT_FOUND);
            }
            if (viewer.isOnline()) {
                openHistory(viewer, target, returnPage, true);
            }
        });
    }

    public void confirmDeleteAll(Player viewer, TargetPlayer target) {
        if (!viewer.hasPermission(Permissions.DELETE)) {
            msg.send(viewer, Messages.NO_PERMISSION);
            return;
        }
        history.countDeaths(target.uuid()).whenComplete((count, error) -> {
            if (error != null) {
                fail(viewer, error);
                return;
            }
            if (!viewer.isOnline()) {
                return;
            }
            if (count == 0) {
                msg.send(viewer, Messages.NO_DEATHS);
                return;
            }
            ItemStack info = GuiItems.icon(org.bukkit.Material.TNT,
                    "&eUsunąć CAŁĄ historię gracza " + Text.escape(target.name()) + "?",
                    "&7Liczba wpisów do usunięcia: &f" + count,
                    "&7Razem z historią nadań kopii zapasowych.",
                    "",
                    "&cTa operacja jest nieodwracalna!");
            new ConfirmGUI(plugin, "&8Potwierdź usunięcie historii", info, "Usuń wszystko",
                    () -> executeDeleteAll(viewer, target),
                    () -> {
                        msg.send(viewer, Messages.CANCELLED);
                        openMainMenu(viewer, target);
                    }).open(viewer);
        });
    }

    private void executeDeleteAll(Player viewer, TargetPlayer target) {
        if (!viewer.isOnline()) {
            return;
        }
        if (!viewer.hasPermission(Permissions.DELETE)) {
            msg.send(viewer, Messages.NO_PERMISSION);
            return;
        }
        history.deleteAll(target.uuid()).whenComplete((removed, error) -> {
            if (error != null) {
                fail(viewer, error);
                return;
            }
            plugin.getLogger().info("Usunięcie historii: " + viewer.getName() + " (" + viewer.getUniqueId()
                    + ") usunął CAŁĄ historię gracza " + target.name() + " (" + target.uuid()
                    + "), wpisów: " + removed);
            if (viewer.isOnline()) {
                msg.send(viewer, Messages.DELETED_ALL, target.name(), removed);
                openMainMenu(viewer, target);
            }
        });
    }

    // ------------------------------------------------------------------ błędy

    public void fail(Player viewer, Throwable error) {
        plugin.getLogger().log(Level.SEVERE, "Błąd bazy danych / operacji PBackup", unwrap(error));
        if (viewer.isOnline()) {
            msg.send(viewer, Messages.DB_ERROR);
        }
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}
