package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.potion.PotionMix;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * The craftable rewards Farming's own {@link CollectionsCatalog} entries (Cactus, Carrot)
 * unlock - kept in one class since every one of them is a small, standalone crafted item
 * with no other logic of its own, unlike a full gear line ({@code LapisArmorService}'s own
 * scope). Every recipe here is registered unconditionally (same as every other custom
 * recipe in this plugin) - the actual per-player *unlock* gate is enforced elsewhere
 * ({@code collections.CollectionsRecipeGateListener} blocks crafting it, {@code
 * crafting.RecipeBookMenuService} shows the requirement) so it can still exist in Bukkit's
 * registry (and this plugin's own Recipe Book) for every player to see, per the player's
 * own explicit spec for Cactus Armor ("até será possível ver no livro de receitas, mas vai
 * ter um aviso dizendo os requisitos").
 *
 * <ul>
 *   <li>Cactus Core / Carrot Core: 8 Cactus/Carrot around a Diamond Block - purely a
 *   collectible crafted item today (no further recipe consumes it yet), same shape {@code
 *   LapisExperienceService}'s own Lapis Core uses for its own "8 + Diamond Block" recipe.
 *   <li>Cactus Armor: the exact vanilla armor shapes (Helmet/Chestplate/Leggings/Boots),
 *   with Cactus in every material slot - real leather armor (Color#00FF00, per explicit
 *   request), no forced stats of its own (kept as plain leather's own vanilla Defense
 *   values - the player's own spec calls it "representada por uma armadura de couro
 *   tingida", cosmetic only).
 *   <li>Potion of Resistance: a real vanilla {@link PotionMix} (Awkward Potion + Cactus),
 *   the actual "central material" the player asked for - vanilla has no player-brewable
 *   recipe for this potion at all normally. Global once registered (a potion mix has no
 *   per-player discovery concept the way a crafting recipe does, so this one isn't gated
 *   the same way the others are - see this class's own doc on {@code
 *   CollectionsRecipeGateListener} for why that only covers {@code CraftingRecipe}s).
 *   <li>Sprout Armor: item definition only (+20 Defense/+15 Farming Fortune per piece, per
 *   explicit request), no recipe registered yet - "ainda não terá crafting, mas deixa
 *   preparado para receber" (a recipe, later). {@link #createSproutPiece} exists so a
 *   future recipe (or admin-menu gift) has a ready-made piece to hand out; nothing calls it
 *   yet.
 * </ul>
 */
public final class FarmingCollectionsItemsService {
    private static final UUID CACTUS_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:cactus_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID CARROT_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:carrot_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID CHOCOLATE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:chocolate_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID FEATHER_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:feather_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID MUSHROOM_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:mushroom_core".getBytes(StandardCharsets.UTF_8));
    private static final Color CACTUS_ARMOR_COLOR = Color.fromRGB(0x00, 0xFF, 0x00);
    /** No exact shade was specified ("tingida de marrom") - a plain chocolate brown, easy to retune. */
    private static final Color CHOCOLATE_ARMOR_COLOR = Color.fromRGB(0x5C, 0x3A, 0x21);
    /** No exact shade was specified ("tingida de vermelho") - matches Cactus Armor's own pure-color convention. */
    private static final Color MUSHROOM_ARMOR_COLOR = Color.fromRGB(0xFF, 0x00, 0x00);
    /** Neither Chocolate nor Mushroom Armor's own recipe ingredient was specified (only their unlock milestones and their Core items' ingredients were) - Chocolate uses Cocoa Beans and Mushroom uses Red Mushroom, matching Cactus Armor's own "the category's own drop material" precedent. */
    private static final int SPROUT_DEFENSE_PER_PIECE = 20;
    /** 8 minutes - see {@link #registerResistancePotionMix}'s own doc on why this is a judgment call. */
    private static final int RESISTANCE_DURATION_TICKS = 9600;
    /** See {@link #createSproutPiece} - not wired to a Farming Fortune source yet, since nothing can obtain this piece at all until a recipe exists. */
    public static final int SPROUT_FARMING_FORTUNE_PER_PIECE = 15;

    private final Plugin plugin;
    private final ItemTierService tiers;

    public FarmingCollectionsItemsService(Plugin plugin, ItemTierService tiers) {
        this.plugin = plugin;
        this.tiers = tiers;
    }

    /** Registers every recipe this class owns that's actually ready today (Cactus/Carrot Core, Cactus Armor, the Resistance Potion mix) - Sprout Armor is deliberately not included, see this class's own doc. */
    public void registerRecipes() {
        this.newShapedRecipe(CollectionsCatalog.CACTUS_CORE_RECIPE, this.cactusCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.CACTUS);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.CARROT_CORE_RECIPE, this.carrotCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.CARROT);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.CACTUS_HELMET_RECIPE, this.cactusPiece(Material.LEATHER_HELMET, "Cactus Helmet"),
                new String[]{"XXX", "X X"}, r -> r.setIngredient('X', Material.CACTUS));
        this.newShapedRecipe(CollectionsCatalog.CACTUS_CHESTPLATE_RECIPE, this.cactusPiece(Material.LEATHER_CHESTPLATE, "Cactus Chestplate"),
                new String[]{"X X", "XXX", "XXX"}, r -> r.setIngredient('X', Material.CACTUS));
        this.newShapedRecipe(CollectionsCatalog.CACTUS_LEGGINGS_RECIPE, this.cactusPiece(Material.LEATHER_LEGGINGS, "Cactus Leggings"),
                new String[]{"XXX", "X X", "X X"}, r -> r.setIngredient('X', Material.CACTUS));
        this.newShapedRecipe(CollectionsCatalog.CACTUS_BOOTS_RECIPE, this.cactusPiece(Material.LEATHER_BOOTS, "Cactus Boots"),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', Material.CACTUS));
        this.registerResistancePotionMix();

        this.newShapedRecipe(CollectionsCatalog.CHOCOLATE_CORE_RECIPE, this.chocolateCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.COCOA_BEANS);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.CHOCOLATE_HELMET_RECIPE, this.chocolatePiece(Material.LEATHER_HELMET, "Chocolate Helmet"),
                new String[]{"XXX", "X X"}, r -> r.setIngredient('X', Material.COCOA_BEANS));
        this.newShapedRecipe(CollectionsCatalog.CHOCOLATE_CHESTPLATE_RECIPE, this.chocolatePiece(Material.LEATHER_CHESTPLATE, "Chocolate Chestplate"),
                new String[]{"X X", "XXX", "XXX"}, r -> r.setIngredient('X', Material.COCOA_BEANS));
        this.newShapedRecipe(CollectionsCatalog.CHOCOLATE_LEGGINGS_RECIPE, this.chocolatePiece(Material.LEATHER_LEGGINGS, "Chocolate Leggings"),
                new String[]{"XXX", "X X", "X X"}, r -> r.setIngredient('X', Material.COCOA_BEANS));
        this.newShapedRecipe(CollectionsCatalog.CHOCOLATE_BOOTS_RECIPE, this.chocolatePiece(Material.LEATHER_BOOTS, "Chocolate Boots"),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', Material.COCOA_BEANS));

        this.newShapedRecipe(CollectionsCatalog.FEATHER_CORE_RECIPE, this.featherCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.FEATHER);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });

        // "de qualquer tipo" (either mushroom color) - the Core recipe's own explicit spec.
        RecipeChoice.MaterialChoice anyMushroom = new RecipeChoice.MaterialChoice(Material.RED_MUSHROOM, Material.BROWN_MUSHROOM);
        this.newShapedRecipe(CollectionsCatalog.MUSHROOM_CORE_RECIPE, this.mushroomCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', anyMushroom);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.MUSHROOM_HELMET_RECIPE, this.mushroomPiece(Material.LEATHER_HELMET, "Mushroom Helmet", 0),
                new String[]{"XXX", "X X"}, r -> r.setIngredient('X', Material.RED_MUSHROOM));
        this.newShapedRecipe(CollectionsCatalog.MUSHROOM_CHESTPLATE_RECIPE, this.mushroomPiece(Material.LEATHER_CHESTPLATE, "Mushroom Chestplate", MushroomArmorService.CHESTPLATE_DEFENSE),
                new String[]{"X X", "XXX", "XXX"}, r -> r.setIngredient('X', Material.RED_MUSHROOM));
        this.newShapedRecipe(CollectionsCatalog.MUSHROOM_LEGGINGS_RECIPE, this.mushroomPiece(Material.LEATHER_LEGGINGS, "Mushroom Leggings", MushroomArmorService.LEGGINGS_DEFENSE),
                new String[]{"XXX", "X X", "X X"}, r -> r.setIngredient('X', Material.RED_MUSHROOM));
        this.newShapedRecipe(CollectionsCatalog.MUSHROOM_BOOTS_RECIPE, this.mushroomPiece(Material.LEATHER_BOOTS, "Mushroom Boots", 0),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', Material.RED_MUSHROOM));
    }

    /**
     * Vanilla has no real {@link PotionType} for Resistance at all (it only ever exists as
     * a raw {@link PotionEffectType}, e.g. on an Ominous Bottle - never a player-brewable
     * base type) - so this potion is built the same way any other "custom" potion in this
     * plugin's own genre would be: a {@link PotionType#MUNDANE} base (an inert vanilla type,
     * never a real gameplay effect of its own) with {@link PotionEffectType#RESISTANCE}
     * added as a custom effect on top, a custom display name so it doesn't just read
     * "Mundane Potion", and a matching potion color. {@link #RESISTANCE_DURATION_TICKS}
     * (8 minutes) matches vanilla's own un-extended base potion duration convention -
     * no duration was specified, so this is a judgment call, easy to retune.
     */
    private void registerResistancePotionMix() {
        var awkward = new org.bukkit.inventory.ItemStack(Material.POTION);
        PotionMeta awkwardMeta = (PotionMeta) awkward.getItemMeta();
        awkwardMeta.setBasePotionType(PotionType.AWKWARD);
        awkward.setItemMeta(awkwardMeta);

        var resistance = new org.bukkit.inventory.ItemStack(Material.POTION);
        PotionMeta resistanceMeta = (PotionMeta) resistance.getItemMeta();
        resistanceMeta.setBasePotionType(PotionType.MUNDANE);
        resistanceMeta.addCustomEffect(new PotionEffect(PotionEffectType.RESISTANCE, RESISTANCE_DURATION_TICKS, 0), true);
        resistanceMeta.setColor(Color.fromRGB(0x9C, 0x57, 0x4B));
        resistanceMeta.displayName(Component.text("Potion of Resistance", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        resistance.setItemMeta(resistanceMeta);

        NamespacedKey key = new NamespacedKey(this.plugin, "resistance_from_cactus");
        Bukkit.getPotionBrewer().removePotionMix(key);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, resistance,
                new RecipeChoice.ExactChoice(awkward), new RecipeChoice.MaterialChoice(Material.CACTUS)));
    }

    private org.bukkit.inventory.ItemStack cactusCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.CACTUS_CORE, CACTUS_CORE_PROFILE);
        meta.displayName(Component.text("Cactus Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack carrotCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.CARROT_CORE, CARROT_CORE_PROFILE);
        meta.displayName(Component.text("Carrot Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** One piece of Cactus Armor: dyed-green leather, purely cosmetic - real leather Defense, no forced override, matching the player's own "representada por uma armadura de couro tingida" spec. */
    private org.bukkit.inventory.ItemStack cactusPiece(Material material, String name) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(CACTUS_ARMOR_COLOR);
        }
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack chocolateCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.CHOCOLATE_CORE, CHOCOLATE_CORE_PROFILE);
        meta.displayName(Component.text("Chocolate Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack featherCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.FEATHER_CORE, FEATHER_CORE_PROFILE);
        meta.displayName(Component.text("Feather Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack mushroomCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.MUSHROOM_CORE, MUSHROOM_CORE_PROFILE);
        meta.displayName(Component.text("Mushroom Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** One piece of Chocolate Armor: dyed-brown leather, purely cosmetic - same "no forced stats" spec as Cactus Armor (only Sprout/Mushroom Armor have explicit stats). */
    private org.bukkit.inventory.ItemStack chocolatePiece(Material material, String name) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(CHOCOLATE_ARMOR_COLOR);
        }
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One piece of Mushroom Armor: dyed-red leather, {@code defense} forced (0 for Helmet/
     * Boots, per the player's own per-piece spec - see {@link MushroomArmorService}) via the
     * same override every other custom-Defense armor in this plugin uses. Health is
     * deliberately NOT baked here - {@link MushroomArmorService#applyToInventory} bakes it
     * fresh every tick instead (base + this piece's own reforge Health, tripled at night),
     * so a value baked here would just be immediately overwritten anyway.
     */
    private org.bukkit.inventory.ItemStack mushroomPiece(Material material, String name, int defense) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(MUSHROOM_ARMOR_COLOR);
        }
        if (defense > 0) {
            ArmorDefenseService.forceDefense(meta, defense);
        }
        MushroomArmorService.markMushroomPiece(meta);
        meta.displayName(Component.text(name, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One piece of Sprout Armor: +{@value #SPROUT_DEFENSE_PER_PIECE} Defense (forced, same
     * per-item override {@code ArmorDefenseService#forceDefense} already gives Miner's/Lapis
     * Lazuli Armor) and +{@value #SPROUT_FARMING_FORTUNE_PER_PIECE} Farming Fortune per the
     * player's own spec - the Fortune half isn't wired into {@code GeneralSkillService} yet
     * (no late-bound hook call here), since nothing can obtain this piece at all until a
     * recipe exists to craft it (see this class's own doc) - wiring an unreachable bonus
     * would be dead code with nothing to verify it against. Not called from anywhere yet;
     * exists so the moment a recipe (or admin gift) is added, the piece itself needs no
     * further design work.
     */
    public org.bukkit.inventory.ItemStack createSproutPiece(Material material, String name) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.fromRGB(0x4C, 0xAF, 0x50));
        }
        ArmorDefenseService.forceDefense(meta, SPROUT_DEFENSE_PER_PIECE);
        this.tiers.forceTier(meta, ItemTier.B);
        meta.getPersistentDataContainer().set(new NamespacedKey("foodtooltips", "sprout_armor_piece"), PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private void newShapedRecipe(NamespacedKey key, org.bukkit.inventory.ItemStack result, String[] shape, java.util.function.Consumer<ShapedRecipe> ingredients) {
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);
        ingredients.accept(recipe);
        Bukkit.addRecipe(recipe);
    }

    private static void applyProfile(SkullMeta meta, String texture, UUID profileId) {
        var profile = Bukkit.createProfile(profileId);
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
    }
}
