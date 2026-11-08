package pl.pbackup.models;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Zapis jednej śmierci gracza.
 * <p>
 * Rekord "podsumowania" (z listy historii) nie zawiera ekwipunku - {@link #hasInventory()} zwraca wtedy false.
 * Pełny rekord (wczytany po id) zawiera przedmioty z chwili śmierci.
 */
public final class DeathRecord {

    private final long id;
    private final UUID playerUuid;
    private final String playerName;
    private final long deathTime;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final String cause;
    private final String killerName;
    private final UUID killerUuid;
    private final int xpLevel;
    private final float xpProgress;
    private final int xpPoints;

    /** 36 slotów (hotbar 0-8, reszta 9-35). */
    private final ItemStack[] storage;
    /** 4 sloty: 0 buty, 1 spodnie, 2 napierśnik, 3 hełm. */
    private final ItemStack[] armor;
    private final ItemStack offhand;

    private List<BackupRecord> grants = List.of();

    public DeathRecord(long id, UUID playerUuid, String playerName, long deathTime, String world,
                       double x, double y, double z, String cause, String killerName, UUID killerUuid,
                       int xpLevel, float xpProgress, int xpPoints,
                       ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.deathTime = deathTime;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.cause = cause;
        this.killerName = killerName;
        this.killerUuid = killerUuid;
        this.xpLevel = xpLevel;
        this.xpProgress = xpProgress;
        this.xpPoints = xpPoints;
        this.storage = storage;
        this.armor = armor;
        this.offhand = offhand;
    }

    /** Zwraca kopię rekordu z dołączonym ekwipunkiem (i listą nadań z oryginału). */
    public DeathRecord withInventory(ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
        DeathRecord copy = new DeathRecord(id, playerUuid, playerName, deathTime, world, x, y, z, cause,
                killerName, killerUuid, xpLevel, xpProgress, xpPoints, storage, armor, offhand);
        copy.grants = this.grants;
        return copy;
    }

    public boolean hasInventory() {
        return storage != null && armor != null;
    }

    public long getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getDeathTime() {
        return deathTime;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public String getCause() {
        return cause;
    }

    public String getKillerName() {
        return killerName;
    }

    public UUID getKillerUuid() {
        return killerUuid;
    }

    public int getXpLevel() {
        return xpLevel;
    }

    public float getXpProgress() {
        return xpProgress;
    }

    public int getXpPoints() {
        return xpPoints;
    }

    public ItemStack[] getStorage() {
        return storage;
    }

    public ItemStack[] getArmor() {
        return armor;
    }

    public ItemStack getOffhand() {
        return offhand;
    }

    /** Historia nadań kopii zapasowej, od najnowszego. */
    public List<BackupRecord> getGrants() {
        return grants;
    }

    public void setGrants(List<BackupRecord> grants) {
        this.grants = grants == null ? List.of() : List.copyOf(grants);
    }
}
