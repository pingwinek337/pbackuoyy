package pl.pbackup.utils;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bezpieczna serializacja tablic {@link ItemStack} do bajtów (zachowuje wszystkie metadane:
 * zaklęcia, nazwy, lore, komponenty danych, zawartość shulkerów itd.).
 * <p>
 * Format: [int liczba_slotów] i dla każdego slotu [int długość lub -1 dla pustego][bajty itemu].
 */
public final class ItemSerializer {

    private static final int MAX_SLOTS = 1024;

    private ItemSerializer() {
    }

    public static byte[] serialize(ItemStack[] items) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null || item.isEmpty()) {
                    out.writeInt(-1);
                    continue;
                }
                byte[] bytes = item.serializeAsBytes();
                out.writeInt(bytes.length);
                out.write(bytes);
            }
        }
        return buffer.toByteArray();
    }

    /**
     * Odczytuje tablicę przedmiotów. Jeśli pojedynczy przedmiot jest uszkodzony, slot zostaje pusty,
     * a problem trafia do logu - pozostałe przedmioty są odzyskane.
     */
    public static ItemStack[] deserialize(byte[] data, Logger logger) throws IOException {
        if (data == null) {
            throw new IOException("Brak danych do odczytu");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int count = in.readInt();
            if (count < 0 || count > MAX_SLOTS) {
                throw new IOException("Nieprawidłowa liczba slotów: " + count);
            }
            ItemStack[] result = new ItemStack[count];
            for (int i = 0; i < count; i++) {
                int length = in.readInt();
                if (length == -1) {
                    continue;
                }
                if (length < 0 || length > data.length) {
                    throw new IOException("Nieprawidłowa długość przedmiotu: " + length);
                }
                byte[] bytes = new byte[length];
                in.readFully(bytes);
                try {
                    result[i] = ItemStack.deserializeBytes(bytes);
                } catch (RuntimeException ex) {
                    logger.log(Level.WARNING, "Nie udało się odczytać przedmiotu ze slotu " + i
                            + " - slot pominięty.", ex);
                }
            }
            return result;
        }
    }
}
