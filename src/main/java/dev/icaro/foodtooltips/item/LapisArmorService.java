package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.util.LoreWrap;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Lapis Lazuli Armor: a full leather-dyed-blue armor set, stronger than Diamond's own
 * per-piece Defense (Boots +20, Leggings +35, Chestplate +40, Helmet +25 - Diamond's own
 * are 15/30/40/15) and, on top of that, every piece grants +{@value
 * #MINING_SPEED_PER_PIECE} Mining Speed, +{@value #MINING_FORTUNE_PER_PIECE} Mining
 * Fortune and +50% vanilla XP orbs (any source) while worn - see {@link
 * #equippedMiningSpeedBonus}/{@link #equippedMiningFortuneBonus}/{@link
 * #equippedXpOrbBonus}, wired into {@code GeneralSkillService}'s own
 * miningSpeed/fortune/xpOrbMultiplier formulas from {@code FoodTooltipsPlugin} the same
 * late-bound way {@code ArmorDefenseService}'s own protectionBonus/lethalityPenalty are.
 * Cosmetically just dyed leather (no custom item model/equippable asset, unlike Miner's
 * Armor) - the real texture is somebody else's job, not this class's.
 *
 * <p>Craftable, unlike every other special armor/weapon in this plugin (Miner's Armor,
 * every Legendary Weapon) which is mob-dropped or admin-menu-only: each piece's recipe
 * ({@link #registerRecipes}) is the real vanilla armor shape with Lapis Lazuli Blocks in
 * place of the usual material, plus the piece's own Diamond equivalent consumed in the
 * shape's own center slot - by design, so getting a set requires already having crafted
 * a full Diamond armor set first rather than being a straight cheaper alternative to it
 * (Lapis Lazuli is far more abundant than Diamond, so matching Diamond's own recipe
 * shape/cost 1:1 would have made this strictly better AND cheaper).
 */
public final class LapisArmorService {
    private static final int HELMET_DEFENSE = 25;
    private static final int CHESTPLATE_DEFENSE = 40;
    private static final int LEGGINGS_DEFENSE = 35;
    private static final int BOOTS_DEFENSE = 20;
    /** See {@link #equippedMiningSpeedBonus}. */
    private static final int MINING_SPEED_PER_PIECE = 20;
    /** See {@link #equippedMiningFortuneBonus}. */
    private static final int MINING_FORTUNE_PER_PIECE = 2;
    /** See {@link #equippedXpOrbBonus} - already the fraction {@code GeneralSkillService#xpOrbMultiplier} adds directly (0.5 = +50%). */
    private static final double XP_ORB_BONUS_PER_PIECE = 0.5;
    /** Plain leather dye - a placeholder blue, not the pack's own real texture (see this class's own doc). */
    private static final Color ARMOR_COLOR = Color.fromRGB(30, 60, 190);

    /** Marks a piece as Lapis Lazuli Armor - see {@link #isLapisPiece}. */
    private static final NamespacedKey LAPIS_ARMOR_KEY = new NamespacedKey("foodtooltips", "lapis_armor_piece");
    /** Vanilla's own gold - matches {@code VanillaEnchantEntry}'s own "named stat" color (Mining Speed, Mining Fortune...) - see {@link #attributeLines}. */
    private static final NamedTextColor ATTRIBUTE_COLOR = NamedTextColor.GOLD;
    /** Short explanatory line shown below the gold attribute lines ({@link #attributeLines}) - the only part of this piece's lore that's actually translated, swapped by {@link #localize}. */
    private static final String DESCRIPTION_PT = "Concede esses bônus enquanto equipada.";
    private static final String DESCRIPTION_EN = "Grants these bonuses while worn.";

    private final Plugin plugin;
    private final ItemTierService tiers;

    public LapisArmorService(Plugin plugin, ItemTierService tiers) {
        this.plugin = plugin;
        this.tiers = tiers;
    }

    /** Helmet/chestplate/leggings/boots, in that order, freshly built - shared by {@link #registerRecipes}, since every recipe's own result needs the exact same fixed item. */
    private ItemStack[] fullSet() {
        return new ItemStack[]{
                this.lapisPiece(new ItemStack(Material.LEATHER_HELMET), HELMET_DEFENSE, "Lapis Lazuli Helmet"),
                this.lapisPiece(new ItemStack(Material.LEATHER_CHESTPLATE), CHESTPLATE_DEFENSE, "Lapis Lazuli Chestplate"),
                this.lapisPiece(new ItemStack(Material.LEATHER_LEGGINGS), LEGGINGS_DEFENSE, "Lapis Lazuli Leggings"),
                this.lapisPiece(new ItemStack(Material.LEATHER_BOOTS), BOOTS_DEFENSE, "Lapis Lazuli Boots")};
    }

    /**
     * One piece of Lapis Lazuli Armor: dyed blue leather, unbreakable, forced Defense
     * ({@code ArmorDefenseService#forceDefense}) regardless of being cosmetically
     * leather, pinned to Tier C (same as Miner's Armor and plain Diamond gear, per
     * explicit request - rarity here isn't meant to track power level). The name is
     * always English, in both languages (per explicit request - unlike every other
     * item in this plugin, which shows a translated name) - only the description line
     * below the gold attribute lines ({@link #attributeLines}) is actually translated,
     * swapped by {@link #localize} via {@link #applyToInventory}.
     */
    private ItemStack lapisPiece(ItemStack item, int defense, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(ARMOR_COLOR);
        }
        meta.setUnbreakable(true);
        ArmorDefenseService.forceDefense(meta, defense);
        this.tiers.forceTier(meta, ItemTier.C);
        meta.getPersistentDataContainer().set(LAPIS_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>(attributeLines());
        lore.addAll(wrappedDescription(DESCRIPTION_PT));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** The 3 gold "named stat" lines every piece shows, one per line, above the (translated) description - same vanilla-gold convention {@code VanillaEnchantEntry} uses for Mining Speed/Mining Fortune, and always in English regardless of language, same as those. */
    private static List<Component> attributeLines() {
        return List.of(
                line("Mining Speed: +" + MINING_SPEED_PER_PIECE),
                line("Mining Fortune: +" + MINING_FORTUNE_PER_PIECE),
                line("XP Orbs: +" + Math.round(XP_ORB_BONUS_PER_PIECE * 100) + "%"));
    }

    private static Component line(String text) {
        return Component.text(text, ATTRIBUTE_COLOR).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * A fresh, standalone Lapis Lazuli Armor set (helmet/chestplate/leggings/boots),
     * already localized to {@code viewer} - for the {@code /rpgitems} admin menu
     * ({@code LegendaryItemsMenuService}), independent of ever crafting one. The exact
     * same items (same stats, Tier, Unbreakable) the crafting recipes themselves
     * produce - same idea as {@code MinerVariantService#createArmorSet}.
     */
    public List<ItemStack> createArmorSet(Player viewer) {
        Language l = Language.of(viewer);
        List<ItemStack> set = new ArrayList<>();
        for (ItemStack piece : this.fullSet()) {
            localize(piece, l);
            set.add(piece);
        }
        return set;
    }

    /**
     * Registers all 4 crafting recipes - see this class's own doc for why every shape
     * consumes the piece's own Diamond equivalent in its center slot on top of Lapis
     * Lazuli Blocks everywhere else, rather than a straight material swap on Diamond's
     * own recipe shape.
     */
    public void registerRecipes() {
        ItemStack[] set = this.fullSet();
        this.addRecipe("lapis_lazuli_helmet", set[0], new String[]{"LLL", "LDL"}, Material.DIAMOND_HELMET);
        this.addRecipe("lapis_lazuli_chestplate", set[1], new String[]{"L L", "LDL", "LLL"}, Material.DIAMOND_CHESTPLATE);
        this.addRecipe("lapis_lazuli_leggings", set[2], new String[]{"LLL", "LDL", "L L"}, Material.DIAMOND_LEGGINGS);
        this.addRecipe("lapis_lazuli_boots", set[3], new String[]{"L L", "LDL"}, Material.DIAMOND_BOOTS);
    }

    private void addRecipe(String key, ItemStack result, String[] shape, Material diamondPiece) {
        NamespacedKey recipeKey = new NamespacedKey(this.plugin, key);
        // Removed first so a /reload (which re-runs onEnable, and so this method) re-registers
        // cleanly instead of Bukkit silently rejecting the duplicate key.
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape(shape);
        recipe.setIngredient('L', Material.LAPIS_BLOCK);
        recipe.setIngredient('D', diamondPiece);
        Bukkit.addRecipe(recipe);
    }

    /** Whether {@code item} is a Lapis Lazuli Armor piece - see {@link #LAPIS_ARMOR_KEY}. */
    public static boolean isLapisPiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(LAPIS_ARMOR_KEY, PersistentDataType.BYTE);
    }

    /** How many Lapis Lazuli Armor pieces {@code p} currently has equipped (0-4). */
    private int piecesEquipped(Player p) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        if (isLapisPiece(inv.getHelmet())) n++;
        if (isLapisPiece(inv.getChestplate())) n++;
        if (isLapisPiece(inv.getLeggings())) n++;
        if (isLapisPiece(inv.getBoots())) n++;
        return n;
    }

    /** +{@value #MINING_SPEED_PER_PIECE} per equipped piece - wired into {@code GeneralSkillService#applyMiningSpeedAttribute}. */
    public int equippedMiningSpeedBonus(Player p) {
        return this.piecesEquipped(p) * MINING_SPEED_PER_PIECE;
    }

    /** +{@value #MINING_FORTUNE_PER_PIECE} per equipped piece - wired into {@code GeneralSkillService#fortune}. */
    public int equippedMiningFortuneBonus(Player p) {
        return this.piecesEquipped(p) * MINING_FORTUNE_PER_PIECE;
    }

    /** +50% per equipped piece, already as the fraction {@code GeneralSkillService#xpOrbMultiplier} adds directly. */
    public double equippedXpOrbBonus(Player p) {
        return this.piecesEquipped(p) * XP_ORB_BONUS_PER_PIECE;
    }

    /**
     * Renders {@code item}'s description line in {@code l} if it's one of this class's
     * own pieces, returning whether anything actually changed - same shape as {@code
     * MinerVariantService#localize}, minus the name swap (the name is always English -
     * see {@link #lapisPiece}'s own doc - only the description line changes).
     */
    public static boolean localize(ItemStack item, Language l) {
        if (item == null || item.isEmpty() || !isLapisPiece(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        boolean changed = false;
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        List<Component> ptBlock = wrappedDescription(DESCRIPTION_PT);
        List<Component> enBlock = wrappedDescription(DESCRIPTION_EN);
        List<Component> wantedBlock = l == Language.PT ? ptBlock : enBlock;
        int at = indexOfBlock(lore, ptBlock);
        int size = ptBlock.size();
        if (at < 0) {
            at = indexOfBlock(lore, enBlock);
            size = enBlock.size();
        }
        if (at >= 0 && !lore.subList(at, at + size).equals(wantedBlock)) {
            lore.subList(at, at + size).clear();
            lore.addAll(at, wantedBlock);
            meta.lore(lore);
            changed = true;
        }
        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }

    /** The index of {@code block} as a contiguous run within {@code lore}, or -1 if it doesn't occur. */
    private static int indexOfBlock(List<Component> lore, List<Component> block) {
        outer:
        for (int i = 0; i <= lore.size() - block.size(); i++) {
            for (int j = 0; j < block.size(); j++) {
                if (!lore.get(i + j).equals(block.get(j))) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /** Same periodic "keep every held item's tooltip in the holder's own language" sweep {@code MinerVariantService#applyToInventory} runs - called from {@code FoodTooltipsPlugin}'s own per-tick loop. Storage and armor slots only - this armor is never held in the off hand. */
    public void applyToInventory(Player p) {
        Language l = Language.of(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (ItemStack item : storage) {
            changed |= localize(item, l);
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (ItemStack item : armor) {
            armorChanged |= localize(item, l);
        }
        if (armorChanged) {
            inv.setArmorContents(armor);
        }
    }

    /** {@code text} word-wrapped into gray, non-italic lore lines - see {@link LoreWrap#wrapText}. */
    private static List<Component> wrappedDescription(String text) {
        List<Component> lore = new ArrayList<>();
        for (String part : LoreWrap.wrapText(text, LoreWrap.DEFAULT_WIDTH)) {
            lore.add(Component.text(part, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        return lore;
    }
}
