package pl.pbackup.utils;

/** Przeliczanie poziomu i paska doświadczenia na łączną liczbę punktów XP (wzory z Minecrafta). */
public final class XpUtil {

    private XpUtil() {
    }

    public static int totalPoints(int level, float progress) {
        if (level < 0) {
            level = 0;
        }
        double base;
        int toNext;
        if (level <= 16) {
            base = (double) level * level + 6.0 * level;
            toNext = 2 * level + 7;
        } else if (level <= 31) {
            base = 2.5 * level * level - 40.5 * level + 360.0;
            toNext = 5 * level - 38;
        } else {
            base = 4.5 * level * level - 162.5 * level + 2220.0;
            toNext = 9 * level - 158;
        }
        float clamped = Math.max(0f, Math.min(1f, progress));
        double total = Math.round(base) + Math.round(clamped * toNext);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, total));
    }
}
