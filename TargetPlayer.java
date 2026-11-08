package pl.pbackup.models;

import java.util.UUID;

/**
 * Gracz, którego historię przeglądamy (może być online lub offline).
 * Wszystkie operacje opierają się na UUID, a nick służy tylko do wyświetlania.
 */
public record TargetPlayer(UUID uuid, String name) {
}
