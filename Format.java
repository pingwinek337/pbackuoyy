package pl.pbackup.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formatowanie dat. */
public final class Format {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private Format() {
    }

    public static String dateTime(long epochMillis, ZoneId zone) {
        return DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }
}
