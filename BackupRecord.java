package pl.pbackup.models;

import java.util.UUID;

/**
 * Pojedyncze nadanie kopii zapasowej z danej śmierci.
 *
 * @param id           identyfikator wpisu nadania
 * @param deathId      identyfikator śmierci, z której nadano kopię
 * @param givenByName  nick osoby nadającej
 * @param givenByUuid  UUID osoby nadającej
 * @param targetUuid   UUID gracza, który otrzymał kopię
 * @param grantTime    czas nadania (epoch millis)
 * @param itemsGiven   liczba przekazanych przedmiotów (suma sztuk)
 * @param itemsDropped liczba przedmiotów upuszczonych, bo zabrakło miejsca
 * @param xpRestored   liczba przywróconych punktów doświadczenia
 */
public record BackupRecord(
        long id,
        long deathId,
        String givenByName,
        UUID givenByUuid,
        UUID targetUuid,
        long grantTime,
        int itemsGiven,
        int itemsDropped,
        int xpRestored
) {
}
