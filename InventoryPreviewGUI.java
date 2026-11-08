package pl.pbackup.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import pl.pbackup.PBackup;
import pl.pbackup.models.DeathRecord;
import pl.pbackup.models.TargetPlayer;
import pl.pbackup.utils.Permissions;
import pl.pbackup.utils.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Podgląd ekwipunku z chwili śmierci (54 sloty, TYLKO DO ODCZYTU).
 * <p>
 * Układ: sloty 0-26 = główny ekwipunek, 27-35 = pasek szybkiego dostępu (jak w grze),
 * 37-40 = zbroja (hełm, napierśnik, spodnie, buty), 42 = druga ręka,
 * 45 = powrót, 49 = informacje, 53 = nadanie kopii zapasowej.
 */
public final class InventoryPreviewGUI extends AbstractGui {

    private static final int SLOT_HELMET = 37;
    private static final int SLOT_CHEST = 38;
    private static final int SLOT_LEGS = 39;
    private static final int SLOT_BOOTS = 40;
    private static final int SLOT_OFFHAND = 42;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_GIVE = 53;

    private final TargetPlayer target;
    private final DeathRecord death;
    private final int returnPage;

    public InventoryPreviewGUI(PBackup plugin, Player viewer, TargetPlayer target, DeathRecord death, int returnPage) {
        super(plugin);
        this.target = target;
        this.death = death;
        this.returnPage = returnPage;
        this.inventory = Bukkit.createInventory(this, 54,
                Text.c("&8Ekwipunek &7» &f" + Text.escape(target.name()) + " &8#" + death.getId()));

        ItemStack[] storage = death.getStorage();
        // główny ekwipunek (indeksy 9-35) na slotach 0-26
        for (int slot = 0; slot < 27; slot++) {
            put(slot, at(storage, 9 + slot));
        }
        // hotbar (indeksy 0-8) na slotach 27-35
        for (int slot = 27; slot < 36; slot++) {
            put(slot, at(storage, slot - 27));
        }

        ItemStack[] armor = death.getArmor();
        placeEquipment(SLOT_HELMET, at(armor, 3), "Hełm");
        placeEquipment(SLOT_CHEST, at(armor, 2), "Napierśnik");
        placeEquipment(SLOT_LEGS, at(armor, 1), "Spodnie");
        placeEquipment(SLOT_BOOTS, at(armor, 0), "Buty");
        placeEquipment(SLOT_OFFHAND, death.getOffhand(), "Druga ręka");

        inventory.setItem(SLOT_BACK, GuiItems.icon(Material.OAK_DOOR, "&cPowrót do historii"));

        List<String> info = new ArrayList<>(GuiItems.deathLines(death, plugin.getZoneId()));
        info.add("");
        info.add("&8Podgląd tylko do odczytu");
        inventory.setItem(SLOT_INFO, GuiItems.icon(Material.EXPERIENCE_BOTTLE,
                "&6Śmierć #" + death.getId() + " &7- " + Text.escape(target.name()), info));

        if (viewer.hasPermission(Permissions.GIVE)) {
            inventory.setItem(SLOT_GIVE, GuiItems.icon(Material.EMERALD, "&aNadaj kopię zapasową",
                    "&7Przekaże graczowi ten ekwipunek i XP.",
                    "&7Można to zrobić wielokrotnie.",
                    "",
                    "&eKliknij, aby przejść do potwierdzenia"));
        }

        // tło dla pustych slotów dolnych rzędów (sloty 36+); pasek 0-35 zostawiamy z prawdziwymi (pustymi) slotami
        ItemStack filler = GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 36; i < 54; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private static ItemStack at(ItemStack[] array, int index) {
        if (array == null || index < 0 || index >= array.length) {
            return null;
        }
        return array[index];
    }

    private void put(int slot, ItemStack item) {
        if (item != null && !item.isEmpty()) {
            inventory.setItem(slot, item.clone());
        }
    }

    private void placeEquipment(int slot, ItemStack item, String label) {
        if (item != null && !item.isEmpty()) {
            inventory.setItem(slot, item.clone());
        } else {
            inventory.setItem(slot, GuiItems.icon(Material.GRAY_STAINED_GLASS_PANE, "&8Pusty slot: " + label));
        }
    }

    @Override
    public void handleClick(Player viewer, InventoryClickEvent event, int slot) {
        // Podgląd jest tylko do odczytu - reagujemy wyłącznie na przyciski.
        if (slot == SLOT_BACK) {
            plugin.getNavigator().openHistory(viewer, target, returnPage, false);
        } else if (slot == SLOT_GIVE) {
            plugin.getNavigator().confirmGive(viewer, target, death.getId(), returnPage);
        }
    }
}
