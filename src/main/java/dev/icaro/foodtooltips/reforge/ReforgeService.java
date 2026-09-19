package dev.icaro.foodtooltips.reforge;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.item.SwordDamageService;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeaponService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Applies and tracks sword reforges (see {@link ReforgePrefix}) - the Blacksmith's own
 * gameplay logic, kept separate from {@link ReforgeMenuService} (screen/inventory plumbing
 * only, same split {@code EnchantService}/{@code EnchantMenuService} already use).
 *
 * <p>The tier reforged at is never a player choice - it's always {@code item}'s own tier ({@link
 * #tierOf}, the same {@code ItemTierService} rarity every item already carries, forced tier for
 * a legendary weapon included), so a Diamond Sword always costs Iron and rolls Tier C stats,
 * never anything else. Reforging costs one tier-specific material ({@link #costMaterial}) and
 * grants {@link #ATTEMPTS_PER_CHARGE} random rerolls at that tier before the next material is
 * needed - both the remaining rerolls and which tier they're for live on the item itself ({@link
 * #CHARGE_TIER_KEY}/{@link #ATTEMPTS_KEY}).
 *
 * <p>The rolled prefix's name is prepended to the item's display name as its own {@link
 * Component} wrapping the original name ({@link #applyName}) rather than by editing text -
 * that way the original name (and its {@code ItemTierService}-applied color/bold) never has
 * to be reconstructed or parsed back out of a rendered string, translatable material names
 * included. Its stat lines are inserted as a block, replaced whole on every reroll (see {@link
 * #LORE_COUNT_KEY}) - see {@link #applyLore} for exactly where that block goes on a plain sword
 * versus a legendary one.
 */
public final class ReforgeService {
    public static final int ATTEMPTS_PER_CHARGE = 5;
    private static final int LORE_INSERT_INDEX = 2;

    private static final NamespacedKey PREFIX_KEY = new NamespacedKey("foodtooltips", "reforge_prefix");
    private static final NamespacedKey TIER_KEY = new NamespacedKey("foodtooltips", "reforge_tier");
    private static final NamespacedKey CHARGE_TIER_KEY = new NamespacedKey("foodtooltips", "reforge_charge_tier");
    private static final NamespacedKey ATTEMPTS_KEY = new NamespacedKey("foodtooltips", "reforge_attempts");
    private static final NamespacedKey LORE_COUNT_KEY = new NamespacedKey("foodtooltips", "reforge_lore_count");

    private final ItemTierService tiers;

    public ReforgeService(Plugin plugin, ItemTierService tiers) {
        this.tiers = tiers;
    }

    // ---- Eligibility / cost ----

    /** Which tier {@code item} reforges at - always its own {@code ItemTierService} rarity, never a player choice. */
    public ItemTier tierOf(ItemStack item) {
        return this.tiers.tierOf(item);
    }

    /**
     * Sword-only for now (see {@link ReforgePrefix}'s class doc) - any {@code _SWORD}-material
     * item this plugin already gives its own flat damage total to, plain or legendary (every
     * legendary weapon, daggers and longswords included, is built on a {@code _SWORD} material -
     * see {@code LegendaryWeapon#material} - so this one check already covers all of them, no
     * per-{@code WeaponType} special-casing needed).
     */
    public boolean isReforgeable(ItemStack item) {
        return item != null && !item.isEmpty() && item.getType().name().endsWith("_SWORD")
                && SwordDamageService.totalDamage(item.getType()) != null;
    }

    public Material costMaterial(ItemTier tier) {
        return switch (tier) {
            case D -> Material.COAL;
            case C -> Material.IRON_INGOT;
            case B -> Material.GOLD_INGOT;
            case A -> Material.DIAMOND;
            case S, E -> Material.NETHERITE_SCRAP;
        };
    }

    /** Attempts left in {@code item}'s current charge, but only if that charge is for {@code item}'s own {@link #tierOf} - 0 for a stale charge (the item's tier can't actually change, but this stays consistent with {@link #reforge}'s own check) or no charge at all, meaning the next reforge pays for a fresh one. */
    public int attemptsRemaining(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        PersistentDataContainer d = meta.getPersistentDataContainer();
        if (!this.tierOf(item).name().equals(d.get(CHARGE_TIER_KEY, PersistentDataType.STRING))) {
            return 0;
        }
        return d.getOrDefault(ATTEMPTS_KEY, PersistentDataType.INTEGER, 0);
    }

    // ---- Applied stats (read by combat/stats code) ----

    /** The currently-applied reforge's stats for {@code item}, or all-zero if it's never been reforged. Safe to call on anything - not just a reforgeable sword. */
    public ReforgeStats statsOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return ReforgeStats.NONE;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return ReforgeStats.NONE;
        }
        PersistentDataContainer d = meta.getPersistentDataContainer();
        String prefixName = d.get(PREFIX_KEY, PersistentDataType.STRING);
        String tierName = d.get(TIER_KEY, PersistentDataType.STRING);
        if (prefixName == null || tierName == null) {
            return ReforgeStats.NONE;
        }
        try {
            return ReforgePrefix.valueOf(prefixName).stats(ItemTier.valueOf(tierName));
        } catch (IllegalArgumentException ex) {
            return ReforgeStats.NONE;
        }
    }

    // ---- Rolling ----

    public enum Outcome { SUCCESS, MISSING_MATERIAL }

    public record Result(Outcome outcome, ReforgePrefix prefix, ItemTier tier, int attemptsRemaining, Material missingMaterial) {
    }

    /**
     * Rolls one random {@link ReforgePrefix} onto {@code item} at its own {@link #tierOf},
     * charging the player one {@link #costMaterial} if the item has no attempts left in its
     * current charge (see the class doc). Mutates {@code item} in place (name, lore and PDC)
     * on success.
     */
    public Result reforge(Player player, ItemStack item) {
        ItemTier tier = this.tierOf(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return new Result(Outcome.MISSING_MATERIAL, null, tier, 0, this.costMaterial(tier));
        }
        PersistentDataContainer d = meta.getPersistentDataContainer();
        int attempts = tier.name().equals(d.get(CHARGE_TIER_KEY, PersistentDataType.STRING))
                ? d.getOrDefault(ATTEMPTS_KEY, PersistentDataType.INTEGER, 0) : 0;
        if (attempts <= 0) {
            Material cost = this.costMaterial(tier);
            if (!player.getInventory().containsAtLeast(new ItemStack(cost), 1)) {
                return new Result(Outcome.MISSING_MATERIAL, null, tier, 0, cost);
            }
            player.getInventory().removeItem(new ItemStack(cost, 1));
            attempts = ATTEMPTS_PER_CHARGE;
            d.set(CHARGE_TIER_KEY, PersistentDataType.STRING, tier.name());
        }
        ReforgePrefix[] all = ReforgePrefix.values();
        ReforgePrefix prefix = all[ThreadLocalRandom.current().nextInt(all.length)];
        // Unwraps any previous prefix (see #applyName) BEFORE PREFIX_KEY below is
        // overwritten with the new roll - it's what #applyName reads to tell "never
        // reforged before" (no wrapper to unwrap) apart from "rerolling" (unwrap first).
        this.applyName(item, meta, prefix);
        this.applyLore(item, meta, prefix.stats(tier), Language.of(player));
        attempts -= 1;
        d.set(ATTEMPTS_KEY, PersistentDataType.INTEGER, attempts);
        d.set(PREFIX_KEY, PersistentDataType.STRING, prefix.name());
        d.set(TIER_KEY, PersistentDataType.STRING, tier.name());
        item.setItemMeta(meta);
        return new Result(Outcome.SUCCESS, prefix, tier, attempts, null);
    }

    /**
     * Wraps the item's current name in a new prefix {@link Component} carrying the exact same
     * style (color/bold from {@code ItemTierService}) - see the class doc for why this never
     * needs to read the name back out as plain text. On a reroll, the previous prefix wrapper
     * (its single child is the true original name) is unwrapped first so prefixes never stack.
     */
    private void applyName(ItemStack item, ItemMeta meta, ReforgePrefix prefix) {
        Component current = meta.hasDisplayName() ? meta.displayName() : Component.translatable(item.getType().translationKey());
        boolean alreadyPrefixed = meta.getPersistentDataContainer().has(PREFIX_KEY, PersistentDataType.STRING);
        Component baseName = alreadyPrefixed && !current.children().isEmpty() ? current.children().get(0) : current;
        Component prefixText = Component.text(prefix.displayWord() + " ").style(current.style());
        meta.displayName(prefixText.append(baseName));
    }

    /**
     * Replaces whatever stat-line block this service last inserted (see {@link #LORE_COUNT_KEY})
     * with {@code stats}'s own lines. A plain sword's lore is a fixed, known shape ({@code
     * SwordDamageService} always puts Damage/Attack Speed at indices 0/1), so the block goes
     * right after those two; a legendary weapon's lore instead has several of its own lines at
     * indices {@code LegendaryWeaponService} tracks and rewrites by PDC-stored index ({@code
     * STRENGTH_LINE_KEY}/{@code SPEED_LINE_KEY}) - inserting anywhere before the end would shift
     * those stored indices out of sync with their real position, so for those this always
     * appends at the very end instead, never touching an index another system already owns.
     */
    private void applyLore(ItemStack item, ItemMeta meta, ReforgeStats stats, Language l) {
        PersistentDataContainer d = meta.getPersistentDataContainer();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        int previousCount = d.getOrDefault(LORE_COUNT_KEY, PersistentDataType.INTEGER, 0);
        boolean legendary = LegendaryWeaponService.isLegendary(item);
        int insertAt = legendary ? Math.max(0, lore.size() - previousCount) : Math.min(LORE_INSERT_INDEX, lore.size());
        for (int i = 0; i < previousCount && insertAt < lore.size(); i++) {
            lore.remove(insertAt);
        }
        List<Component> statLines = this.statLines(stats, l);
        lore.addAll(insertAt, statLines);
        meta.lore(lore);
        d.set(LORE_COUNT_KEY, PersistentDataType.INTEGER, statLines.size());
    }

    private List<Component> statLines(ReforgeStats stats, Language l) {
        List<Component> lines = new ArrayList<>();
        if (stats.strength() != 0) {
            lines.add(this.statLine(l.choose("Força: ", "Strength: "), stats.strength(), false, NamedTextColor.RED));
        }
        if (stats.critChance() != 0) {
            lines.add(this.statLine(l.choose("Chance Crítica: ", "Crit Chance: "), stats.critChance(), true, NamedTextColor.AQUA));
        }
        if (stats.critDamage() != 0) {
            lines.add(this.statLine(l.choose("Dano Crítico: ", "Crit Damage: "), stats.critDamage(), true, NamedTextColor.WHITE));
        }
        if (stats.intelligence() != 0) {
            lines.add(this.statLine(l.choose("Inteligência: ", "Intelligence: "), stats.intelligence(), false, NamedTextColor.AQUA));
        }
        if (stats.attackSpeed() != 0) {
            lines.add(this.statLine(l.choose("Velocidade de Ataque: ", "Attack Speed: "), stats.attackSpeed(), true, NamedTextColor.YELLOW));
        }
        return lines;
    }

    private Component statLine(String label, double value, boolean percent, NamedTextColor color) {
        String sign = value >= 0 ? "+" : "";
        String text = label + sign + this.trimmed(value) + (percent ? "%" : "");
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private String trimmed(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.1f", value);
    }
}
