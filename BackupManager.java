package pl.pbackup.managers;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import pl.pbackup.PBackup;
import pl.pbackup.database.DatabaseManager;
import pl.pbackup.models.BackupRecord;
import pl.pbackup.models.DeathRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Nadawanie kopii zapasowych. Działa w trybie "dodaj": przedmioty ze śmierci są DODAWANE do obecnego
 * ekwipunku gracza (nigdy nie nadpisują ani nie kasują tego, co gracz ma teraz). Zbroja i offhand trafiają
 * na swoje sloty, jeśli są wolne - w przeciwnym razie do zwykłego ekwipunku. Co się nie zmieści, spada
 * pod nogi gracza, więc nic nie ginie.
 */
public final class BackupManager {

    /** Wynik nadania kopii. */
    public record GiveResult(int itemsGiven, int itemsDropped, int xpRestored) {
    }

    private final PBackup plugin;
    private final DatabaseManager database;

    public BackupManager(PBackup plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Przekazuje graczowi ekwipunek ze śmierci. Musi być wywołane na głównym wątku.
     * Wszystkie przedmioty są klonowane, więc dane w pamięci pluginu nie mogą się zduplikować.
     */
    public GiveResult give(Player target, DeathRecord death) {
        if (!death.getPlayerUuid().equals(target.getUniqueId())) {
            throw new IllegalArgumentException("UUID gracza nie zgadza się z UUID zapisanej śmierci");
        }
        if (!death.hasInventory()) {
            throw new IllegalArgumentException("Wpis śmierci nie zawiera ekwipunku");
        }

        PlayerInventory inventory = target.getInventory();
        List<ItemStack> pending = new ArrayList<>();
        int totalAmount = 0;

        // Zbroja: 0 buty, 1 spodnie, 2 napierśnik, 3 hełm
        ItemStack[] armor = death.getArmor();
        for (int i = 0; i < 4 && i < armor.length; i++) {
            ItemStack item = armor[i];
            if (isEmpty(item)) {
                continue;
            }
            ItemStack copy = item.clone();
            totalAmount += copy.getAmount();
            if (isEmpty(getArmor(inventory, i))) {
                setArmor(inventory, i, copy);
            } else {
                pending.add(copy);
            }
        }

        // Druga ręka
        ItemStack offhand = death.getOffhand();
        if (!isEmpty(offhand)) {
            ItemStack copy = offhand.clone();
            totalAmount += copy.getAmount();
            if (isEmpty(inventory.getItemInOffHand())) {
                inventory.setItemInOffHand(copy);
            } else {
                pending.add(copy);
            }
        }

        // Reszta ekwipunku
        for (ItemStack item : death.getStorage()) {
            if (isEmpty(item)) {
                continue;
            }
            ItemStack copy = item.clone();
            totalAmount += copy.getAmount();
            pending.add(copy);
        }

        int dropped = 0;
        if (!pending.isEmpty()) {
            Map<Integer, ItemStack> leftover = inventory.addItem(pending.toArray(new ItemStack[0]));
            for (ItemStack item : leftover.values()) {
                dropped += item.getAmount();
                target.getWorld().dropItem(target.getLocation(), item);
            }
        }

        int xpRestored = 0;
        if (plugin.getConfig().getBoolean("settings.restore-xp", true) && death.getXpPoints() > 0) {
            String mode = plugin.getConfig().getString("settings.xp-mode", "ADD");
            if ("SET".equalsIgnoreCase(mode)) {
                target.setLevel(0);
                target.setExp(0f);
            }
            target.giveExp(death.getXpPoints());
            xpRestored = death.getXpPoints();
        }

        return new GiveResult(Math.max(0, totalAmount - dropped), dropped, xpRestored);
    }

    /** Zapisuje w bazie fakt nadania kopii (kto, komu, kiedy, ile). */
    public CompletableFuture<Long> recordGrant(DeathRecord death, Player giver, Player target, GiveResult result) {
        BackupRecord record = new BackupRecord(0L, death.getId(), giver.getName(), giver.getUniqueId(),
                target.getUniqueId(), System.currentTimeMillis(),
                result.itemsGiven(), result.itemsDropped(), result.xpRestored());
        return plugin.toMain(database.insertGrant(record));
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    private static ItemStack getArmor(PlayerInventory inventory, int index) {
        return switch (index) {
            case 0 -> inventory.getBoots();
            case 1 -> inventory.getLeggings();
            case 2 -> inventory.getChestplate();
            default -> inventory.getHelmet();
        };
    }

    private static void setArmor(PlayerInventory inventory, int index, ItemStack item) {
        switch (index) {
            case 0 -> inventory.setBoots(item);
            case 1 -> inventory.setLeggings(item);
            case 2 -> inventory.setChestplate(item);
            default -> inventory.setHelmet(item);
        }
    }
}
