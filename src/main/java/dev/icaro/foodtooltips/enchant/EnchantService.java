package dev.icaro.foodtooltips.enchant;

import dev.icaro.foodtooltips.i18n.Language;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Reads/writes which {@link EnchantEntry} levels an item carries - a custom
 * {@link IcarusEnchant} stored as one PDC integer per enchant (0/absent = not
 * applied), or a real vanilla {@code Enchantment} stored the normal vanilla way. No
 * limit on how many distinct entries (custom or vanilla) an item can hold at once -
 * keeps the item's own "Encantamentos" lore block - custom and vanilla entries
 * together, each with its description - in sync with whatever's applied, hiding
 * Minecraft's own native enchantment tooltip so the two don't duplicate each other.
 *
 * <p>Deliberately doesn't touch combat math itself (Ferocity/Crit Chance/Vampirism/
 * Execution/Health Regen/True Defense) - that wiring is a separate follow-up; this
 * class is the data layer the Enchanting Table screen (and, later, the stat
 * calculations) both read through.
 */
public final class EnchantService {
    private static final String LORE_HEADER_PT = "Encantamentos:";
    private static final String LORE_HEADER_EN = "Enchantments:";
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    /** More than this many applied custom entries and the lore drops each one's description line - keeps the tooltip from ballooning. */
    private static final int DESCRIPTION_CUTOFF = 4;
    /** Vanilla's own enchantment-name blue, for an applied entry below its max level. */
    private static final TextColor NAME_COLOR = TextColor.color(0x5555FF);
    /** Reserved for an entry at its max level - previously used for every entry. */
    private static final TextColor MAX_LEVEL_NAME_COLOR = NamedTextColor.LIGHT_PURPLE;
    /**
     * Vanilla enchantments hidden from this table entirely: Mending and the two
     * curses (never something a player wants applied on purpose), Flame/Infinity/
     * Lure/Luck of the Sea/Fire Aspect/Feather Falling - each replaced by a leveled
     * custom entry (see {@link IcarusEnchant}) since their real vanilla level cap
     * (1, 1, 3, 3, 2, 4 respectively) is fixed by Mojang/Bukkit and can't be raised
     * to fit the leveled effect wanted for them - and Protection/Fire Protection/
     * Blast Protection/Projectile Protection/Respiration/Thorns, replaced the same
     * way even though their level cap isn't changing for Respiration/Thorns, since
     * the effect wanted for them doesn't match real vanilla's own mechanic either.
     */
    private static final Set<String> EXCLUDED_VANILLA_KEYS = Set.of(
            "mending", "vanishing_curse", "binding_curse", "flame", "infinity", "lure", "luck_of_the_sea",
            "fire_aspect", "protection", "fire_protection", "blast_protection", "projectile_protection",
            "feather_falling", "respiration", "thorns");
    /** Sharpness/Smite/Bane of Arthropods conflict with each other in real vanilla (you can't combine them via an anvil) - this table deliberately allows it. */
    private static final Set<String> NON_EXCLUSIVE_DAMAGE_FAMILY = Set.of("sharpness", "smite", "bane_of_arthropods");
    /**
     * Fortune and Efficiency both pass real vanilla's own {@code canEnchantItem} for a
     * hoe (its {@code EnchantmentTarget} is the same broad "digger" category pickaxe/
     * axe/shovel share), but neither belongs on the Enchanting Table's hoe options:
     * Farming's own general-skill bonus already covers Fortune's drop-multiplying job
     * for crops (see {@code GeneralSkillService#fortune}), and a hoe has no real
     * mining speed of its own for Efficiency to raise. Pickaxe/axe/shovel are
     * unaffected - only the hoe loses these two.
     */
    static final Set<String> HOE_EXCLUDED_VANILLA_KEYS = Set.of("fortune", "efficiency");
    /** Mutually exclusive with each other on the same piece - same real vanilla rule as Protection/Fire Protection/Blast Protection/Projectile Protection's own {@code conflictsWith}, re-implemented here since these are custom entries real vanilla's own conflict table never sees (see {@link #customBlockReason}). */
    private static final Set<IcarusEnchant> PROTECTION_FAMILY = EnumSet.of(IcarusEnchant.PROTECTION, IcarusEnchant.FIRE_PROTECTION, IcarusEnchant.BLAST_PROTECTION, IcarusEnchant.PROJECTILE_PROTECTION);

