package pl.pbackup.utils;

import java.util.Locale;

/** Polskie nazwy przyczyn śmierci (klucze to nazwy {@code EntityDamageEvent.DamageCause}). */
public final class CauseNames {

    private CauseNames() {
    }

    public static String display(String cause) {
        String key = cause == null ? "UNKNOWN" : cause;
        return switch (key) {
            case "ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK" -> "Atak (walka)";
            case "PROJECTILE" -> "Pocisk";
            case "FALL" -> "Upadek z wysokości";
            case "FIRE", "FIRE_TICK" -> "Ogień";
            case "LAVA" -> "Lawa";
            case "DROWNING" -> "Utonięcie";
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> "Eksplozja";
            case "VOID" -> "Pustka";
            case "LIGHTNING" -> "Piorun";
            case "SUICIDE" -> "Samobójstwo";
            case "STARVATION" -> "Głód";
            case "POISON" -> "Trucizna";
            case "MAGIC" -> "Magia";
            case "WITHER" -> "Wither";
            case "FALLING_BLOCK" -> "Spadający blok";
            case "THORNS" -> "Ciernie";
            case "DRAGON_BREATH" -> "Oddech smoka";
            case "FLY_INTO_WALL" -> "Uderzenie w ścianę (elytra)";
            case "HOT_FLOOR" -> "Gorąca podłoga";
            case "CONTACT" -> "Kontakt z blokiem (np. kaktus)";
            case "SUFFOCATION" -> "Uduszenie";
            case "CRAMMING" -> "Ścisk (entity cramming)";
            case "FREEZE" -> "Zamarznięcie";
            case "SONIC_BOOM" -> "Sonic boom (Warden)";
            case "MELTING" -> "Stopienie";
            case "CAMPFIRE" -> "Ognisko";
            case "KILL" -> "Zabity komendą";
            case "WORLD_BORDER" -> "Granica świata";
            case "CUSTOM" -> "Inna (plugin)";
            case "UNKNOWN" -> "Nieznana";
            default -> key.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }
}
