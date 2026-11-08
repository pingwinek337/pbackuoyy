package pl.pbackup.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import pl.pbackup.PBackup;
import pl.pbackup.managers.DeathHistoryManager;
import pl.pbackup.models.DeathPage;
import pl.pbackup.models.DeathRecord;
import pl.pbackup.models.TargetPlayer;
import pl.pbackup.utils.Permissions;
import pl.pbackup.utils.Text;

import java.util.List;

/** Lista śmierci gracza (54 sloty: 45 wpisów + dolny rząd nawigacji). */
public final class DeathHistoryGUI extends AbstractGui {

    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 47;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_DELETE_ALL = 51;
    private static final int SLOT_NEXT = 53;

    private final TargetPlayer target;
    private final DeathPage page;

    public DeathHistoryGUI(PBackup plugin, Player viewer, TargetPlayer target, DeathPage page) {
        super(plugin);
        this.target = target;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 54,
                Text.c("&8Historia śmierci &7» &f" + Text.escape(target.name())));

        boolean canGive = viewer.hasPermission(Permissions.GIVE);
        boolean canDelete = viewer.hasPermission(Permissions.DELETE);

        List<DeathRecord> records = page.records();
        for (int i = 0; i < records.size() && i < DeathHistoryManager.PAGE_SIZE; i++) {
            inventory.setItem(i, GuiItems.deathIcon(records.get(i), plugin.getZoneId(), true, canGive, canDelete));
        }

        if (page.page() > 0) {
            inventory.setItem(SLOT_PREV, GuiItems.icon(Material.ARROW, "&e« Poprzednia strona",
                    "&7Przejdź do strony &f" + page.page()));
        }
        inventory.setItem(SLOT_BACK, GuiItems.icon(Material.OAK_DOOR, "&cPowrót do menu"));

        ItemStack pageItem = GuiItems.icon(Material.PAPER,
                "&eStrona &f" + (page.page() + 1) + "&7/&f" + page.totalPages(),
                "&7Wszystkich śmierci: &f" + page.total());
        pageItem.setAmount(Math.min(64, page.page() + 1));
        inventory.setItem(SLOT_PAGE, pageItem);

        if (canDelete) {
            inventory.setItem(SLOT_DELETE_ALL, GuiItems.icon(Material.TNT, "&cUsuń całą historię",
                    "&7Trwale usuwa wszystkie śmierci tego gracza.",
                    "",
                    "&eKliknij, aby przejść do potwierdzenia"));
        }

        if (page.page() + 1 < page.totalPages()) {
            inventory.setItem(SLOT_NEXT, GuiItems.icon(Material.ARROW, "&eNastępna strona »",
                    "&7Przejdź do strony &f" + (page.page() + 2)));
        }

        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
        // puste miejsca na wpisy zostawiamy faktycznie puste (bez szyby), aby było widać koniec listy
        for (int i = records.size(); i < DeathHistoryManager.PAGE_SIZE; i++) {
            inventory.setItem(i, null);
        }
    }

    @Override
    public void handleClick(Player viewer, InventoryClickEvent event, int slot) {
        GuiNavigator nav = plugin.getNavigator();

        if (slot >= 0 && slot < DeathHistoryManager.PAGE_SIZE) {
            if (slot >= page.records().size()) {
                return;
            }
            DeathRecord death = page.records().get(slot);
            ClickType click = event.getClick();
            switch (click) {
                case LEFT, SHIFT_LEFT -> nav.confirmGive(viewer, target, death.getId(), page.page());
                case RIGHT -> nav.openPreview(viewer, target, death.getId(), page.page());
                case SHIFT_RIGHT -> nav.confirmDeleteDeath(viewer, target, death.getId(), page.page());
                default -> {
                }
            }
            return;
        }

        switch (slot) {
            case SLOT_PREV -> {
                if (page.page() > 0) {
                    nav.openHistory(viewer, target, page.page() - 1, false);
                }
            }
            case SLOT_NEXT -> {
                if (page.page() + 1 < page.totalPages()) {
                    nav.openHistory(viewer, target, page.page() + 1, false);
                }
            }
            case SLOT_BACK -> nav.openMainMenu(viewer, target);
            case SLOT_DELETE_ALL -> nav.confirmDeleteAll(viewer, target);
            default -> {
            }
        }
    }
}
