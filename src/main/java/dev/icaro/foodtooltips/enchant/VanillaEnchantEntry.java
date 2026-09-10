package dev.icaro.foodtooltips.enchant;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;

/**
 * Wraps a real vanilla {@link Enchantment} as an {@link EnchantEntry}, so the
 * reworked Enchanting Table screen can offer every vanilla enchantment
 * (Sharpness, Protection, Unbreaking, ...) explicitly alongside the plugin's own -
 * see {@code EnchantService#allEntries}. Names come straight from Minecraft's own
 * translations (resolved client-side, so they match whatever locale the player's
 * client is set to) rather than a hand-maintained PT/EN list.
 */
final class VanillaEnchantEntry implements EnchantEntry {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    /**
     * Flat XP-level cost per level, matching {@link IcarusEnchant}'s own
     * "costPerLevel * level" shape - Bukkit's {@link Enchantment} doesn't carry a
     * per-enchant cost of its own, so every vanilla entry shares this one default.
     * Deliberately cheap relative to the custom enchants (3-5): there are far more
     * vanilla entries competing for the same tier-based slots.
     */
    private static final int COST_PER_LEVEL = 2;

    private final Enchantment enchantment;

    VanillaEnchantEntry(Enchantment enchantment) {
        this.enchantment = enchantment;
    }

    Enchantment enchantment() {
        return this.enchantment;
    }

    @Override
    public String id() {
        return "vanilla:" + this.enchantment.getKey();
    }

    @Override
    public String catalogName(boolean pt) {
        // No "bare" (level-independent) name in Bukkit's API - level 1's leveled name is
        // the closest fit, and already omits the numeral entirely for maxLevel-1 entries.
        return PLAIN.serialize(this.enchantment.displayName(1));
    }

    @Override
    public String leveledName(boolean pt, int level) {
        return PLAIN.serialize(this.enchantment.displayName(level));
    }

    @Override
    public int maxLevel() {
        return this.enchantment.getMaxLevel();
    }

    @Override
    public int costAtLevel(int level) {
        return COST_PER_LEVEL * level;
    }

    @Override
    public String description(boolean pt) {
        return null;
    }

    @Override
    public String formattedValue(int level) {
        return "";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VanillaEnchantEntry other && other.enchantment.equals(this.enchantment);
    }

    @Override
    public int hashCode() {
        return this.enchantment.hashCode();
    }
}