    private final Map<IcarusEnchant, NamespacedKey> keys = new EnumMap<>(IcarusEnchant.class);
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public EnchantService(Plugin plugin) {
        for (IcarusEnchant e : IcarusEnchant.values()) {
            this.keys.put(e, new NamespacedKey(plugin, "enchant_" + e.name().toLowerCase(Locale.ROOT)));
        }
    }

    /** Every offerable entry (custom and every registered vanilla enchantment), alphabetized by its {@code pt}-or-English catalog name - what the Enchanting Table's catalog/guide screens page through. */
    public List<EnchantEntry> allEntries(boolean pt) {
        List<EnchantEntry> list = new ArrayList<>();
        for (IcarusEnchant e : IcarusEnchant.values()) {
            list.add(new CustomEnchantEntry(e));
        }
        for (Enchantment e : Registry.ENCHANTMENT) {
            if (EXCLUDED_VANILLA_KEYS.contains(e.getKey().getKey())) {
                continue;
            }
            list.add(new VanillaEnchantEntry(e));
        }
        Collator collator = Collator.getInstance(pt ? Locale.of("pt", "BR") : Locale.US);
        list.sort(Comparator.comparing(e -> e.catalogName(pt), collator));
        return list;
    }

    /** Catalog names of every entry whose {@link EnchantEntry#requiredEnchantingLevel} is exactly {@code level} - used by {@code SkillsMenuService}'s own per-level browser for the Enchanting skill (via {@code EnchantMenuService}'s delegate) to show what unlocks at that level. Empty if nothing does. */
    public List<String> unlocksAtLevel(int level, boolean pt) {
        List<String> names = new ArrayList<>();
        for (EnchantEntry e : this.allEntries(pt)) {
            if (e.requiredEnchantingLevel() == level) {
                names.add(e.catalogName(pt));
            }
        }
        return names;
    }

    /** {@code item}'s current level of the custom entry {@code enchant}, or 0 if it doesn't have it - a convenience for callers outside this package (combat/damage listeners) that only ever deal in {@link IcarusEnchant} directly and have no way to build a {@link CustomEnchantEntry} themselves (package-private by design). */
    public int customLevel(ItemStack item, IcarusEnchant enchant) {
        return this.levelOf(item, new CustomEnchantEntry(enchant));
    }

    /** Same convenience as {@link #customLevel}, the write side - lets a caller outside this package (e.g. a mob-spawn system gearing up a variant with its own enchanted equipment) apply a custom entry without needing a {@link CustomEnchantEntry} of its own. Same as calling {@link #setLevel} directly, just resolving the entry for you. */
    public void setCustomLevel(ItemStack item, IcarusEnchant enchant, int level, boolean pt) {
        this.setLevel(item, new CustomEnchantEntry(enchant), level, pt);
    }

