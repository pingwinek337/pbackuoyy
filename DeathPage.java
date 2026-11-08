package pl.pbackup.models;

import java.util.List;

/**
 * Jedna strona historii śmierci gracza.
 *
 * @param records    wpisy z tej strony (bez zawartości ekwipunku)
 * @param page       numer strony liczony od 0
 * @param totalPages łączna liczba stron (minimum 1)
 * @param total      łączna liczba śmierci gracza
 */
public record DeathPage(List<DeathRecord> records, int page, int totalPages, int total) {
}
