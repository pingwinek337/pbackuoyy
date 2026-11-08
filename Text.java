package pl.pbackup.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** Pomocnicze metody do kolorowania tekstu (kody &amp;) przy użyciu Adventure. */
public final class Text {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /** Zamienia tekst z kodami & na komponent (bez domyślnej kursywy, ważne dla nazw i lore itemów). */
    public static Component c(String text) {
        return SERIALIZER.deserialize(text == null ? "" : text)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> lore(List<String> lines) {
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(c(line));
        }
        return result;
    }

    /** Usuwa znaki kolorujące z tekstu pochodzącego od gracza/moba, aby nie mógł wstrzykiwać formatowania. */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "").replace("§", "");
    }
}
