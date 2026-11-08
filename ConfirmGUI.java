package pl.pbackup.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import pl.pbackup.PBackup;
import pl.pbackup.utils.Text;

/**
 * Okno potwierdzenia (27 slotów). Akcję można zatwierdzić tylko raz - kolejne kliknięcia
 * (np. dwuklik) są ignorowane, więc nie dojdzie do przypadkowego podwójnego nadania.
 */
public final class ConfirmGUI extends AbstractGui {

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_CANCEL = 15;

    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean used;

    public ConfirmGUI(PBackup plugin, String title, ItemStack info, String confirmLabel,
                      Runnable onConfirm, Runnable onCancel) {
        super(plugin);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.inventory = Bukkit.createInventory(this, 27, Text.c(title));

        inventory.setItem(SLOT_CONFIRM, GuiItems.icon(Material.LIME_CONCRETE, "&a&l" + confirmLabel,
                "&7Kliknij, aby potwierdzić"));
        inventory.setItem(SLOT_INFO, info);
        inventory.setItem(SLOT_CANCEL, GuiItems.icon(Material.RED_CONCRETE, "&c&lAnuluj",
                "&7Kliknij, aby wrócić"));
        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public void handleClick(Player viewer, InventoryClickEvent event, int slot) {
        if (used) {
            return;
        }
        if (slot == SLOT_CONFIRM) {
            used = true;
            plugin.sync(() -> {
                viewer.closeInventory();
                onConfirm.run();
            });
        } else if (slot == SLOT_CANCEL) {
            used = true;
            plugin.sync(() -> {
                viewer.closeInventory();
                onCancel.run();
            });
        }
    }
}
