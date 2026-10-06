package dev.icaro.foodtooltips.power;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;

/**
 * The 10 Powers from the player's own reference table (5 Starter, 5 Intermediate) - scope
 * explicitly confirmed with the player ("Só os 10 da tabela"), no other tier from the real
 * Hypixel Skyblock power list included. Base stat values transcribed directly from that table;
 * {@code 0.0} for a stat a given row's table entry doesn't list at all. Mirrors {@code
 * global.LevelColorCatalog}'s own {@code List.of(...)} + id-lookup-map shape.
 */
public final class PowerCatalog {
    private static final List<Power> POWERS = List.of(
            new Power("fortuitous", "Fortuitous", PowerType.STARTER, Material.EMERALD,
                    3.35, 1.2, 4.35, 0.0, 0.0, 4.8, 0.0, 0.0),
            new Power("pretty", "Pretty", PowerType.STARTER, Material.PINK_DYE,
                    1.65, 1.2, 4.8, 8.65, 0.475, 1.2, 18.8, 0.0),
            new Power("protected", "Protected", PowerType.STARTER, Material.SHIELD,
                    11.75, 18.8, 2.4, 0.0, 0.475, 1.2, 0.0, 0.0),
            new Power("simple", "Simple", PowerType.STARTER, Material.IRON_INGOT,
                    5.82, 3.6, 3.6, 1.2, 1.45, 3.6, 5.4, 0.0),
            new Power("warrior", "Warrior", PowerType.STARTER, Material.IRON_SWORD,
                    3.35, 1.2, 8.4, 0.0, 2.4, 6.0, 0.0, 0.0),
            new Power("commando", "Commando", PowerType.INTERMEDIATE, Material.CROSSBOW,
                    5.82, 2.4, 8.4, 0.0, 0.475, 8.4, 0.0, 0.0),
            new Power("disciplined", "Disciplined", PowerType.INTERMEDIATE, Material.GOLDEN_SWORD,
                    5.82, 2.4, 7.2, 0.0, 1.45, 7.2, 0.0, 0.0),
            new Power("inspired", "Inspired", PowerType.INTERMEDIATE, Material.ENCHANTED_BOOK,
                    1.65, 1.2, 8.4, 0.0, 0.95, 3.6, 16.2, 0.0),
            new Power("ominous", "Ominous", PowerType.INTERMEDIATE, Material.OMINOUS_BOTTLE,
                    5.82, 0.0, 0.0, 3.6, 1.45, 3.6, 6.1, 0.9),
            new Power("prepared", "Prepared", PowerType.INTERMEDIATE, Material.TOTEM_OF_UNDYING,
                    12.4, 11.3, 1.93, 0.0, 0.475, 0.95, 0.0, 0.0));
    private static final Map<String, Power> BY_ID;

    private PowerCatalog() {
    }

    public static List<Power> powers() {
        return POWERS;
    }

    public static Optional<Power> find(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    public static Power defaultPower() {
        return BY_ID.get("fortuitous");
    }

    static {
        LinkedHashMap<String, Power> map = new LinkedHashMap<>();
        for (Power power : POWERS) {
            map.put(power.id(), power);
        }
        BY_ID = Map.copyOf(map);
    }
}
