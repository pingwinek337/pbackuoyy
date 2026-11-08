package pl.pbackup.utils;

import org.bukkit.command.CommandSender;

import java.util.Locale;

/** Komunikaty pluginu (po polsku) wraz z prefiksem z config.yml. */
public final class Messages {

    public static final String NO_PERMISSION = "&cNie masz uprawnień do wykonania tej operacji.";
    public static final String ONLY_PLAYERS = "&cTej komendy może użyć tylko gracz (otwiera GUI).";
    public static final String USAGE = "&eUżycie: &f/pbackup <nick_gracza>";
    public static final String INVALID_NICK = "&cNieprawidłowy nick gracza.";
    public static final String PLAYER_NOT_FOUND =
            "&cNie znaleziono gracza &f%s&c. Gracz musi być online, znany serwerowi lub mieć zapisaną śmierć.";
    public static final String DB_ERROR = "&cWystąpił błąd bazy danych. Szczegóły znajdziesz w konsoli serwera.";
    public static final String NO_DEATHS = "&eTen gracz nie ma żadnych zapisanych śmierci.";
    public static final String DEATH_NOT_FOUND = "&cNie znaleziono wpisu śmierci (mógł zostać usunięty).";
    public static final String UUID_MISMATCH =
            "&cBłąd: wpis śmierci nie należy do tego gracza (niezgodny UUID). Operację przerwano.";
    public static final String TARGET_OFFLINE =
            "&cGracz &f%s &cmusi być online, aby otrzymać kopię zapasową.";
    public static final String BACKUP_GIVEN_ADMIN =
            "&aNadano kopię zapasową ze śmierci &f#%d &agraczowi &f%s&a. Przedmiotów: &f%d&a, upuszczonych (brak miejsca): &f%d&a, XP: &f%d&a.";
    public static final String BACKUP_GIVEN_TARGET =
            "&aOtrzymałeś kopię zapasową ekwipunku ze śmierci &f#%d &aod &f%s&a.";
    public static final String DROPPED_NOTICE =
            "&eNie wszystko zmieściło się w ekwipunku - część przedmiotów upuszczono pod nogami gracza.";
    public static final String GRANT_LOG_FAILED =
            "&eKopia została nadana, ale nie udało się zapisać wpisu w historii nadań (sprawdź konsolę).";
    public static final String DELETED_ONE = "&aUsunięto wpis śmierci &f#%d&a.";
    public static final String DELETED_ALL = "&aUsunięto całą historię śmierci gracza &f%s &a(wpisów: &f%d&a).";
    public static final String CANCELLED = "&7Operacja anulowana.";

    private final String prefix;

    public Messages(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    /** Wysyła wiadomość z prefiksem. Argumenty tekstowe są oczyszczone ze znaków formatujących. */
    public void send(CommandSender target, String template, Object... args) {
        target.sendMessage(Text.c(prefix + format(template, args)));
    }

    public static String format(String template, Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }
        Object[] safe = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            safe[i] = args[i] instanceof String s ? Text.escape(s) : args[i];
        }
        return String.format(Locale.ROOT, template, safe);
    }
}
