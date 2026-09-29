package dev.icaro.foodtooltips.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;

/** Vanilla-texture glyphs supplied by IcarusTexture's {@code stats.png} font atlas. */
public final class StatIcons {
    public static final char HEALTH = '\uE050';
    public static final char DEFENSE = '\uE051';
    public static final char MANA = '\uE052';
    public static final char VITALITY = '\uE053';
    public static final char HEALTH_REGEN = '\uE054';
    public static final char TRUE_DEFENSE = '\uE055';
    public static final char STRENGTH = '\uE056';
    public static final char CRIT_CHANCE = '\uE057';
    public static final char CRIT_DAMAGE = '\uE058';
    public static final char ATTACK_SPEED = '\uE059';
    public static final char AGILITY = '\uE05A';
    public static final char INTELLIGENCE = '\uE05B';
    public static final char ABILITY_DAMAGE = '\uE05C';
    public static final char MENDING = '\uE05D';
    public static final char SWING_RANGE = '\uE05E';
    public static final char MINING_SPEED = '\uE05F';
    public static final char MINING_FORTUNE = '\uE060';
    public static final char FARMING_FORTUNE = '\uE061';
    public static final char FORAGING_FORTUNE = '\uE062';
    public static final char SWEEP = '\uE063';
    public static final char HEAT_RESISTANCE = '\uE064';
    public static final char CREAKING_SIGHT = '\uE065';
    public static final char SPEED = '\uE066';
    public static final char NIGHT_VISION = '\uE067';

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Map<String, Character> PREFIXES = prefixes();

    private StatIcons() {}

    public static Component text(String value, TextColor color) {
        return decorate(Component.text(value, color));
    }

    public static Component decorate(Component line) {
        String plain = PLAIN.serialize(line);
        if (plain.isEmpty() || isGlyph(plain.codePointAt(0))) {
            return line;
        }
        String cleaned = plain.replaceFirst("^[🏃🐇✹✦☠☣❤✎🛡⚔↔❉❣❋☘]+\\s*", "");
        Character glyph = glyphFor(cleaned);
        if (glyph == null) {
            return line;
        }
        Component body = Component.text(cleaned).style(line.style());
        return Component.text(glyph + " ", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .append(body);
    }

    public static boolean decorateLore(ItemMeta meta) {
        if (!meta.hasLore()) {
            return false;
        }
        List<Component> original = meta.lore();
        List<Component> updated = new ArrayList<>(original.size());
        boolean changed = false;
        for (Component line : original) {
            Component decorated = decorate(line);
            updated.add(decorated);
            changed |= decorated != line;
        }
        if (changed) {
            meta.lore(updated);
        }
        return changed;
    }

    private static Character glyphFor(String value) {
        for (Map.Entry<String, Character> entry : PREFIXES.entrySet()) {
            int labelAt = value.toLowerCase(java.util.Locale.ROOT)
                    .indexOf(entry.getKey().toLowerCase(java.util.Locale.ROOT));
            if (labelAt == 0 || labelAt > 0 && value.substring(0, labelAt)
                    .matches("[+-]?\\d+(?:\\.\\d+)?%?\\s+")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean isGlyph(int codePoint) {
        return codePoint >= HEALTH && codePoint <= NIGHT_VISION;
    }

    private static Map<String, Character> prefixes() {
        Map<String, Character> map = new LinkedHashMap<>();
        add(map, HEALTH_REGEN, "Regeneração de Vida", "Regen. de Vida", "Health Regeneration", "Health Regen");
        add(map, TRUE_DEFENSE, "Defesa Verdadeira", "True Defense", "Defesa (Reforja)", "Defense (Reforge)");
        add(map, CRIT_CHANCE, "Chance Crítica", "Crit Chance");
        add(map, CRIT_DAMAGE, "Dano Crítico", "Crit Damage");
        add(map, ATTACK_SPEED, "Velocidade de Ataque", "Attack Speed");
        add(map, ABILITY_DAMAGE, "Dano de Habilidade", "Ability Damage");
        add(map, SWING_RANGE, "Alcance de Ataque", "Swing Range");
        add(map, MINING_SPEED, "Mining Speed");
        add(map, MINING_FORTUNE, "Mining Fortune");
        add(map, FARMING_FORTUNE, "Farming Fortune");
        add(map, FORAGING_FORTUNE, "Foraging Fortune");
        add(map, HEAT_RESISTANCE, "Resistência ao Calor", "Heat Resistance");
        add(map, CREAKING_SIGHT, "Creaking Sight");
        add(map, NIGHT_VISION, "Visão Noturna", "Night Vision");
        add(map, VITALITY, "Vitalidade", "Vitality");
        add(map, INTELLIGENCE, "Inteligência", "Intelligence");
        add(map, STRENGTH, "Força", "Strength");
        add(map, AGILITY, "Agilidade", "Agility");
        add(map, MENDING, "Cura (Mending)", "Mending");
        add(map, DEFENSE, "Defesa", "Defense");
        add(map, HEALTH, "Vida", "Health");
        add(map, MANA, "Mana");
        add(map, SWEEP, "Sweep");
        add(map, SPEED, "Velocidade", "Speed");
        return map;
    }

    private static void add(Map<String, Character> map, char glyph, String... labels) {
        for (String label : labels) {
            map.put(label, glyph);
        }
    }
}
