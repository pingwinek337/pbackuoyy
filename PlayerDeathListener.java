package pl.pbackup.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.PlayerInventory;
import pl.pbackup.PBackup;
import pl.pbackup.models.DeathRecord;
import pl.pbackup.utils.XpUtil;

import java.util.UUID;
import java.util.logging.Level;

/** Automatyczny zapis każdej śmierci gracza. */
public final class PlayerDeathListener implements Listener {

    private final PBackup plugin;

    public PlayerDeathListener(PBackup plugin) {
        this.plugin = plugin;
    }

    /**
     * MONITOR: widzimy ostateczny wynik zdarzenia. W tym momencie ekwipunek gracza jest jeszcze pełny
     * (serwer czyści go dopiero po zakończeniu zdarzenia), więc zapisujemy dokładny stan z chwili śmierci.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (event.getKeepInventory()
                && plugin.getConfig().getBoolean("settings.skip-keep-inventory-deaths", true)) {
            return;
        }

        try {
            PlayerInventory inventory = player.getInventory();
            Location location = player.getLocation();

            EntityDamageEvent lastDamage = player.getLastDamageCause();
            String cause = lastDamage != null ? lastDamage.getCause().name() : "UNKNOWN";

            String killerName = null;
            UUID killerUuid = null;
            Entity killer = event.getDamageSource().getCausingEntity();
            if (killer == null) {
                killer = player.getKiller();
            }
            if (killer != null && !killer.getUniqueId().equals(player.getUniqueId())) {
                killerName = killer.getName();
                if (killer instanceof Player) {
                    killerUuid = killer.getUniqueId();
                }
            }

            int level = player.getLevel();
            float progress = player.getExp();

            DeathRecord record = new DeathRecord(
                    0L,
                    player.getUniqueId(),
                    player.getName(),
                    System.currentTimeMillis(),
                    location.getWorld() != null ? location.getWorld().getName() : "nieznany",
                    location.getX(), location.getY(), location.getZ(),
                    cause,
                    killerName,
                    killerUuid,
                    level,
                    progress,
                    XpUtil.totalPoints(level, progress),
                    inventory.getStorageContents(),
                    inventory.getArmorContents(),
                    inventory.getItemInOffHand());

            // recordDeath serializuje ekwipunek od razu (na tym wątku); do pliku zapisuje asynchronicznie
            plugin.getDeathHistory().recordDeath(record).whenComplete((id, error) -> {
                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE, "Nie udało się zapisać śmierci gracza "
                            + player.getName() + " (" + player.getUniqueId() + ")", error);
                }
            });
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd podczas zapisu śmierci gracza " + player.getName(), e);
        }
    }
}
