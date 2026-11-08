package pl.pbackup.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import pl.pbackup.PBackup;
import pl.pbackup.gui.AbstractGui;

/**
 * Zabezpiecza wszystkie okna pluginu: każde kliknięcie, przeciągnięcie, shift-click, klawisze numeryczne,
 * wyrzucanie (Q / klik poza oknem) i zamianę z drugą ręką są anulowane. Okno jest rozpoznawane po
 * {@code InventoryHolder}, a nie po tytule.
 */
public final class InventoryListener implements Listener {

    private final PBackup plugin;

    public InventoryListener(PBackup plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof AbstractGui gui)) {
            return;
        }

        // Anulujemy KAŻDE kliknięcie, gdy nasze okno jest otwarte (także w dolnym ekwipunku gracza,
        // bo shift-click / klawisze numeryczne mogłyby przenieść przedmioty do/z okna).
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            plugin.sync(player::updateInventory);
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked != top) {
            return;
        }
        int slot = event.getSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        gui.handleClick(player, event, slot);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof AbstractGui) {
            event.setCancelled(true);
        }
    }
}
