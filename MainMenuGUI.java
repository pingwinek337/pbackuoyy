package pl.pbackup.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import pl.pbackup.PBackup;
import pl.pbackup.models.TargetPlayer;
import pl.pbackup.utils.Permissions;
import pl.pbackup.utils.Text;

import java.util.List;

/** Główne menu (27 slotów). */
public final class MainMenuGUI extends AbstractGui {

    private static final int SLOT_HISTORY = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_DELETE_ALL = 15;
    private static final int SLOT_CLOSE = 22;

    private final TargetPlayer target;

    public MainMenuGUI(PBackup plugin, Player viewer, TargetPlayer target, int deathCount) {
        super(plugin);
        this.target = target;
        this.inventory = Bukkit.createInventory(this, 27, Text.c("&8PBackup &7» &f" + Text.escape(target.name())));

        boolean online = Bukkit.getPlayer(target.uuid()) != null;

        inventory.setItem(SLOT_HISTORY, GuiItems.icon(Material.WRITABLE_BOOK, "&6Historia śmierci",
                "&7Zapisanych śmierci: &f" + deathCount,
                "",
                "&eKliknij, aby otworzyć listę"));

        inventory.setItem(SLOT_INFO, GuiItems.head(plugin.getServer().getOfflinePlayer(target.uuid()),
                "&f" + Text.escape(target.name()),
                List.of("&7UUID: &8" + target.uuid(),
                        "&7Status: " + (online ? "&aonline" : "&coffline"),
                        "&7Śmierci w historii: &f" + deathCount)));

        if (viewer.hasPermission(Permissions.DELETE)) {
            inventory.setItem(SLOT_DELETE_ALL, GuiItems.icon(Material.TNT, "&cUsuń całą historię",
                    "&7Trwale usuwa wszystkie śmierci",
                    "&7i historię nadań tego gracza.",
                    "",
                    "&eKliknij, aby przejść do potwierdzenia"));
        }

        inventory.setItem(SLOT_CLOSE, GuiItems.icon(Material.BARRIER, "&cZamknij"));
        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public void handleClick(Player viewer, InventoryClickEvent event, int slot) {
        switch (slot) {
            case SLOT_HISTORY -> plugin.getNavigator().openHistory(viewer, target, 0, false);
            case SLOT_DELETE_ALL -> plugin.getNavigator().confirmDeleteAll(viewer, target);
            case SLOT_CLOSE -> plugin.sync(viewer::closeInventory);
            default -> {
            }
        }
    }
}
