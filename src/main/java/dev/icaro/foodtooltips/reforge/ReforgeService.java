package dev.icaro.foodtooltips.reforge;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.item.SwordDamageService;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeaponService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Applies and tracks both sword reforges ({@link ReforgePrefix}) and armor reforges
 * ({@link ArmorReforgePrefix}) - the Blacksmith's own gameplay logic, kept separate from
 * {@link ReforgeMenuService} (screen/inventory plumbing only, same split {@code
 * EnchantService}/{@code EnchantMenuService} already use).
 *
 * <p>The tier reforged at is never a player choice - it's always {@code item}'s own tier
 * ({@link #tierOf}, the same {@code ItemTierService} rarity every item already carries,
 * forced tier for a legendary weapon included), so a Diamond Sword always costs Iron and
 * rolls Tier C stats, never anything else. Reforging costs one tier-specific material
 * ({@link #costMaterial}) and grants {@link #ATTEMPTS_PER_CHARGE} random rerolls at that
 * tier before the next material is needed - both the remaining rerolls and which tier
 * they're for live on the item itself ({@link #CHARGE_TIER_KEY}/{@link #ATTEMPTS_KEY}),
 * shared by both catalogs since one item is always only ever one or the other.
 *
 * <p>The rolled prefix's name is prepended to the item's display name as its own {@link
 * Component} wrapping the original name ({@link #applyName}) rather than by editing text -
 * that way the original name (and its {@code ItemTierService}-applied color/bold) never has
 * to be reconstructed or parsed back out of a rendered string, translatable material names
 * included. Stat lines are inserted as a block, replaced whole on every reroll (see {@link
 * #LORE_COUNT_KEY}) - see {@link #applySwordLore}/{@link #applyArmorLore} for exactly where
 * each catalog's block goes.
 *
 * <p>Armor's Health and Agility (Speed) bonuses are flat and player-level-independent, so
 * they're baked directly into the piece's own attribute modifiers once, at reforge time
 * (see {@link #applyArmorAttributeModifiers}) - Bukkit already sums an equipped item's own
 * modifiers into the real attribute automatically, no periodic reapplication needed, exactly
 * like a legendary weapon's own Agility bonus ({@code LegendaryWeaponService#create}). Attack
 * Speed is the one exception - it's a percentage of the wielder's Combat-level-derived
 * Attack Speed rating, which changes independently of the item, so it needs the same
 * periodic recompute-if-changed treatment {@code SwordDamageService} gives a sword's own
 * Attack Speed bonus - see {@link #applyArmorAttackSpeedModifiers}. Every other armor stat
 * (Defense, Strength, Crit Chance, Crit Damage, Intelligence) isn't a real vanilla attribute
 * at all in this plugin, so it's summed on demand from whatever's currently equipped (see
 * {@link #totalArmorStats}) by whichever system already owns that stat's formula
 * ({@code ArmorDefenseService}, {@code CombatListener}, {@code PlayerStatsService}).
 */
public final class ReforgeService {
    public static final int ATTEMPTS_PER_CHARGE = 5;
    private static final int SWORD_LORE_INSERT_INDEX = 2;
    /** 1 point of Agility -&gt; +1% of vanilla's base Movement Speed (0.1) while worn - same constant {@code LegendaryWeaponService} uses for a held weapon's own Agility bonus, duplicated here rather than shared since the two classes otherwise have no reason to depend on each other. */
    private static final double AGILITY_SPEED_PER_POINT = 0.001;

    private static final NamespacedKey PREFIX_KEY = new NamespacedKey("foodtooltips", "reforge_prefix");
    private static final NamespacedKey TIER_KEY = new NamespacedKey("foodtooltips", "reforge_tier");
    private static final NamespacedKey CHARGE_TIER_KEY = new NamespacedKey("foodtooltips", "reforge_charge_tier");
    private static final NamespacedKey ATTEMPTS_KEY = new NamespacedKey("foodtooltips", "reforge_attempts");
    private static final NamespacedKey LORE_COUNT_KEY = new NamespacedKey("foodtooltips", "reforge_lore_count");
    private static final NamespacedKey ARMOR_PREFIX_KEY = new NamespacedKey("foodtooltips", "armor_reforge_prefix");
    private static final NamespacedKey ARMOR_TIER_KEY = new NamespacedKey("foodtooltips", "armor_reforge_tier");
    private static final NamespacedKey ARMOR_HEALTH_KEY = new NamespacedKey("foodtooltips", "armor_reforge_health");
    private static final NamespacedKey ARMOR_AGILITY_KEY = new NamespacedKey("foodtooltips", "armor_reforge_agility");
    private static final NamespacedKey ARMOR_SPEED_KEY = new NamespacedKey("foodtooltips", "armor_reforge_attack_speed");

    private final ItemTierService tiers;
    private final CombatSkillService combat;
    /** Wired after construction ({@code LegendaryWeaponService} itself depends on this class for its own Attack Speed reforge bonus, so the reverse reference can't be a constructor param without a cycle) - see {@link #applySwordLore}. */
    private LegendaryWeaponService legendary;

    public ReforgeService(Plugin plugin, ItemTierService tiers, CombatSkillService combat) {
        this.tiers = tiers;
        this.combat = combat;
    }

    public void legendary(LegendaryWeaponService legendary) {
        this.legendary = legendary;
    }

    // ---- Eligibility / cost ----

    /** Which tier {@code item} reforges at - always its own {@code ItemTierService} rarity, never a player choice. */
    public ItemTier tierOf(ItemStack item) {
        return this.tiers.tierOf(item);
    }

    /** A plain or legendary sword (see {@link ReforgePrefix}'s class doc) or an armor piece (see {@link ArmorReforgePrefix}'s) - the only two gear kinds with a reforge table today. */
    public boolean isReforgeable(ItemStack item) {
        return this.isSword(item) || isArmorPiece(item);
    }

    /** Any {@code _SWORD}-material item this plugin already gives its own flat damage total to, plain or legendary (every legendary weapon, daggers and longswords included, is built on a {@code _SWORD} material - see {@code LegendaryWeapon#material} - so this one check already covers all of them, no per-{@code WeaponType} special-casing needed). */
    private boolean isSword(ItemStack item) {
        return item != null && !item.isEmpty() && item.getType().name().endsWith("_SWORD")
                && SwordDamageService.totalDamage(item.getType()) != null;
    }

    /** A helmet, chestplate, leggings or boots of any material - no legendary armor exists in this plugin yet, so unlike swords there's no separate check to fold in. */
    public static boolean isArmorPiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        return armorSlot(item.getType()) != null;
    }

    private static EquipmentSlotGroup armorSlot(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) return EquipmentSlotGroup.HEAD;
        if (name.endsWith("_CHESTPLATE")) return EquipmentSlotGroup.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlotGroup.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlotGroup.FEET;
        return null;
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

    /** The currently-applied sword reforge's stats for {@code item}, or all-zero if it's never been reforged (or isn't a sword). Safe to call on anything. */
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

    /** The currently-applied armor reforge's stats for {@code item}, or all-zero if it's never been reforged (or isn't armor). Safe to call on anything. */
    public ArmorReforgeStats armorStatsOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return ArmorReforgeStats.NONE;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return ArmorReforgeStats.NONE;
        }
        PersistentDataContainer d = meta.getPersistentDataContainer();
        String prefixName = d.get(ARMOR_PREFIX_KEY, PersistentDataType.STRING);
        String tierName = d.get(ARMOR_TIER_KEY, PersistentDataType.STRING);
        if (prefixName == null || tierName == null) {
            return ArmorReforgeStats.NONE;
        }
        try {
            return ArmorReforgePrefix.valueOf(prefixName).stats(ItemTier.valueOf(tierName));
        } catch (IllegalArgumentException ex) {
            return ArmorReforgeStats.NONE;
        }
    }

    /** Sum of {@link #armorStatsOf} across {@code player}'s currently equipped helmet, chestplate, leggings and boots - what {@code ArmorDefenseService}/{@code CombatListener}/{@code PlayerStatsService} each read for their own stat's formula. */
    public ArmorReforgeStats totalArmorStats(Player player) {
        PlayerInventory inv = player.getInventory();
        ArmorReforgeStats total = ArmorReforgeStats.NONE;
        total = total.plus(this.armorStatsOf(inv.getHelmet()));
        total = total.plus(this.armorStatsOf(inv.getChestplate()));
        total = total.plus(this.armorStatsOf(inv.getLeggings()));
        total = total.plus(this.armorStatsOf(inv.getBoots()));
        return total;
    }

    // ---- Rolling ----

    public enum Outcome { SUCCESS, MISSING_MATERIAL }

    public record Result(Outcome outcome, String prefixWord, ItemTier tier, int attemptsRemaining, Material missingMaterial) {
    }

    /**
     * Rolls a random reforge onto {@code item} at its own {@link #tierOf} - a sword rolls
     * {@link ReforgePrefix}, an armor piece rolls {@link ArmorReforgePrefix} (see {@link
     * #isReforgeable}) - charging the player one {@link #costMaterial} if the item has no
     * attempts left in its current charge (see the class doc). Mutates {@code item} in place
     * (name, lore, attributes and PDC) on success.
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
        attempts -= 1;
        d.set(ATTEMPTS_KEY, PersistentDataType.INTEGER, attempts);
        Language language = Language.of(player);
        String prefixWord;
        if (this.isArmorPiece(item)) {
            ArmorReforgePrefix[] all = ArmorReforgePrefix.values();
            ArmorReforgePrefix prefix = all[ThreadLocalRandom.current().nextInt(all.length)];
            this.applyName(item, meta, prefix.displayWord(), ARMOR_PREFIX_KEY);
            this.applyArmorLore(meta, prefix.stats(tier), language);
            this.applyArmorAttributeModifiers(item, meta, prefix.stats(tier));
            d.set(ARMOR_PREFIX_KEY, PersistentDataType.STRING, prefix.name());
            d.set(ARMOR_TIER_KEY, PersistentDataType.STRING, tier.name());
            prefixWord = prefix.displayWord();
        } else {
            ReforgePrefix[] all = ReforgePrefix.values();
            ReforgePrefix prefix = all[ThreadLocalRandom.current().nextInt(all.length)];
            this.applyName(item, meta, prefix.displayWord(), PREFIX_KEY);
            this.applySwordLore(item, meta, prefix.stats(tier), language);
            d.set(PREFIX_KEY, PersistentDataType.STRING, prefix.name());
            d.set(TIER_KEY, PersistentDataType.STRING, tier.name());
            prefixWord = prefix.displayWord();
        }
        item.setItemMeta(meta);
        return new Result(Outcome.SUCCESS, prefixWord, tier, attempts, null);
    }

    /**
     * Wraps the item's current name in a new prefix {@link Component} carrying the exact same
     * style (color/bold from {@code ItemTierService}) - see the class doc for why this never
     * needs to read the name back out as plain text. On a reroll, the previous prefix wrapper
     * (its single child is the true original name) is unwrapped first so prefixes never stack -
     * {@code alreadyPrefixedKey} is whichever catalog's own prefix PDC key this item already
     * carries (checked BEFORE {@link #reforge} overwrites it with the new roll).
     */
    private void applyName(ItemStack item, ItemMeta meta, String prefixWord, NamespacedKey alreadyPrefixedKey) {
        Component current = meta.hasDisplayName() ? meta.displayName() : Component.translatable(item.getType().translationKey());
        boolean alreadyPrefixed = meta.getPersistentDataContainer().has(alreadyPrefixedKey, PersistentDataType.STRING);
        Component baseName = alreadyPrefixed && !current.children().isEmpty() ? current.children().get(0) : current;
        Component prefixText = Component.text(prefixWord + " ").style(current.style());
        meta.displayName(prefixText.append(baseName));
    }

    /**
     * Replaces whatever stat-line block this service last inserted (see {@link #LORE_COUNT_KEY})
     * with {@code stats}'s own lines, right after the item's own Attack Speed line - a plain
     * sword's is always at index 1 ({@code SwordDamageService}), a legendary weapon's own index
     * varies per weapon ({@link LegendaryWeaponService#speedLineIndex}). Attack Speed itself
     * never gets a line here even when {@code stats.attackSpeed()} is nonzero - see {@link
     * #swordStatLines}. Inserting after (never before) that line means it never needs to move, but a
     * legendary weapon's separately-tracked Strength-scaling line ({@code STRENGTH_LINE_KEY},
     * Two as One / Kamish's Wrath) can sit right after it and would drift out of sync with this
     * block's size changing on every reroll - {@link LegendaryWeaponService#shiftStrengthLineIfAtOrAfter}
     * keeps that index correct.
     */
    private void applySwordLore(ItemStack item, ItemMeta meta, ReforgeStats stats, Language l) {
        Integer legendarySpeedIndex = this.legendary == null ? null : this.legendary.speedLineIndex(item);
        int fallbackIndex = SWORD_LORE_INSERT_INDEX;
        int insertAt = legendarySpeedIndex != null ? legendarySpeedIndex + 1 : fallbackIndex;
        int previousCount = this.replaceLoreBlock(meta, insertAt, this.swordStatLines(stats, l));
        if (legendarySpeedIndex != null) {
            int inserted = meta.getPersistentDataContainer().getOrDefault(LORE_COUNT_KEY, PersistentDataType.INTEGER, 0);
            this.legendary.shiftStrengthLineIfAtOrAfter(meta, insertAt, inserted - previousCount);
        }
    }

    /** Replaces the reforge stat-line block right after the armor piece's own "Defesa: +N" line ({@code ArmorDefenseService#tooltip}, always index 0 when present) - or right at the start if that line is somehow missing (a piece with 0 base Defense, e.g. a Turtle Shell reused as a helmet skin, never gets one). */
    private void applyArmorLore(ItemMeta meta, ArmorReforgeStats stats, Language l) {
        this.replaceLoreBlock(meta, 1, this.armorStatLines(stats, l));
    }

    /**
     * Shared block-replace primitive both {@link #applySwordLore}/{@link #applyArmorLore} use:
     * removes the {@link #LORE_COUNT_KEY} lines this service inserted last time (starting right
     * at {@code insertAt}, which never moves between rerolls for either catalog) and inserts
     * {@code newLines} in their place. Returns the previous block's size, so a caller that also
     * needs to shift some other tracked index (see {@link #applySwordLore}) can compute the net
     * size change itself.
     */
    private int replaceLoreBlock(ItemMeta meta, int insertAt, List<Component> newLines) {
        PersistentDataContainer d = meta.getPersistentDataContainer();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        int previousCount = d.getOrDefault(LORE_COUNT_KEY, PersistentDataType.INTEGER, 0);
        int at = Math.min(insertAt, lore.size());
        for (int i = 0; i < previousCount && at < lore.size(); i++) {
            lore.remove(at);
        }
        lore.addAll(at, newLines);
        meta.lore(lore);
        d.set(LORE_COUNT_KEY, PersistentDataType.INTEGER, newLines.size());
        return previousCount;
    }

    /** Every stat line except Attack Speed, which instead merges into the item's own existing Attack Speed line as a "(+X%)" suffix (see {@code SwordDamageService}/{@code LegendaryWeaponService#speedLine}) rather than getting a redundant line of its own here. */
    private List<Component> swordStatLines(ReforgeStats stats, Language l) {
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
        return lines;
    }

    /**
     * Every armor stat gets its own line, Defense included - unlike a sword's Attack Speed,
     * merging into {@code ArmorDefenseService}'s own "Defesa: +N" line would mean making that
     * one-shot tooltip pass reforge-aware (it only ever rewrites once per item today), so this
     * uses a distinctly-labeled "Defesa (Reforja)" line instead of touching that system.
     */
    private List<Component> armorStatLines(ArmorReforgeStats stats, Language l) {
        List<Component> lines = new ArrayList<>();
        if (stats.health() != 0) {
            lines.add(this.statLine(l.choose("Vida: ", "Health: "), stats.health(), false, NamedTextColor.RED));
        }
        if (stats.defense() != 0) {
            lines.add(this.statLine(l.choose("Defesa (Reforja): ", "Defense (Reforge): "), stats.defense(), false, NamedTextColor.GREEN));
        }
        if (stats.strength() != 0) {
            lines.add(this.statLine(l.choose("Força: ", "Strength: "), stats.strength(), false, NamedTextColor.RED));
        }
        if (stats.critChance() != 0) {
            lines.add(this.statLine(l.choose("Chance Crítica: ", "Crit Chance: "), stats.critChance(), true, NamedTextColor.AQUA));
        }
        if (stats.critDamage() != 0) {
            lines.add(this.statLine(l.choose("Dano Crítico: ", "Crit Damage: "), stats.critDamage(), true, NamedTextColor.WHITE));
        }
        if (stats.agility() != 0) {
            lines.add(this.statLine(l.choose("Agilidade: ", "Agility: "), stats.agility(), false, NamedTextColor.WHITE));
        }
        if (stats.attackSpeed() != 0) {
            lines.add(this.statLine(l.choose("Velocidade de Ataque: ", "Attack Speed: "), stats.attackSpeed(), true, NamedTextColor.YELLOW));
        }
        if (stats.intelligence() != 0) {
            lines.add(this.statLine(l.choose("Inteligência: ", "Intelligence: "), stats.intelligence(), false, NamedTextColor.AQUA));
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

    /**
     * Bakes {@code stats}'s flat Health and Agility bonuses directly onto the piece's own
     * attribute modifiers, scoped to its own equipment slot - both are constant regardless of
     * the wielder's level, so unlike Attack Speed ({@link #applyArmorAttackSpeedModifiers})
     * this only ever needs to run once, here at reforge time, exactly like a legendary weapon's
     * own Agility bonus ({@code LegendaryWeaponService#create}). Called with the new roll's
     * stats every time (including a reroll), so a stat this roll doesn't grant correctly drops
     * whatever modifier a previous roll left behind instead of stacking.
     */
    private void applyArmorAttributeModifiers(ItemStack item, ItemMeta meta, ArmorReforgeStats stats) {
        EquipmentSlotGroup slot = armorSlot(item.getType());
        if (slot == null) {
            return;
        }
        this.removeModifier(meta, Attribute.MAX_HEALTH, ARMOR_HEALTH_KEY);
        if (stats.health() != 0) {
            meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(ARMOR_HEALTH_KEY, stats.health(), AttributeModifier.Operation.ADD_NUMBER, slot));
        }
        this.removeModifier(meta, Attribute.MOVEMENT_SPEED, ARMOR_AGILITY_KEY);
        if (stats.agility() != 0) {
            meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                    new AttributeModifier(ARMOR_AGILITY_KEY, stats.agility() * AGILITY_SPEED_PER_POINT, AttributeModifier.Operation.ADD_NUMBER, slot));
        }
    }

    private void removeModifier(ItemMeta meta, Attribute attribute, NamespacedKey key) {
        if (!meta.hasAttributeModifiers()) {
            return;
        }
        for (AttributeModifier m : meta.getAttributeModifiers(attribute)) {
            if (m.getKey().equals(key)) {
                meta.removeAttributeModifier(attribute, m);
                return;
            }
        }
    }

    /**
     * Refreshes every equipped armor piece's own real Attack Speed modifier (see the class doc
     * on why this is the one armor reforge stat that can't just be baked in once) to match
     * {@code player}'s current Combat level - call this from the same periodic per-player pass
     * {@code SwordDamageService#applySwordDamage} already runs on.
     */
    public void applyArmorAttackSpeedModifiers(Player player) {
        double base = this.combat.attackSpeed(this.combat.progress(player).level());
        PlayerInventory inv = player.getInventory();
        this.updateArmorSpeedModifier(inv.getHelmet(), EquipmentSlotGroup.HEAD, base);
        this.updateArmorSpeedModifier(inv.getChestplate(), EquipmentSlotGroup.CHEST, base);
        this.updateArmorSpeedModifier(inv.getLeggings(), EquipmentSlotGroup.LEGS, base);
        this.updateArmorSpeedModifier(inv.getBoots(), EquipmentSlotGroup.FEET, base);
    }

    private void updateArmorSpeedModifier(ItemStack item, EquipmentSlotGroup slot, double base) {
        if (item == null || item.isEmpty()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        double bonusPercent = this.armorStatsOf(item).attackSpeed();
        double delta = base * bonusPercent / 100.0;
        AttributeModifier existing = null;
        if (meta.hasAttributeModifiers()) {
            for (AttributeModifier m : meta.getAttributeModifiers(Attribute.ATTACK_SPEED)) {
                if (m.getKey().equals(ARMOR_SPEED_KEY)) {
                    existing = m;
                    break;
                }
            }
        }
        boolean needsUpdate = Math.abs(delta) < 1.0E-4 ? existing != null : (existing == null || Math.abs(existing.getAmount() - delta) > 1.0E-4);
        if (!needsUpdate) {
            return;
        }
        if (existing != null) {
            meta.removeAttributeModifier(Attribute.ATTACK_SPEED, existing);
        }
        if (Math.abs(delta) > 1.0E-4) {
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(ARMOR_SPEED_KEY, delta, AttributeModifier.Operation.ADD_NUMBER, slot));
        }
        item.setItemMeta(meta);
    }
}