    /** {@code item}'s current level of {@code entry}, or 0 if it doesn't have it. */
    public int levelOf(ItemStack item, EnchantEntry entry) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        if (entry instanceof VanillaEnchantEntry v) {
            return item.getEnchantmentLevel(v.enchantment());
        }
        CustomEnchantEntry c = (CustomEnchantEntry) entry;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer level = meta.getPersistentDataContainer().get(this.keys.get(c.enchant()), PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    /**
     * Every entry {@code item} could actually receive right now - vanilla entries
     * whose {@code Enchantment#canEnchantItem} accepts this item (matching vanilla's
     * own Enchanting Table filtering, minus {@link #HOE_EXCLUDED_VANILLA_KEYS} on a
     * hoe specifically, and minus Unbreaking on any item already flagged unbreakable -
     * e.g. the Zombie/Skeleton Miner's own Miner's Armor, or this plugin's other
     * one-off unbreakable items - since it would have nothing left to do), and custom
     * entries whose {@link IcarusEnchant#canApplyTo} accepts this item's material.
     * Empty for a null/empty item - the Enchanting Table screen shows nothing in its
     * catalog until an item is placed.
     */
    public List<EnchantEntry> compatibleEntries(ItemStack item, boolean pt) {
        List<EnchantEntry> result = new ArrayList<>();
        if (item == null || item.isEmpty()) {
            return result;
        }
        boolean hoe = item.getType().name().endsWith("_HOE");
        ItemMeta meta = item.getItemMeta();
        boolean alreadyUnbreakable = meta != null && meta.isUnbreakable();
        for (EnchantEntry e : this.allEntries(pt)) {
            if (e instanceof VanillaEnchantEntry v) {
                boolean excludedHoe = hoe && HOE_EXCLUDED_VANILLA_KEYS.contains(v.enchantment().getKey().getKey());
                boolean excludedUnbreaking = alreadyUnbreakable && v.enchantment().getKey().getKey().equals("unbreaking");
                if (v.enchantment().canEnchantItem(item) && !excludedHoe && !excludedUnbreaking) {
                    result.add(e);
                }
            } else if (e instanceof CustomEnchantEntry c) {
                if (c.enchant().canApplyTo(item.getType())) {
                    result.add(e);
                }
            }
        }
        return result;
    }

    /** Every entry (custom or vanilla) currently on {@code item} with a level &gt; 0, custom entries first in {@link IcarusEnchant} declaration order, then vanilla ones. */
    public Map<EnchantEntry, Integer> levelsOf(ItemStack item) {
        Map<EnchantEntry, Integer> result = new LinkedHashMap<>();
        if (item == null || item.isEmpty()) {
            return result;
        }
        for (IcarusEnchant e : IcarusEnchant.values()) {
            CustomEnchantEntry entry = new CustomEnchantEntry(e);
            int level = this.levelOf(item, entry);
            if (level > 0) {
                result.put(entry, level);
            }
        }
        for (Map.Entry<Enchantment, Integer> e : item.getEnchantments().entrySet()) {
            result.put(new VanillaEnchantEntry(e.getKey()), e.getValue());
        }
        return result;
    }

    /**
     * Why {@code enchantment} can't go on {@code item} right now, or null if it's
     * fine. Sharpness/Smite/Bane of Arthropods are deliberately exempted from real
     * vanilla's mutual-exclusion rule (see {@link #NON_EXCLUSIVE_DAMAGE_FAMILY}) -
     * every other real vanilla conflict still applies.
     */
    public String vanillaBlockReason(ItemStack item, Enchantment enchantment, boolean pt) {
        if (!enchantment.canEnchantItem(item)) {
            return pt ? "Esse encantamento não se aplica a este tipo de item." : "This enchantment doesn't apply to this item type.";
        }
        boolean exempt = NON_EXCLUSIVE_DAMAGE_FAMILY.contains(enchantment.getKey().getKey());
        for (Enchantment existing : item.getEnchantments().keySet()) {
            if (existing.equals(enchantment) || (exempt && NON_EXCLUSIVE_DAMAGE_FAMILY.contains(existing.getKey().getKey()))) {
                continue;
            }
            if (enchantment.conflictsWith(existing)) {
                String name = PLAIN.serialize(existing.displayName(1));
                return pt ? "Conflita com " + name + ", já aplicado." : "Conflicts with " + name + ", already applied.";
            }
        }
        // Smelting Touch (a custom entry, so real vanilla's own conflictsWith never
        // sees it) drops a mined block's furnace-smelted form instead of the raw block
        // Silk Touch would keep - mutually exclusive by design, checked by name on both
        // sides (see the SMELTING_TOUCH case in customBlockReason below).
        if (enchantment.equals(Enchantment.SILK_TOUCH) && this.customLevel(item, IcarusEnchant.SMELTING_TOUCH) > 0) {
            return pt ? "Conflita com Toque Fundente, já aplicado." : "Conflicts with Smelting Touch, already applied.";
        }
        return null;
    }

    /** Why {@code enchant} can't go on {@code item} right now, or null if it's fine - the custom-entry counterpart to {@link #vanillaBlockReason}. */
    public String customBlockReason(ItemStack item, IcarusEnchant enchant, boolean pt) {
        if (!enchant.canApplyTo(item.getType())) {
            return pt ? "Esse encantamento não se aplica a este tipo de item." : "This enchantment doesn't apply to this item type.";
        }
        if (enchant == IcarusEnchant.SMELTING_TOUCH && item.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            return pt ? "Conflita com Toque de Seda, já aplicado." : "Conflicts with Silk Touch, already applied.";
        }
        if (PROTECTION_FAMILY.contains(enchant)) {
            for (IcarusEnchant other : PROTECTION_FAMILY) {
                if (other != enchant && this.customLevel(item, other) > 0) {
                    return pt ? "Conflita com " + other.displayName(true) + ", já aplicado." : "Conflicts with " + other.displayName(false) + ", already applied.";
                }
            }
        }
        return null;
    }

    /**
     * Sets {@code item}'s level of {@code entry} (the caller is responsible for
     * charging the player and checking {@link #vanillaBlockReason} for a vanilla
     * entry - see {@code EnchantMenuService}), then rebuilds the item's own
     * "Encantamentos" lore block to match - both kinds now go through the same block
     * (see {@link #rebuildLore}), so their descriptions show up on the item itself,
     * not just in the catalog. Silently no-ops on a null/empty item.
     */
    public void setLevel(ItemStack item, EnchantEntry entry, int level, boolean pt) {
        if (item == null || item.isEmpty()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (entry instanceof VanillaEnchantEntry v) {
            meta.addEnchant(v.enchantment(), level, true);
        } else {
            CustomEnchantEntry c = (CustomEnchantEntry) entry;
            meta.getPersistentDataContainer().set(this.keys.get(c.enchant()), PersistentDataType.INTEGER, level);
        }
        item.setItemMeta(meta);
        this.rebuildLore(item, pt);
    }

    /** Removes {@code entry} from {@code item} entirely (not just lowering its level) and rebuilds the lore block to match - the caller (see {@code EnchantMenuService}) is responsible for confirming this with the player first. Silently no-ops on a null/empty item. */
    public void removeLevel(ItemStack item, EnchantEntry entry, boolean pt) {
        if (item == null || item.isEmpty()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (entry instanceof VanillaEnchantEntry v) {
            meta.removeEnchant(v.enchantment());
        } else {
            CustomEnchantEntry c = (CustomEnchantEntry) entry;
            meta.getPersistentDataContainer().remove(this.keys.get(c.enchant()));
        }
        item.setItemMeta(meta);
        this.rebuildLore(item, pt);
    }

    /**
     * Strips any existing "Encantamentos"/"Enchantments" block from {@code item}'s
     * lore and rebuilds it fresh from whatever's actually applied right now (custom
     * AND vanilla entries together) - self-healing the same way {@code
     * FoodTooltipService} does for its own block, so this never drifts out of sync
     * with the data it's derived from. Inserted right before the item's "TIER ..."
     * badge line if present (keeping that as the very last line, its own documented
     * position), otherwise appended at the end. Also hides Minecraft's own native
     * enchantment tooltip line (HIDE_ENCHANTS) whenever there's at least one entry, so
     * this block - which shows the same name/level plus a description - is the only
     * one the player sees instead of the two duplicating each other, and forces the
     * enchantment glint on ({@link ItemMeta#setEnchantmentGlintOverride}) - vanilla
     * only glints an item that carries a real {@code Enchantment}, so an item with
     * only custom (PDC-stored) entries applied would otherwise render with no glint
     * at all despite genuinely being enchanted.
     *
     * <p>Public (and returning whether anything changed, same shape as {@code
     * FoodTooltipService#update}/{@code ItemTierService#applyTier}) so {@code
     * FoodTooltipListener} can call this on every item its own general per-item
     * refresh sweeps over - not just the ones this class itself just enchanted via
     * {@link #setLevel}/{@link #removeLevel}. Without that, an item enchanted the
     * vanilla way (a loot chest, a mob drop, fishing, villager trading - anything
     * that never went through the reworked Enchanting Table) kept showing real
     * vanilla's own plain enchantment tooltip forever, instead of this plugin's own
     * colored name+description block every enchant applied through the table gets.
     * The {@code levels.isEmpty() && !hasItemFlag(HIDE_ENCHANTS)} fast path skips
     * building/comparing lore lists for the vast majority of plain items such a sweep
     * touches that have nothing to do with enchantments at all.
     */
    public boolean rebuildLore(ItemStack item, boolean pt) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Map<EnchantEntry, Integer> levels = this.levelsOf(item);
        if (levels.isEmpty() && !meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
            return false;
        }
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        List<Component> original = new ArrayList<>(lore);
        this.stripLoreBlock(lore);
        if (!levels.isEmpty()) {
            List<Component> block = this.loreBlock(levels, pt);
            this.insertBeforeTier(lore, block);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setEnchantmentGlintOverride(true);
        } else {
            meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setEnchantmentGlintOverride(null);
        }
        if (lore.equals(original)) {
            return false;
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Runs {@link #rebuildLore} across {@code p}'s entire inventory (storage, armor,
     * offhand) - the same belt-and-suspenders periodic sweep {@code
     * ItemTierService#applyItemTiers} already runs for TIER tags, called from the same
     * {@code FoodTooltipsPlugin} per-tick loop, so an item that arrived some way
     * {@code FoodTooltipListener}'s own event-driven refresh never fires for (a
     * {@code /give} command, another plugin's kill reward...) still gets converted
     * without needing the player to touch it first.
     */
    public void applyToInventory(Player p) {
        boolean pt = Language.of(p) == Language.PT;
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            if (this.rebuildLore(storage[i], pt)) {
                changed = true;
            }
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            if (this.rebuildLore(armor[i], pt)) {
                armorChanged = true;
            }
        }
        if (armorChanged) {
            inv.setArmorContents(armor);
        }
        ItemStack offhand = inv.getItemInOffHand();
        if (this.rebuildLore(offhand, pt)) {
            inv.setItemInOffHand(offhand);
        }
    }

    /** The "Encantamentos:"/list block only, no surrounding blank lines - {@link #insertBeforeTier} decides those dynamically based on what's actually adjacent once inserted. */
    private List<Component> loreBlock(Map<EnchantEntry, Integer> levels, boolean pt) {
        List<Component> block = new ArrayList<>();
        block.add(this.line(pt ? LORE_HEADER_PT : LORE_HEADER_EN, NamedTextColor.GOLD));
        boolean showDescriptions = levels.size() <= DESCRIPTION_CUTOFF;
        for (Map.Entry<EnchantEntry, Integer> entry : levels.entrySet()) {
            EnchantEntry e = entry.getKey();
            int level = entry.getValue();
            TextColor nameColor = level >= e.maxLevel() ? MAX_LEVEL_NAME_COLOR : NAME_COLOR;
            block.add(Component.text(e.leveledName(pt, level), nameColor).decoration(TextDecoration.ITALIC, false));
            if (showDescriptions) {
                for (Component descLine : e.resolvedDescription(pt, level)) {
                    block.add(Component.text("  ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).append(descLine));
                }
            }
        }
        return block;
    }

    /** Removes an existing "Encantamentos"/"Enchantments" block (its header, everything after until the next blank/end, and the blank line right before it) so {@link #rebuildLore} can add a fresh one without duplicating it. */
    private void stripLoreBlock(List<Component> lore) {
        int headerIndex = -1;
        for (int i = 0; i < lore.size(); i++) {
            String s = PLAIN.serialize(lore.get(i));
            if (s.equals(LORE_HEADER_PT) || s.equals(LORE_HEADER_EN)) {
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0) {
            return;
        }
        int from = headerIndex > 0 && PLAIN.serialize(lore.get(headerIndex - 1)).isEmpty() ? headerIndex - 1 : headerIndex;
        int to = headerIndex + 1;
        // Stops at the next blank line OR the TIER badge line - never past it, since
        // this block is inserted right before TIER (see rebuildLore) with nothing
        // blank in between, unlike FoodTooltipService's own block (always last, so a
        // blank-only stop condition is enough there but would eat the TIER line here).
        while (to < lore.size()) {
            String s = PLAIN.serialize(lore.get(to));
            if (s.isEmpty() || s.startsWith("TIER ")) {
                break;
            }
            to++;
        }
        lore.subList(from, to).clear();
    }

    private int findTierIndex(List<Component> lore) {
        for (int i = 0; i < lore.size(); i++) {
            if (PLAIN.serialize(lore.get(i)).startsWith("TIER ")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Inserts {@code block} right before the item's "TIER ..." badge line (or at the
     * end, if it isn't tagged yet) - same "insert before TIER" convention {@code
     * FoodTooltipService#insertBeforeTier} already uses for its own block, reusing
     * whatever blank line already happens to sit at the insertion point instead of
     * always adding a fresh one. Without this - {@code loreBlock} used to prepend its
     * own unconditional blank - inserting right at an existing blank (the one {@code
     * ItemTierService#applyTier} leaves right before TIER) produced two blank lines in
     * a row before "Encantamentos:"/"Enchantments:" and none at all before TIER, since
     * the block's own trailing content landed directly against it.
     */
    private void insertBeforeTier(List<Component> lore, List<Component> block) {
        if (block.isEmpty()) {
            return;
        }
        int at = this.findTierIndex(lore);
        if (at < 0) {
            at = lore.size();
        }
        if (at == 0 || !this.isBlank(lore.get(at - 1))) {
            lore.add(at++, Component.empty());
        }
        lore.addAll(at, block);
        at += block.size();
        if (at < lore.size() && !this.isBlank(lore.get(at))) {
            lore.add(at, Component.empty());
        }
    }

    private boolean isBlank(Component c) {
        return PLAIN.serialize(c).isEmpty();
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    public static String roman(int level) {
        return level >= 1 && level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
    }

    public static void chargeXp(Player p, int levels) {
        p.giveExpLevels(-levels);
    }
}
