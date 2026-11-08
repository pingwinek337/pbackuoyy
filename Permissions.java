package pl.pbackup.utils;

/** Nazwy uprawnień pluginu. */
public final class Permissions {

    /** Dostęp do komendy i głównego menu. */
    public static final String USE = "pbackup.use";
    /** Przeglądanie historii i podgląd ekwipunku. */
    public static final String VIEW = "pbackup.view";
    /** Nadawanie kopii zapasowych. */
    public static final String GIVE = "pbackup.give";
    /** Usuwanie historii śmierci. */
    public static final String DELETE = "pbackup.delete";

    private Permissions() {
    }
}
