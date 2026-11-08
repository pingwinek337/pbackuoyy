package pl.pbackup.managers;

import org.bukkit.inventory.ItemStack;
import pl.pbackup.PBackup;
import pl.pbackup.database.DatabaseManager;
import pl.pbackup.models.DeathPage;
import pl.pbackup.models.DeathRecord;
import pl.pbackup.models.TargetPlayer;
import pl.pbackup.utils.ItemSerializer;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Logika historii śmierci. Wszystkie zwracane {@link CompletableFuture} kończą się na głównym wątku serwera,
 * więc w callbackach można bezpiecznie używać API Bukkita.
 */
public final class DeathHistoryManager {

    /** Liczba śmierci na jednej stronie GUI (5 rzędów po 9 slotów; dolny rząd to nawigacja). */
    public static final int PAGE_SIZE = 45;

    private final PBackup plugin;
    private final DatabaseManager database;

    public DeathHistoryManager(PBackup plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Zapisuje śmierć. Ekwipunek jest serializowany SYNCHRONICZNIE (na wątku wywołującym),
     * zanim serwer zdąży go wyczyścić - dopiero zapis do pliku odbywa się asynchronicznie.
     */
    public CompletableFuture<Long> recordDeath(DeathRecord record) {
        byte[] storage;
        byte[] armor;
        byte[] offhand;
        try {
            storage = ItemSerializer.serialize(record.getStorage());
            armor = ItemSerializer.serialize(record.getArmor());
            offhand = ItemSerializer.serialize(new ItemStack[]{record.getOffhand()});
        } catch (IOException | RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return plugin.toMain(database.insertDeath(record, storage, armor, offhand));
    }

    public CompletableFuture<Integer> countDeaths(UUID owner) {
        return plugin.toMain(database.countDeaths(owner));
    }

    public CompletableFuture<DeathPage> getPage(UUID owner, int page) {
        return plugin.toMain(database.loadPage(owner, page, PAGE_SIZE));
    }

    /** Wczytuje pełny wpis śmierci wraz z ekwipunkiem (deserializacja na głównym wątku). */
    public CompletableFuture<Optional<DeathRecord>> getFull(long deathId) {
        return plugin.toMain(database.loadDeath(deathId)).thenApply(opt -> {
            if (opt.isEmpty()) {
                return Optional.<DeathRecord>empty();
            }
            DatabaseManager.StoredDeath stored = opt.get();
            try {
                ItemStack[] storage = ItemSerializer.deserialize(stored.storage(), plugin.getLogger());
                ItemStack[] armor = ItemSerializer.deserialize(stored.armor(), plugin.getLogger());
                ItemStack[] offhandArr = ItemSerializer.deserialize(stored.offhand(), plugin.getLogger());
                ItemStack offhand = offhandArr.length > 0 ? offhandArr[0] : null;
                return Optional.of(stored.meta().withInventory(storage, armor, offhand));
            } catch (IOException e) {
                throw new IllegalStateException("Uszkodzone dane ekwipunku dla śmierci #" + deathId, e);
            }
        });
    }

    public CompletableFuture<Boolean> deleteDeath(long deathId, UUID owner) {
        return plugin.toMain(database.deleteDeath(deathId, owner));
    }

    public CompletableFuture<Integer> deleteAll(UUID owner) {
        return plugin.toMain(database.deleteAllDeaths(owner));
    }

    public CompletableFuture<Optional<TargetPlayer>> findByName(String name) {
        return plugin.toMain(database.findPlayerByName(name));
    }
}
