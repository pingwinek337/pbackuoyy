package pl.pbackup.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pl.pbackup.models.BackupRecord;
import pl.pbackup.models.DeathRecord;
import pl.pbackup.utils.CauseNames;
import pl.pbackup.utils.Format;
import pl.pbackup.utils.Text;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fabryka ikon używanych w GUI. */
public final class GuiItems {

    private GuiItems() {
    }

    public static ItemStack icon(Material material, String name, String... lore) {
        return icon(material, name, Arrays.asList(lore));
    }

    public static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.c(name));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(Text.lore(lore));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /** Pusty "wypełniacz" tła. */
    public static ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack head(OfflinePlayer player, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            meta.displayName(Text.c(name));
            meta.lore(Text.lore(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Ikona śmierci dobrana do przyczyny. */
    public static Material causeMaterial(DeathRecord death) {
        if (death.getKillerUuid() != null) {
            return Material.DIAMOND_SWORD; // zabity przez gracza
        }
        String cause = death.getCause() == null ? "UNKNOWN" : death.getCause();
        return switch (cause) {
            case "ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK", "THORNS" ->
                    death.getKillerName() != null ? Material.ZOMBIE_HEAD : Material.IRON_SWORD;
            case "PROJECTILE" -> Material.ARROW;
            case "FALL", "FLY_INTO_WALL" -> Material.FEATHER;
            case "FIRE", "FIRE_TICK", "CAMPFIRE" -> Material.BLAZE_POWDER;
            case "LAVA", "HOT_FLOOR", "MELTING" -> Material.LAVA_BUCKET;
            case "DROWNING" -> Material.WATER_BUCKET;
            case "VOID" -> Material.ENDER_PEARL;
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> Material.TNT;
            case "STARVATION" -> Material.ROTTEN_FLESH;
            case "CONTACT" -> Material.CACTUS;
            case "SUFFOCATION", "CRAMMING" -> Material.DIRT;
            case "FALLING_BLOCK" -> Material.ANVIL;
            case "LIGHTNING" -> Material.LIGHTNING_ROD;
            case "POISON", "MAGIC" -> Material.POTION;
            case "WITHER" -> Material.WITHER_SKELETON_SKULL;
            case "DRAGON_BREATH" -> Material.DRAGON_BREATH;
            case "FREEZE" -> Material.POWDER_SNOW_BUCKET;
            case "SUICIDE", "KILL" -> Material.BARRIER;
            default -> Material.SKELETON_SKULL;
        };
    }

    /** Linie opisujące śmierć (używane w liście, potwierdzeniu i podglądzie). */
    public static List<String> deathLines(DeathRecord death, ZoneId zone) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Data: &f" + Format.dateTime(death.getDeathTime(), zone));
        lore.add("&7Świat: &f" + Text.escape(death.getWorld()));
        lore.add("&7Koordynaty: &fX: " + Math.round(death.getX())
                + " Y: " + Math.round(death.getY())
                + " Z: " + Math.round(death.getZ()));
        lore.add("&7Przyczyna: &c" + CauseNames.display(death.getCause()));
        lore.add("&7Zabójca: " + (death.getKillerName() == null
                ? "&8brak" : "&c" + Text.escape(death.getKillerName())));
        lore.add("&7Poziom XP: &a" + death.getXpLevel() + " &8(" + death.getXpPoints() + " pkt)");
        lore.add("");

        List<BackupRecord> grants = death.getGrants();
        if (grants.isEmpty()) {
            lore.add("&7Kopia zapasowa: &cnie nadano");
        } else {
            lore.add("&7Kopia zapasowa: &anadano &7(razy: &f" + grants.size() + "&7)");
            int shown = Math.min(3, grants.size());
            for (int i = 0; i < shown; i++) {
                BackupRecord g = grants.get(i);
                lore.add("&8 • &f" + Text.escape(g.givenByName()) + " &8- &7"
                        + Format.dateTime(g.grantTime(), zone));
            }
            if (grants.size() > shown) {
                lore.add("&8 • ... i " + (grants.size() - shown) + " wcześniejszych");
            }
        }
        return lore;
    }

    /** Ikona jednej śmierci na liście historii. */
    public static ItemStack deathIcon(DeathRecord death, ZoneId zone, boolean showHints, boolean canGive,
                                      boolean canDelete) {
        List<String> lore = deathLines(death, zone);
        if (showHints) {
            lore.add("");
            if (canGive) {
                lore.add("&eLPM &7- nadaj kopię zapasową");
            }
            lore.add("&ePPM &7- podgląd ekwipunku");
            if (canDelete) {
                lore.add("&eSHIFT + PPM &7- usuń ten wpis");
            }
        }
        return icon(causeMaterial(death), "&c☠ &fŚmierć #" + death.getId(), lore);
    }
}
