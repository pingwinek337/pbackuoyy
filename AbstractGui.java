package pl.pbackup.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import pl.pbackup.PBackup;

/**
 * Bazowa klasa wszystkich okien pluginu. Dzięki temu, że okno jest {@link InventoryHolder}em,
 * {@code InventoryListener} rozpoznaje je bez porównywania tytułów (odporne na podszywanie się).
 */
public abstract class AbstractGui implements InventoryHolder {

    protected final PBackup plugin;
    protected Inventory inventory;

    protected AbstractGui(PBackup plugin) {
        this.plugin = plugin;
    }

    /**
     * Obsługa kliknięcia w GÓRNY ekwipunek okna. Zdarzenie jest już anulowane przez listener.
     */
    public abstract void handleClick(Player viewer, InventoryClickEvent event, int slot);

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player viewer) {
        viewer.openInventory(inventory);
    }

    /** Wypełnia wszystkie puste sloty szybką tła. */
    protected void fillEmpty(Material material) {
        ItemStack filler = GuiItems.filler(material);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }
}
