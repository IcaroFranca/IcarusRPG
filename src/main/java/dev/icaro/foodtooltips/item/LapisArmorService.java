package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.util.LoreWrap;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
    /** Holds a piece's own name in each language, same idea as {@code MinerVariantService#PIECE_NAME_PT_KEY} - see {@link #localize}. */
    private static final NamespacedKey PIECE_NAME_PT_KEY = new NamespacedKey("foodtooltips", "lapis_piece_name_pt");
    private static final NamespacedKey PIECE_NAME_EN_KEY = new NamespacedKey("foodtooltips", "lapis_piece_name_en");
    /** States the bonus every piece grants while worn - the same summary in both languages, swapped by {@link #localize}. */
    private static final String DESCRIPTION_PT = "Concede +" + MINING_SPEED_PER_PIECE + " Mining Speed, +" + MINING_FORTUNE_PER_PIECE + " Mining Fortune e +50% de Orbes de XP de qualquer fonte.";
    private static final String DESCRIPTION_EN = "Grants +" + MINING_SPEED_PER_PIECE + " Mining Speed, +" + MINING_FORTUNE_PER_PIECE + " Mining Fortune, and +50% XP Orbs from any source.";

    private final Plugin plugin;
    private final ItemTierService tiers;

    public LapisArmorService(Plugin plugin, ItemTierService tiers) {
        this.plugin = plugin;
        this.tiers = tiers;
    }

    /** Helmet/chestplate/leggings/boots, in that order, freshly built - shared by {@link #registerRecipes}, since every recipe's own result needs the exact same fixed item. */
    private ItemStack[] fullSet() {
        return new ItemStack[]{
                this.lapisPiece(new ItemStack(Material.LEATHER_HELMET), HELMET_DEFENSE, "Capacete de Lápis-Lazúli", "Lapis Lazuli Helmet"),
                this.lapisPiece(new ItemStack(Material.LEATHER_CHESTPLATE), CHESTPLATE_DEFENSE, "Peitoral de Lápis-Lazúli", "Lapis Lazuli Chestplate"),
                this.lapisPiece(new ItemStack(Material.LEATHER_LEGGINGS), LEGGINGS_DEFENSE, "Calça de Lápis-Lazúli", "Lapis Lazuli Leggings"),
                this.lapisPiece(new ItemStack(Material.LEATHER_BOOTS), BOOTS_DEFENSE, "Bota de Lápis-Lazúli", "Lapis Lazuli Boots")};
    }

    /**
     * One piece of Lapis Lazuli Armor: dyed blue leather, unbreakable, forced Defense
     * ({@code ArmorDefenseService#forceDefense}) regardless of being cosmetically
     * leather, pinned to Tier S (stronger than Diamond's own Tier C, and above Miner's
     * Armor's Tier A) - Portuguese name/description by default, same as every other
     * item spawned without a player context to read a language preference from ({@link
     * #localize}, called via {@link #applyToInventory}, keeps this correct for whoever
     * actually ends up holding it).
     */
    private ItemStack lapisPiece(ItemStack item, int defense, String namePt, String nameEn) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(ARMOR_COLOR);
        }
        meta.setUnbreakable(true);
        ArmorDefenseService.forceDefense(meta, defense);
        this.tiers.forceTier(meta, ItemTier.S);
        meta.getPersistentDataContainer().set(LAPIS_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(PIECE_NAME_PT_KEY, PersistentDataType.STRING, namePt);
        meta.getPersistentDataContainer().set(PIECE_NAME_EN_KEY, PersistentDataType.STRING, nameEn);
        meta.displayName(Component.text(namePt, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(wrappedDescription(DESCRIPTION_PT));
        item.setItemMeta(meta);
        return item;
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
     * Renders {@code item}'s name/description in {@code l} if it's one of this class's
     * own pieces, returning whether anything actually changed - same shape (and same
     * reasoning) as {@code MinerVariantService#localize}: reads the PT/EN name pair from
     * PDC rather than the item's own (already tier-recolored) display name, so this is
     * safe to call repeatedly and in either direction.
     */
    public static boolean localize(ItemStack item, Language l) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String wantedName = meta.getPersistentDataContainer().get(l == Language.PT ? PIECE_NAME_PT_KEY : PIECE_NAME_EN_KEY, PersistentDataType.STRING);
        if (wantedName == null) {
            return false;
        }
        boolean changed = false;
        String currentNameText = meta.hasDisplayName() ? PlainTextComponentSerializer.plainText().serialize(meta.displayName()) : null;
        if (!wantedName.equals(currentNameText)) {
            meta.displayName(Component.text(wantedName, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            changed = true;
        }
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
