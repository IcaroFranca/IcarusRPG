package dev.icaro.foodtooltips.potion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * A potion whose effect is one of this plugin's own stats rather than a vanilla effect - today the
 * Gravel Collection's Critical Potion. Its type, level and duration live in the item's PDC
 * ({@link #TYPE_KEY}/{@link #LEVEL_KEY}/{@link #DURATION_KEY}), so the generic potion upgrades work
 * on it like on a vanilla potion: Redstone sets its duration ({@link RedstoneDurationService}),
 * Glowstone raises its level ({@link GlowstoneLevelService}), and drinking it ({@code
 * item.GravelCollectionService#consume}) reads them back. {@link #describe} rebuilds its name and
 * lore from those values every time one changes.
 */
public final class CustomPotion {
    public static final NamespacedKey TYPE_KEY = new NamespacedKey("foodtooltips", "custom_potion_type");
    public static final NamespacedKey LEVEL_KEY = new NamespacedKey("foodtooltips", "custom_potion_level");
    public static final NamespacedKey DURATION_KEY = new NamespacedKey("foodtooltips", "custom_potion_duration");
    public static final String CRITICAL = "critical";
    /** Critical Potion's base duration - 3 minutes, like most vanilla potions, so Redstone (8/16/40 min) has room to matter. */
    public static final int CRITICAL_BASE_TICKS = 3 * 60 * 20;
    public static final int CRITICAL_MAX_LEVEL = 2;

    private CustomPotion() {
    }

    /** {@code item}'s custom type, or null for any other item. */
    public static String type(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(TYPE_KEY, PersistentDataType.STRING);
    }

    public static int level(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(LEVEL_KEY, PersistentDataType.INTEGER, 1);
    }

    public static int duration(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(DURATION_KEY, PersistentDataType.INTEGER, CRITICAL_BASE_TICKS);
    }

    public static int maxLevel(String type) {
        return CRITICAL.equals(type) ? CRITICAL_MAX_LEVEL : 1;
    }

    /** Critical Potion: +Crit Chance % at {@code level} - the player's own table (I: 10, II: 15). */
    public static double criticalChance(int level) {
        return level >= 2 ? 15.0 : 10.0;
    }

    /** Critical Potion: +Crit Damage % at {@code level} - the player's own table (I: 10, II: 20). */
    public static double criticalDamage(int level) {
        return level >= 2 ? 20.0 : 10.0;
    }

    /** Writes {@code type}/{@code level}/{@code ticks} into {@code meta} and rebuilds its name and lore to match. */
    public static void write(ItemMeta meta, String type, int level, int ticks) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(TYPE_KEY, PersistentDataType.STRING, type);
        pdc.set(LEVEL_KEY, PersistentDataType.INTEGER, level);
        pdc.set(DURATION_KEY, PersistentDataType.INTEGER, ticks);
        describe(meta);
    }

    /** Name + lore from the PDC values - "Critical Potion II", its stats and its duration. */
    public static void describe(ItemMeta meta) {
        String type = meta.getPersistentDataContainer().get(TYPE_KEY, PersistentDataType.STRING);
        if (!CRITICAL.equals(type)) {
            return;
        }
        int level = level(meta);
        meta.displayName(Component.text("Critical Potion" + (level >= 2 ? " II" : ""), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(line("+" + trim(criticalChance(level)) + "% Crit Chance", NamedTextColor.BLUE));
        lore.add(line("+" + trim(criticalDamage(level)) + "% Crit Damage", NamedTextColor.BLUE));
        lore.add(line("Duration: " + clock(duration(meta)), NamedTextColor.GRAY));
        meta.lore(lore);
    }

    /** "3:00" / "40:00" from ticks. */
    static String clock(int ticks) {
        int seconds = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
