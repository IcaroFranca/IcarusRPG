package dev.icaro.foodtooltips.enchant;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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
     * own Enchanting Table filtering), and custom entries whose {@link
     * IcarusEnchant#canApplyTo} accepts this item's material. Empty for a null/empty
     * item - the Enchanting Table screen shows nothing in its catalog until an item
     * is placed.
     */
    public List<EnchantEntry> compatibleEntries(ItemStack item, boolean pt) {
        List<EnchantEntry> result = new ArrayList<>();
        if (item == null || item.isEmpty()) {
            return result;
        }
        for (EnchantEntry e : this.allEntries(pt)) {
            if (e instanceof VanillaEnchantEntry v) {
                if (v.enchantment().canEnchantItem(item)) {
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
        return null;
    }

    /** Why {@code enchant} can't go on {@code item} right now, or null if it's fine - the custom-entry counterpart to {@link #vanillaBlockReason}. */
    public String customBlockReason(ItemStack item, IcarusEnchant enchant, boolean pt) {
        if (!enchant.canApplyTo(item.getType())) {
            return pt ? "Esse encantamento não se aplica a este tipo de item." : "This enchantment doesn't apply to this item type.";
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
     * one the player sees instead of the two duplicating each other.
     */
    public void rebuildLore(ItemStack item, boolean pt) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        this.stripLoreBlock(lore);
        Map<EnchantEntry, Integer> levels = this.levelsOf(item);
        if (!levels.isEmpty()) {
            List<Component> block = this.loreBlock(levels, pt);
            int tierIndex = this.findTierIndex(lore);
            if (tierIndex >= 0) {
                lore.addAll(tierIndex, block);
            } else {
                lore.addAll(block);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /** The "Encantamentos:"/list block only - a blank separator line first if there's other lore before it. */
    private List<Component> loreBlock(Map<EnchantEntry, Integer> levels, boolean pt) {
        List<Component> block = new ArrayList<>();
        block.add(Component.empty());
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
