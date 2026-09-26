package dev.icaro.foodtooltips.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.util.LoreWrap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Lapis Lazuli "Experience" crafting sub-tree: two crafting-only intermediate items
 * (Lapis Core, True Lapis Core - both {@code PLAYER_HEAD}s with a fixed minecraft-heads.com
 * skin) feeding into two custom Experience Bottle tiers (Grand: {@value #GRAND_XP} XP,
 * Titanic: {@value #TITANIC_XP} XP), each strictly gated behind already having crafted
 * the previous tier - same "requires the previous tier as an ingredient" philosophy as
 * {@code LapisArmorService}'s own Diamond-in-the-center armor recipes:
 * <ul>
 *   <li>Lapis Core: 8 Lapis Lazuli Blocks around a Diamond Block.
 *   <li>Grand Experience Bottle: same shape as the plain Experience Bottle recipe ({@code
 *   LapisArmorService#registerExperienceBottleRecipe}), but with Lapis Core in place of
 *   raw Lapis Lazuli.
 *   <li>True Lapis Core: 8 Lapis Core around a Netherite Ingot.
 *   <li>Titanic Experience Bottle: same shape again, with True Lapis Core in place of
 *   Lapis Core.
 * </ul>
 * Grand/Titanic Experience Bottles are plain {@code Material.EXPERIENCE_BOTTLE} (so they
 * throw and animate exactly like the vanilla item) tagged with their own XP amount via
 * PDC ({@link #EXPERIENCE_AMOUNT_KEY}), overriding vanilla's small random payout on
 * landing - see {@link #expBottle}. Also independently fishable - see {@link #fish} - and
 * both giveable from the {@code /rpgitems} admin menu ({@link #grandBottleGift}/{@link
 * #titanicBottleGift}).
 *
 * <p>Both custom heads use a fixed (not random) profile UUID ({@link #LAPIS_CORE_PROFILE}/
 * {@link #TRUE_LAPIS_CORE_PROFILE}) - unlike every other custom head in this plugin (menu-
 * only display tiles, rebuilt fresh every time a menu opens, so a random UUID per build
 * never matters there), these are real inventory items that have to reliably match
 * themselves: a random UUID per build would make two "different" copies of the same
 * Lapis Core fail to stack, and fail to match {@link RecipeChoice.ExactChoice} in True
 * Lapis Core's own recipe.
 */
public final class LapisExperienceService implements Listener {
    private static final int GRAND_XP = 1500;
    private static final int TITANIC_XP = 250_000;

    private static final UUID LAPIS_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:lapis_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID TRUE_LAPIS_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:true_lapis_core".getBytes(StandardCharsets.UTF_8));

    private static final NamedTextColor ATTRIBUTE_COLOR = NamedTextColor.GOLD;

    /** Marks a Lapis Core - see {@link #localize}. */
    private static final NamespacedKey LAPIS_CORE_KEY = new NamespacedKey("foodtooltips", "lapis_core");
    /** Marks a True Lapis Core - see {@link #localize}. */
    private static final NamespacedKey TRUE_LAPIS_CORE_KEY = new NamespacedKey("foodtooltips", "true_lapis_core");
    /** Holds a custom bottle's own XP payout - see {@link #expBottle}. Presence alone also marks the item as one of this class's own bottles, for {@link #localize}. */
    private static final NamespacedKey EXPERIENCE_AMOUNT_KEY = new NamespacedKey("foodtooltips", "custom_experience_bottle_amount");

    private static final String LAPIS_CORE_DESCRIPTION_PT = "Usado para craftar um Grand Experience Bottle.";
    private static final String LAPIS_CORE_DESCRIPTION_EN = "Used to craft a Grand Experience Bottle.";
    private static final String TRUE_LAPIS_CORE_DESCRIPTION_PT = "Usado para craftar um Titanic Experience Bottle.";
    private static final String TRUE_LAPIS_CORE_DESCRIPTION_EN = "Used to craft a Titanic Experience Bottle.";
    private static final String BOTTLE_DESCRIPTION_PT = "Arremessada como um Frasco de Experiência comum.";
    private static final String BOTTLE_DESCRIPTION_EN = "Thrown like a regular Experience Bottle.";

    /** See {@link #fish} - independent of {@link #TITANIC_FISH_CHANCE}, so (astronomically rarely) both can hit on the same catch. */
    private static final double GRAND_FISH_CHANCE = 0.01;
    /** See {@link #fish}. */
    private static final double TITANIC_FISH_CHANCE = 0.001;

    private final Plugin plugin;
    private final ItemTierService tiers;

    public LapisExperienceService(Plugin plugin, ItemTierService tiers) {
        this.plugin = plugin;
        this.tiers = tiers;
    }

    /**
     * Overrides the vanilla small random XP payout with whichever amount {@link
     * #EXPERIENCE_AMOUNT_KEY} holds on the thrown item itself ({@code
     * ThrowableProjectile#getItem}, which Paper keeps in sync with whatever {@code
     * ItemStack} was actually thrown - PDC included) - a no-op for a plain vanilla
     * Experience Bottle, which never carries this key.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void expBottle(ExpBottleEvent e) {
        ItemStack thrown = e.getEntity().getItem();
        ItemMeta meta = thrown == null ? null : thrown.getItemMeta();
        if (meta == null) {
            return;
        }
        Integer amount = meta.getPersistentDataContainer().get(EXPERIENCE_AMOUNT_KEY, PersistentDataType.INTEGER);
        if (amount != null) {
            e.setExperience(amount);
        }
    }

    /**
     * A real catch (not junk pulled in early, a splash with nothing on the hook, or the
     * bobber just landing) has an independent {@value #TITANIC_FISH_CHANCE} chance of
     * also handing the player a Titanic Experience Bottle and, separately, an
     * independent {@value #GRAND_FISH_CHANCE} chance of a Grand one - on top of
     * whatever vanilla's own catch already gave, same "extra roll on top" shape as
     * {@code CombatListener}'s own Undead's Sword drop chance.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player p = e.getPlayer();
        if (ThreadLocalRandom.current().nextDouble() < TITANIC_FISH_CHANCE) {
            this.give(p, this.titanicBottle());
        }
        if (ThreadLocalRandom.current().nextDouble() < GRAND_FISH_CHANCE) {
            this.give(p, this.grandBottle());
        }
    }

    private void give(Player p, ItemStack item) {
        for (ItemStack overflow : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
    }

    /** A fresh, standalone Grand Experience Bottle, already localized to {@code viewer} - for the {@code /rpgitems} admin menu ({@code LegendaryItemsMenuService}). */
    public List<ItemStack> grandBottleGift(Player viewer) {
        ItemStack item = this.grandBottle();
        localize(item, Language.of(viewer));
        return List.of(item);
    }

    /** A fresh, standalone Titanic Experience Bottle, already localized to {@code viewer} - for the {@code /rpgitems} admin menu ({@code LegendaryItemsMenuService}). */
    public List<ItemStack> titanicBottleGift(Player viewer) {
        ItemStack item = this.titanicBottle();
        localize(item, Language.of(viewer));
        return List.of(item);
    }

    /** Registers all 4 recipes - see this class's own doc for the shapes/gating. */
    public void registerRecipes() {
        ItemStack lapisCore = this.lapisCore();
        ItemStack trueLapisCore = this.trueLapisCore();

        this.newRecipe("lapis_core", lapisCore, new String[]{"LLL", "LDL", "LLL"}, recipe -> {
            recipe.setIngredient('L', Material.LAPIS_BLOCK);
            recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        });
        this.newRecipe("true_lapis_core", trueLapisCore, new String[]{"LLL", "LDL", "LLL"}, recipe -> {
            recipe.setIngredient('L', new RecipeChoice.ExactChoice(lapisCore));
            recipe.setIngredient('D', Material.NETHERITE_INGOT);
        });
        this.newRecipe("grand_experience_bottle", this.grandBottle(), new String[]{" L ", "LBL", "LLL"}, recipe -> {
            recipe.setIngredient('L', new RecipeChoice.ExactChoice(lapisCore));
            recipe.setIngredient('B', Material.GLASS_BOTTLE);
        });
        this.newRecipe("titanic_experience_bottle", this.titanicBottle(), new String[]{" L ", "LBL", "LLL"}, recipe -> {
            recipe.setIngredient('L', new RecipeChoice.ExactChoice(trueLapisCore));
            recipe.setIngredient('B', Material.GLASS_BOTTLE);
        });
    }

    /** A fresh {@link ShapedRecipe} for {@code key}/{@code result}/{@code shape}, with any stale registration from a previous {@code /reload} removed first - Bukkit otherwise silently rejects a duplicate key instead of replacing it. */
    private void newRecipe(String key, ItemStack result, String[] shape, Consumer<ShapedRecipe> ingredients) {
        NamespacedKey recipeKey = new NamespacedKey(this.plugin, key);
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape(shape);
        ingredients.accept(recipe);
        Bukkit.addRecipe(recipe);
    }

    private ItemStack lapisCore() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.LAPIS_CORE, LAPIS_CORE_PROFILE);
        meta.getPersistentDataContainer().set(LAPIS_CORE_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Lapis Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(wrappedDescription(LAPIS_CORE_DESCRIPTION_PT));
        this.tiers.forceTier(meta, ItemTier.C);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack trueLapisCore() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.TRUE_LAPIS_CORE, TRUE_LAPIS_CORE_PROFILE);
        meta.getPersistentDataContainer().set(TRUE_LAPIS_CORE_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("True Lapis Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(wrappedDescription(TRUE_LAPIS_CORE_DESCRIPTION_PT));
        this.tiers.forceTier(meta, ItemTier.A);
        item.setItemMeta(meta);
        return item;
    }

    private static void applyProfile(SkullMeta meta, String texture, UUID profileId) {
        PlayerProfile profile = Bukkit.createProfile(profileId);
        profile.setProperty(new ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
    }

    private ItemStack grandBottle() {
        return this.bottle(GRAND_XP, "Grand Experience Bottle", ItemTier.B);
    }

    private ItemStack titanicBottle() {
        return this.bottle(TITANIC_XP, "Titanic Experience Bottle", ItemTier.S);
    }

    /** One custom Experience Bottle tier: plain vanilla Material (so it throws/animates like the real item), tagged with its own XP payout, a gold "XP Orbs: +N" line (same named-stat convention {@code LapisArmorService} uses) and the shared bottle description below it. */
    private ItemStack bottle(int amount, String name, ItemTier tier) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(EXPERIENCE_AMOUNT_KEY, PersistentDataType.INTEGER, amount);
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("XP Orbs: +" + amount, ATTRIBUTE_COLOR).decoration(TextDecoration.ITALIC, false));
        lore.addAll(wrappedDescription(BOTTLE_DESCRIPTION_PT));
        meta.lore(lore);
        this.tiers.forceTier(meta, tier);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Renders {@code item}'s description line in {@code l} if it's one of this class's
     * own items, returning whether anything actually changed - same shape as {@code
     * LapisArmorService#localize}: the name is always English (never swapped), only the
     * description line changes, and which pair of PT/EN text applies is picked by which
     * marker the item's PDC carries.
     */
    public static boolean localize(ItemStack item, Language l) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(LAPIS_CORE_KEY, PersistentDataType.BYTE)) {
            return swapDescription(item, meta, LAPIS_CORE_DESCRIPTION_PT, LAPIS_CORE_DESCRIPTION_EN, l);
        }
        if (meta.getPersistentDataContainer().has(TRUE_LAPIS_CORE_KEY, PersistentDataType.BYTE)) {
            return swapDescription(item, meta, TRUE_LAPIS_CORE_DESCRIPTION_PT, TRUE_LAPIS_CORE_DESCRIPTION_EN, l);
        }
        if (meta.getPersistentDataContainer().has(EXPERIENCE_AMOUNT_KEY, PersistentDataType.INTEGER)) {
            return swapDescription(item, meta, BOTTLE_DESCRIPTION_PT, BOTTLE_DESCRIPTION_EN, l);
        }
        return false;
    }

    private static boolean swapDescription(ItemStack item, ItemMeta meta, String pt, String en, Language l) {
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        List<Component> ptBlock = wrappedDescription(pt);
        List<Component> enBlock = wrappedDescription(en);
        List<Component> wantedBlock = l == Language.PT ? ptBlock : enBlock;
        int at = indexOfBlock(lore, ptBlock);
        int size = ptBlock.size();
        if (at < 0) {
            at = indexOfBlock(lore, enBlock);
            size = enBlock.size();
        }
        if (at < 0 || lore.subList(at, at + size).equals(wantedBlock)) {
            return false;
        }
        lore.subList(at, at + size).clear();
        lore.addAll(at, wantedBlock);
        meta.lore(lore);
        item.setItemMeta(meta);
        return true;
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

    /** Same periodic "keep every held item's tooltip in the holder's own language" sweep {@code LapisArmorService#applyToInventory} runs - called from {@code FoodTooltipsPlugin}'s own per-tick loop. Storage and armor slots aren't the only place these can sit (Lapis Core is a crafting material, likely to live in a chest more than an inventory) - offhand included too, matching {@code EnchantService#applyToInventory}'s own broader sweep. */
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
        ItemStack offhand = inv.getItemInOffHand();
        if (localize(offhand, l)) {
            inv.setItemInOffHand(offhand);
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
