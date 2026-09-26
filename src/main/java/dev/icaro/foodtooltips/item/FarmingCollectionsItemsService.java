package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import dev.icaro.foodtooltips.skills.SkillType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.potion.PotionMix;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
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
 * crafting.RecipeBookMenuService} only lists it for a player who's actually crossed the
 * milestone - see that class's own "SOMENTE RECEITAS DESBLOQUEADAS" doc) so it can still
 * exist in Bukkit's registry for a player who has unlocked it, without this class needing
 * any gating logic of its own.
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
    private static final UUID FLOWER_CACTUS_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:flower_cactus_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID GOLDEN_CARROT_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:golden_carrot_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID TRUE_CHOCOLATE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:true_chocolate_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID CARROT_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:carrot_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID CHOCOLATE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:chocolate_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID FEATHER_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:feather_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID MUSHROOM_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:mushroom_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID MELON_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:melon_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID POTATO_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:potato_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID PUMPKIN_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:pumpkin_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID WHEAT_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:wheat_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID COW_HAT_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:cow_hat".getBytes(StandardCharsets.UTF_8));
    private static final UUID MILK_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:milk_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID MILKSHAKE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:milkshake_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID WOOL_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:wool_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID RAINBOW_WOOL_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:rainbow_wool_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID NETHER_WART_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:nether_wart_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID MUTANT_NETHER_WART_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:mutant_nether_wart_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID LANTERN_HELMET_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:lantern_helmet".getBytes(StandardCharsets.UTF_8));
    private static final UUID SUGAR_CANE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:sugar_cane_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID RABBIT_ARMOR_HELMET_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:rabbit_armor_helmet".getBytes(StandardCharsets.UTF_8));
    private static final Color CACTUS_ARMOR_COLOR = Color.fromRGB(0x00, 0xFF, 0x00);
    /** No exact shade was specified ("tingida de marrom") - a plain chocolate brown, easy to retune. */
    private static final Color CHOCOLATE_ARMOR_COLOR = Color.fromRGB(0x5C, 0x3A, 0x21);
    /** No exact shade was specified ("tingida de vermelho") - matches Cactus Armor's own pure-color convention. */
    private static final Color MUSHROOM_ARMOR_COLOR = Color.fromRGB(0xFF, 0x00, 0x00);
    private static final Color FARMHAND_HELMET_COLOR = Color.fromRGB(0xFD, 0xE8, 0x62);
    private static final Color FARMHAND_CHESTPLATE_COLOR = Color.fromRGB(0xEC, 0x04, 0x1F);
    private static final Color FARMHAND_LEGGINGS_COLOR = Color.fromRGB(0x4A, 0x48, 0x84);
    private static final Color HAYMAKER_COLOR = Color.fromRGB(0xFF, 0xD7, 0x00);
    private static final Color FARMER_BOOTS_COLOR = Color.fromRGB(0xCC, 0x55, 0x00);
    /** No color was specified for the helmet - kept as the original plain green every piece used before the player's own per-piece colors arrived for the other three. */
    private static final Color SPROUT_HELMET_COLOR = Color.fromRGB(0x4C, 0xAF, 0x50);
    private static final Color SPROUT_CHESTPLATE_COLOR = Color.fromRGB(0xFF, 0xA7, 0x3F);
    private static final Color SPROUT_LEGGINGS_COLOR = Color.fromRGB(0xFF, 0x8E, 0x09);
    private static final Color SPROUT_BOOTS_COLOR = Color.fromRGB(0xAC, 0x39, 0x00);
    /** Neither Chocolate nor Mushroom Armor's own recipe ingredient was specified (only their unlock milestones and their Core items' ingredients were) - Chocolate uses Cocoa Beans and Mushroom uses Red Mushroom, matching Cactus Armor's own "the category's own drop material" precedent. */
    private static final int SPROUT_DEFENSE_PER_PIECE = 20;
    /** 8 minutes - see {@link #registerResistancePotionMix}'s own doc on why this is a judgment call. */
    private static final int RESISTANCE_DURATION_TICKS = 9600;
    /** See {@link #createSproutPiece} - not wired to a Farming Fortune source yet, since nothing can obtain this piece at all until a recipe exists. */
    public static final int SPROUT_FARMING_FORTUNE_PER_PIECE = 15;
    private static final int FARMHAND_DEFENSE_PER_PIECE = 10;
    private static final int FARMHAND_FARMING_FORTUNE_PER_PIECE = 5;
    private static final int HAYMAKER_DEFENSE_PER_PIECE = 15;
    private static final int HAYMAKER_FARMING_FORTUNE_PER_PIECE = 10;
    private static final int FARMER_BOOTS_BASE_HEALTH = 40;
    private static final int FARMER_BOOTS_BASE_DEFENSE = 20;
    private static final int FARMER_BOOTS_BASE_SPEED = 10;
    private static final int FARMER_BOOTS_DEFENSE_PER_LEVEL = 2;
    private static final int FARMER_BOOTS_SPEED_PER_LEVEL = 4;
    private static final int FARMER_BOOTS_FORTUNE_PER_LEVEL = 1;
    /** Same "Speed point -> real Movement Speed" conversion {@code ReforgeService#AGILITY_SPEED_PER_POINT} already uses for Agility, so Farmer Boots' own "+10/+4 per level Speed" spec reads on the same scale as every other Speed-granting source in this plugin. */
    private static final double SPEED_POINT_TO_ATTRIBUTE = 0.001;
    private static final NamespacedKey SPROUT_ARMOR_KEY = new NamespacedKey("foodtooltips", "sprout_armor_piece");
    private static final NamespacedKey FARMHAND_ARMOR_KEY = new NamespacedKey("foodtooltips", "farmhand_armor_piece");
    private static final NamespacedKey HAYMAKER_ARMOR_KEY = new NamespacedKey("foodtooltips", "haymaker_armor_piece");
    private static final NamespacedKey FARMER_BOOTS_KEY = new NamespacedKey("foodtooltips", "farmer_boots_piece");
    private static final NamespacedKey FARMER_BOOTS_HEALTH_KEY = new NamespacedKey("foodtooltips", "farmer_boots_health");
    private static final NamespacedKey FARMER_BOOTS_SPEED_KEY = new NamespacedKey("foodtooltips", "farmer_boots_speed");
    private static final NamespacedKey CACTUS_HEALTH_KEY = new NamespacedKey("foodtooltips", "cactus_armor_health");
    private static final NamespacedKey CHOCOLATE_HEALTH_KEY = new NamespacedKey("foodtooltips", "chocolate_armor_health");
    public static final int CHOCOLATE_HEALTH_PER_PIECE = 20;
    /** See {@code MushroomSoupFlightService} - the actual flight-granting logic lives there, this class only builds the item and its recipe. */
    public static final NamespacedKey MAGICAL_MUSHROOM_SOUP_KEY = new NamespacedKey("foodtooltips", "magical_mushroom_soup");
    public static final NamespacedKey MYSTICAL_MUSHROOM_SOUP_KEY = new NamespacedKey("foodtooltips", "mystical_mushroom_soup");
    /** 2 minutes, per the player's own spec. */
    public static final int MAGICAL_MUSHROOM_SOUP_FLIGHT_TICKS = 2 * 60 * 20;
    /** 200 minutes, per the player's own spec. */
    public static final int MYSTICAL_MUSHROOM_SOUP_FLIGHT_TICKS = 200 * 60 * 20;
    /** See {@code CowHatService} - the actual debuff-immunity logic lives there, this class only builds the item and its recipe. */
    public static final NamespacedKey COW_HAT_KEY = new NamespacedKey("foodtooltips", "cow_hat");
    /** See {@code ArcheryPotionService}. No real ingredient/duration was specified - same judgment-call precedent {@link #registerResistancePotionMix} already set, 8 minutes to match. */
    public static final NamespacedKey ARCHERY_POTION_KEY = new NamespacedKey("foodtooltips", "archery_potion");
    public static final int ARCHERY_POTION_DURATION_TICKS = RESISTANCE_DURATION_TICKS;
    public static final double ARCHERY_POTION_PERCENT = 12.5;
    /** See {@code ManaPotionService}. Same judgment-call duration as {@link #ARCHERY_POTION_KEY}. */
    public static final NamespacedKey MANA_POTION_KEY = new NamespacedKey("foodtooltips", "mana_potion");
    public static final int MANA_POTION_DURATION_TICKS = RESISTANCE_DURATION_TICKS;
    public static final double MANA_POTION_REGEN_PER_SECOND = 1.0;
    /** See {@code EnchantedCarrotStickService} - the actual mount-speed-doubling logic lives there. */
    public static final NamespacedKey ENCHANTED_CARROT_STICK_KEY = new NamespacedKey("foodtooltips", "enchanted_carrot_stick");
    public static final double ENCHANTED_CARROT_STICK_MOUNT_SPEED_MULTIPLIER = 2.0;
    private static final Color RABBIT_ARMOR_COLOR = Color.fromRGB(0xCB, 0xD2, 0xDB);
    private static final Color SPEEDSTER_ARMOR_COLOR = Color.fromRGB(0xE0, 0xFC, 0xF7);
    private static final int RABBIT_HELMET_DEFENSE = 15;
    private static final int RABBIT_CHESTPLATE_DEFENSE = 40;
    private static final int RABBIT_LEGGINGS_DEFENSE = 30;
    private static final int RABBIT_BOOTS_DEFENSE = 15;
    private static final int RABBIT_ARMOR_SPEED_PER_PIECE = 5;
    private static final int SPEEDSTER_HELMET_DEFENSE = 45;
    private static final int SPEEDSTER_CHESTPLATE_DEFENSE = 70;
    private static final int SPEEDSTER_LEGGINGS_DEFENSE = 60;
    private static final int SPEEDSTER_BOOTS_DEFENSE = 35;
    private static final int SPEEDSTER_SPEED_PER_PIECE = 15;
    private static final int LANTERN_HELMET_BASE_HEALTH = 20;
    private static final int LANTERN_HELMET_BASE_DEFENSE = 10;
    private static final int LANTERN_HELMET_DEFENSE_PER_LEVEL = 2;
    private static final int LANTERN_HELMET_HEALTH_PER_LEVEL = 4;
    private static final int LANTERN_HELMET_FORTUNE_PER_LEVEL = 1;
    private static final NamespacedKey LANTERN_HELMET_KEY = new NamespacedKey("foodtooltips", "lantern_helmet_piece");
    private static final NamespacedKey LANTERN_HELMET_HEALTH_KEY = new NamespacedKey("foodtooltips", "lantern_helmet_health");
    private static final NamespacedKey RABBIT_ARMOR_SPEED_KEY = new NamespacedKey("foodtooltips", "rabbit_armor_speed");
    private static final NamespacedKey SPEEDSTER_ARMOR_SPEED_KEY = new NamespacedKey("foodtooltips", "speedster_armor_speed");

    private final Plugin plugin;
    private final ItemTierService tiers;
    private final GeneralSkillService general;

    public FarmingCollectionsItemsService(Plugin plugin, ItemTierService tiers, GeneralSkillService general) {
        this.plugin = plugin;
        this.tiers = tiers;
        this.general = general;
    }

    /** Registers every recipe this class owns that's actually ready today (Cactus/Carrot Core, Cactus Armor, the Resistance Potion mix) - Sprout Armor is deliberately not included, see this class's own doc. */
    public void registerRecipes() {
        this.newShapedRecipe(CollectionsCatalog.CACTUS_CORE_RECIPE, this.cactusCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.CACTUS);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.FLOWER_CACTUS_CORE_RECIPE, this.flowerCactusCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', new RecipeChoice.ExactChoice(this.cactusCore()));
                    r.setIngredient('D', Material.NETHERITE_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.CARROT_CORE_RECIPE, this.carrotCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.CARROT);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.GOLDEN_CARROT_CORE_RECIPE, this.goldenCarrotCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', new RecipeChoice.ExactChoice(this.carrotCore()));
                    r.setIngredient('D', Material.NETHERITE_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.CACTUS_HELMET_RECIPE, this.cactusPiece(Material.LEATHER_HELMET, "Cactus Helmet", 5, 10),
                new String[]{"XXX", "X X"}, r -> r.setIngredient('X', Material.CACTUS));
        this.newShapedRecipe(CollectionsCatalog.CACTUS_CHESTPLATE_RECIPE, this.cactusPiece(Material.LEATHER_CHESTPLATE, "Cactus Chestplate", 15, 25),
                new String[]{"X X", "XXX", "XXX"}, r -> r.setIngredient('X', Material.CACTUS));
        this.newShapedRecipe(CollectionsCatalog.CACTUS_LEGGINGS_RECIPE, this.cactusPiece(Material.LEATHER_LEGGINGS, "Cactus Leggings", 10, 20),
                new String[]{"XXX", "X X", "X X"}, r -> r.setIngredient('X', Material.CACTUS));
        this.newShapedRecipe(CollectionsCatalog.CACTUS_BOOTS_RECIPE, this.cactusPiece(Material.LEATHER_BOOTS, "Cactus Boots", 5, 10),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', Material.CACTUS));
        this.registerResistancePotionMix();

        this.newShapedRecipe(CollectionsCatalog.CHOCOLATE_CORE_RECIPE, this.chocolateCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.COCOA_BEANS);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.TRUE_CHOCOLATE_CORE_RECIPE, this.trueChocolateCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', new RecipeChoice.ExactChoice(this.chocolateCore()));
                    r.setIngredient('D', Material.NETHERITE_BLOCK);
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

        Bukkit.removeRecipe(CollectionsCatalog.FEATHER_TALISMAN_RECIPE);
        ShapelessRecipe featherTalisman = new ShapelessRecipe(CollectionsCatalog.FEATHER_TALISMAN_RECIPE, this.featherTalisman());
        for (int i = 0; i < 9; i++) {
            featherTalisman.addIngredient(Material.FEATHER);
        }
        Bukkit.addRecipe(featherTalisman);

        this.newShapedRecipe(CollectionsCatalog.FEATHER_RING_RECIPE, this.featherRing(),
                new String[]{"CCC", "CTC", "CCC"}, r -> {
                    r.setIngredient('C', new RecipeChoice.ExactChoice(this.featherCore()));
                    r.setIngredient('T', new RecipeChoice.ExactChoice(this.featherTalisman()));
                });

        this.newShapedRecipe(CollectionsCatalog.FEATHER_ARTIFACT_RECIPE, this.featherArtifact(),
                new String[]{"CCC", "CNR", "CC "}, r -> {
                    r.setIngredient('C', new RecipeChoice.ExactChoice(this.featherCore()));
                    r.setIngredient('N', Material.NETHERITE_INGOT);
                    r.setIngredient('R', new RecipeChoice.ExactChoice(this.featherRing()));
                });

        // "de qualquer tipo" (either mushroom color) - the Core recipe's own explicit spec.
        RecipeChoice.MaterialChoice anyMushroom = new RecipeChoice.MaterialChoice(Material.RED_MUSHROOM, Material.BROWN_MUSHROOM);
        this.newShapedRecipe(CollectionsCatalog.MUSHROOM_CORE_RECIPE, this.mushroomCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', anyMushroom);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.MUSHROOM_HELMET_RECIPE, this.mushroomPiece(Material.LEATHER_HELMET, "Mushroom Helmet", MushroomArmorService.HELMET_HEALTH, 0),
                new String[]{"XXX", "X X"}, r -> r.setIngredient('X', Material.RED_MUSHROOM));
        this.newShapedRecipe(CollectionsCatalog.MUSHROOM_CHESTPLATE_RECIPE, this.mushroomPiece(Material.LEATHER_CHESTPLATE, "Mushroom Chestplate", MushroomArmorService.CHESTPLATE_HEALTH, MushroomArmorService.CHESTPLATE_DEFENSE),
                new String[]{"X X", "XXX", "XXX"}, r -> r.setIngredient('X', Material.RED_MUSHROOM));
        this.newShapedRecipe(CollectionsCatalog.MUSHROOM_LEGGINGS_RECIPE, this.mushroomPiece(Material.LEATHER_LEGGINGS, "Mushroom Leggings", MushroomArmorService.LEGGINGS_HEALTH, MushroomArmorService.LEGGINGS_DEFENSE),
                new String[]{"XXX", "X X", "X X"}, r -> r.setIngredient('X', Material.RED_MUSHROOM));
        this.newShapedRecipe(CollectionsCatalog.MUSHROOM_BOOTS_RECIPE, this.mushroomPiece(Material.LEATHER_BOOTS, "Mushroom Boots", MushroomArmorService.BOOTS_HEALTH, 0),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', Material.RED_MUSHROOM));

        this.newShapedRecipe(CollectionsCatalog.MELON_CORE_RECIPE, this.melonCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.MELON_SLICE);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.POTATO_CORE_RECIPE, this.potatoCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.POTATO);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.PUMPKIN_CORE_RECIPE, this.pumpkinCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.PUMPKIN);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.WHEAT_CORE_RECIPE, this.wheatCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.WHEAT);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });

        this.newShapedRecipe(CollectionsCatalog.FARMHAND_HELMET_RECIPE, this.farmhandPiece(Material.LEATHER_HELMET, "Farmhand Helmet", FARMHAND_HELMET_COLOR),
                new String[]{"XXX", "X X"}, r -> r.setIngredient('X', Material.HAY_BLOCK));
        this.newShapedRecipe(CollectionsCatalog.FARMHAND_CHESTPLATE_RECIPE, this.farmhandPiece(Material.LEATHER_CHESTPLATE, "Farmhand Chestplate", FARMHAND_CHESTPLATE_COLOR),
                new String[]{"X X", "XXX", "XXX"}, r -> r.setIngredient('X', Material.HAY_BLOCK));
        this.newShapedRecipe(CollectionsCatalog.FARMHAND_LEGGINGS_RECIPE, this.farmhandPiece(Material.LEATHER_LEGGINGS, "Farmhand Leggings", FARMHAND_LEGGINGS_COLOR),
                new String[]{"XXX", "X X", "X X"}, r -> r.setIngredient('X', Material.HAY_BLOCK));
        // "botas de couro na cor padrão" - the one Farmhand piece deliberately left undyed.
        this.newShapedRecipe(CollectionsCatalog.FARMHAND_BOOTS_RECIPE, this.farmhandPiece(Material.LEATHER_BOOTS, "Farmhand Boots", null),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', Material.HAY_BLOCK));

        // Haymaker Armor: an upgrade recipe, not a from-scratch one - the corresponding
        // Farmhand piece exactly (RecipeChoice.ExactChoice, so a plain dyed leather piece
        // that merely looks similar can't be substituted) surrounded by 8 Wheat Cores.
        this.newHaymakerRecipe(CollectionsCatalog.HAYMAKER_HELMET_RECIPE, this.haymakerPiece(Material.LEATHER_HELMET, "Haymaker Helmet"),
                this.farmhandPiece(Material.LEATHER_HELMET, "Farmhand Helmet", FARMHAND_HELMET_COLOR));
        this.newHaymakerRecipe(CollectionsCatalog.HAYMAKER_CHESTPLATE_RECIPE, this.haymakerPiece(Material.LEATHER_CHESTPLATE, "Haymaker Chestplate"),
                this.farmhandPiece(Material.LEATHER_CHESTPLATE, "Farmhand Chestplate", FARMHAND_CHESTPLATE_COLOR));
        this.newHaymakerRecipe(CollectionsCatalog.HAYMAKER_LEGGINGS_RECIPE, this.haymakerPiece(Material.LEATHER_LEGGINGS, "Haymaker Leggings"),
                this.farmhandPiece(Material.LEATHER_LEGGINGS, "Farmhand Leggings", FARMHAND_LEGGINGS_COLOR));
        this.newHaymakerRecipe(CollectionsCatalog.HAYMAKER_BOOTS_RECIPE, this.haymakerPiece(Material.LEATHER_BOOTS, "Haymaker Boots"),
                this.farmhandPiece(Material.LEATHER_BOOTS, "Farmhand Boots", null));

        this.registerAdrenalinePotionMix();

        this.newShapedRecipe(CollectionsCatalog.FARMER_BOOTS_RECIPE, this.farmerBoots(),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', new RecipeChoice.ExactChoice(this.pumpkinCore())));

        Bukkit.removeRecipe(CollectionsCatalog.MAGICAL_MUSHROOM_SOUP_RECIPE);
        ShapelessRecipe magicalSoup = new ShapelessRecipe(CollectionsCatalog.MAGICAL_MUSHROOM_SOUP_RECIPE, this.magicalMushroomSoup());
        magicalSoup.addIngredient(Material.BOWL);
        magicalSoup.addIngredient(Material.RED_MUSHROOM);
        magicalSoup.addIngredient(Material.BROWN_MUSHROOM);
        magicalSoup.addIngredient(Material.REDSTONE);
        Bukkit.addRecipe(magicalSoup);

        Bukkit.removeRecipe(CollectionsCatalog.MYSTICAL_MUSHROOM_SOUP_RECIPE);
        ShapelessRecipe mysticalSoup = new ShapelessRecipe(CollectionsCatalog.MYSTICAL_MUSHROOM_SOUP_RECIPE, this.mysticalMushroomSoup());
        mysticalSoup.addIngredient(Material.BOWL);
        mysticalSoup.addIngredient(new RecipeChoice.ExactChoice(this.mushroomCore()));
        mysticalSoup.addIngredient(new RecipeChoice.ExactChoice(this.mushroomCore()));
        mysticalSoup.addIngredient(Material.REDSTONE);
        Bukkit.addRecipe(mysticalSoup);

        this.newShapedRecipe(CollectionsCatalog.FARM_CRYSTAL_RECIPE, FarmCrystalService.createItem(),
                new String[]{"PPP", "PDP", "PPP"}, r -> {
                    r.setIngredient('P', new RecipeChoice.ExactChoice(this.pumpkinCore()));
                    r.setIngredient('D', Material.DIAMOND);
                });
        this.newShapedRecipe(CollectionsCatalog.LANTERN_HELMET_RECIPE, this.lanternHelmet(),
                new String[]{"PPP", "P P"}, r -> r.setIngredient('P', new RecipeChoice.ExactChoice(this.pumpkinCore())));

        // Sprout Armor: an upgrade recipe, not a from-scratch one - same "Core surrounds the
        // matching upgrade-source piece" shape as newHaymakerRecipe, but 8 Carrot Cores
        // around the corresponding Haymaker piece.
        this.newSproutRecipe(CollectionsCatalog.SPROUT_HELMET_RECIPE, this.createSproutPiece(Material.LEATHER_HELMET, "Sprout Helmet"),
                this.haymakerPiece(Material.LEATHER_HELMET, "Haymaker Helmet"));
        this.newSproutRecipe(CollectionsCatalog.SPROUT_CHESTPLATE_RECIPE, this.createSproutPiece(Material.LEATHER_CHESTPLATE, "Sprout Chestplate"),
                this.haymakerPiece(Material.LEATHER_CHESTPLATE, "Haymaker Chestplate"));
        this.newSproutRecipe(CollectionsCatalog.SPROUT_LEGGINGS_RECIPE, this.createSproutPiece(Material.LEATHER_LEGGINGS, "Sprout Leggings"),
                this.haymakerPiece(Material.LEATHER_LEGGINGS, "Haymaker Leggings"));
        this.newSproutRecipe(CollectionsCatalog.SPROUT_BOOTS_RECIPE, this.createSproutPiece(Material.LEATHER_BOOTS, "Sprout Boots"),
                this.haymakerPiece(Material.LEATHER_BOOTS, "Haymaker Boots"));

        Bukkit.removeRecipe(CollectionsCatalog.ENCHANTED_CARROT_STICK_RECIPE);
        ShapelessRecipe enchantedCarrotStick = new ShapelessRecipe(CollectionsCatalog.ENCHANTED_CARROT_STICK_RECIPE, this.enchantedCarrotOnAStick());
        enchantedCarrotStick.addIngredient(Material.FISHING_ROD);
        enchantedCarrotStick.addIngredient(new RecipeChoice.ExactChoice(this.carrotCore()));
        Bukkit.addRecipe(enchantedCarrotStick);

        this.registerArcheryPotionMix();
        this.registerManaPotionMix();

        Bukkit.removeRecipe(CollectionsCatalog.COW_HAT_RECIPE);
        ShapelessRecipe cowHat = new ShapelessRecipe(CollectionsCatalog.COW_HAT_RECIPE, this.cowHat());
        for (int i = 0; i < 8; i++) {
            cowHat.addIngredient(Material.BEEF);
        }
        Bukkit.addRecipe(cowHat);

        this.newShapedRecipe(CollectionsCatalog.MILK_CORE_RECIPE, this.milkCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.BEEF);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.MILKSHAKE_CORE_RECIPE, this.milkshakeCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', new RecipeChoice.ExactChoice(this.milkCore()));
                    r.setIngredient('D', Material.NETHERITE_BLOCK);
                });

        this.newShapedRecipe(CollectionsCatalog.WOOL_CORE_RECIPE, this.woolCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.WHITE_WOOL);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.RAINBOW_WOOL_CORE_RECIPE, this.rainbowWoolCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', new RecipeChoice.ExactChoice(this.woolCore()));
                    r.setIngredient('D', Material.NETHERITE_BLOCK);
                });

        this.newShapedRecipe(CollectionsCatalog.NETHER_WART_CORE_RECIPE, this.netherWartCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.NETHER_WART);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.MUTANT_NETHER_WART_CORE_RECIPE, this.mutantNetherWartCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', new RecipeChoice.ExactChoice(this.netherWartCore()));
                    r.setIngredient('D', Material.NETHERITE_BLOCK);
                });

        this.newShapedRecipe(CollectionsCatalog.SUGAR_CANE_CORE_RECIPE, this.sugarCaneCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.SUGAR_CANE);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });

        this.newShapedRecipe(CollectionsCatalog.RABBIT_HELMET_RECIPE, this.rabbitHelmet(),
                new String[]{"XXX", "X X"}, r -> r.setIngredient('X', Material.RABBIT_HIDE));
        this.newShapedRecipe(CollectionsCatalog.RABBIT_CHESTPLATE_RECIPE, this.rabbitPiece(Material.LEATHER_CHESTPLATE, "Rabbit Chestplate", RABBIT_CHESTPLATE_DEFENSE),
                new String[]{"X X", "XXX", "XXX"}, r -> r.setIngredient('X', Material.RABBIT_HIDE));
        this.newShapedRecipe(CollectionsCatalog.RABBIT_LEGGINGS_RECIPE, this.rabbitPiece(Material.LEATHER_LEGGINGS, "Rabbit Leggings", RABBIT_LEGGINGS_DEFENSE),
                new String[]{"XXX", "X X", "X X"}, r -> r.setIngredient('X', Material.RABBIT_HIDE));
        this.newShapedRecipe(CollectionsCatalog.RABBIT_BOOTS_RECIPE, this.rabbitPiece(Material.LEATHER_BOOTS, "Rabbit Boots", RABBIT_BOOTS_DEFENSE),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', Material.RABBIT_HIDE));

        this.newShapedRecipe(CollectionsCatalog.SPEEDSTER_HELMET_RECIPE, this.speedsterPiece(Material.LEATHER_HELMET, "Speedster Helmet", SPEEDSTER_HELMET_DEFENSE),
                new String[]{"XXX", "X X"}, r -> r.setIngredient('X', new RecipeChoice.ExactChoice(this.sugarCaneCore())));
        this.newShapedRecipe(CollectionsCatalog.SPEEDSTER_CHESTPLATE_RECIPE, this.speedsterPiece(Material.LEATHER_CHESTPLATE, "Speedster Chestplate", SPEEDSTER_CHESTPLATE_DEFENSE),
                new String[]{"X X", "XXX", "XXX"}, r -> r.setIngredient('X', new RecipeChoice.ExactChoice(this.sugarCaneCore())));
        this.newShapedRecipe(CollectionsCatalog.SPEEDSTER_LEGGINGS_RECIPE, this.speedsterPiece(Material.LEATHER_LEGGINGS, "Speedster Leggings", SPEEDSTER_LEGGINGS_DEFENSE),
                new String[]{"XXX", "X X", "X X"}, r -> r.setIngredient('X', new RecipeChoice.ExactChoice(this.sugarCaneCore())));
        this.newShapedRecipe(CollectionsCatalog.SPEEDSTER_BOOTS_RECIPE, this.speedsterPiece(Material.LEATHER_BOOTS, "Speedster Boots", SPEEDSTER_BOOTS_DEFENSE),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', new RecipeChoice.ExactChoice(this.sugarCaneCore())));
    }

    /** Sprout Armor's own upgrade-recipe shape: the matching Haymaker piece exactly, dead center, surrounded by 8 Carrot Cores - see {@link #newHaymakerRecipe}, the exact same idea one Core-family earlier. */
    private void newSproutRecipe(NamespacedKey key, ItemStack result, ItemStack haymakerPiece) {
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("CCC", "CFC", "CCC");
        recipe.setIngredient('C', new RecipeChoice.ExactChoice(this.carrotCore()));
        recipe.setIngredient('F', new RecipeChoice.ExactChoice(haymakerPiece));
        Bukkit.addRecipe(recipe);
    }

    /** Magical Mushroom Soup: grants {@value #MAGICAL_MUSHROOM_SOUP_FLIGHT_TICKS}-tick flight on drink, cumulative across multiple - see {@code MushroomSoupFlightService}, which reads {@link #MAGICAL_MUSHROOM_SOUP_KEY} to tell this apart from a plain Mushroom Stew. {@link org.bukkit.inventory.meta.components.FoodComponent#setCanAlwaysEat} lets it be drunk regardless of hunger - a flight potion, not a meal, so gating it behind being hungry would make no sense (the player's own spec: "não deve ser necessário ter fome para consumir essas sopas"). */
    private ItemStack magicalMushroomSoup() {
        var item = new ItemStack(Material.MUSHROOM_STEW);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(MAGICAL_MUSHROOM_SOUP_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Magical Mushroom Soup", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        var food = meta.getFood();
        food.setCanAlwaysEat(true);
        meta.setFood(food);
        item.setItemMeta(meta);
        return item;
    }

    /** Mystical Mushroom Soup: grants {@value #MYSTICAL_MUSHROOM_SOUP_FLIGHT_TICKS}-tick flight on drink, cumulative across multiple - see {@code MushroomSoupFlightService}. Always drinkable regardless of hunger, same reasoning as {@link #magicalMushroomSoup}. */
    private ItemStack mysticalMushroomSoup() {
        var item = new ItemStack(Material.MUSHROOM_STEW);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(MYSTICAL_MUSHROOM_SOUP_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Mystical Mushroom Soup", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        var food = meta.getFood();
        food.setCanAlwaysEat(true);
        meta.setFood(food);
        item.setItemMeta(meta);
        return item;
    }

    /** Haymaker's own upgrade-recipe shape: the matching Farmhand piece exactly, dead center, surrounded by 8 Wheat Cores. */
    private void newHaymakerRecipe(NamespacedKey key, ItemStack result, ItemStack farmhandPiece) {
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("WWW", "WFW", "WWW");
        recipe.setIngredient('W', new RecipeChoice.ExactChoice(this.wheatCore()));
        recipe.setIngredient('F', new RecipeChoice.ExactChoice(farmhandPiece));
        Bukkit.addRecipe(recipe);
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

    /**
     * "aumenta absorção e velocidade" - no exact numbers were specified, so this follows the
     * same judgment-call pattern {@link #registerResistancePotionMix} already set: an Awkward
     * Potion base, Cocoa Beans as the "central material" (this potion's own Collections source,
     * Cocoa Beans M2 - {@link Material#SUGAR} was tried first but collides with vanilla's own
     * real Swiftness Potion recipe, which also uses Sugar as its Awkward-potion ingredient, so
     * it was replaced with the collection's own material instead), Absorption II + Speed II for
     * {@link #RESISTANCE_DURATION_TICKS}' same 8-minute duration. Same "unlocked in name only,
     * no real Brewing Stand gate" limitation as Resistance - see the catalog's own doc on
     * {@code CollectionsCatalog}'s Cocoa Beans M2.
     */
    private void registerAdrenalinePotionMix() {
        var awkward = new org.bukkit.inventory.ItemStack(Material.POTION);
        PotionMeta awkwardMeta = (PotionMeta) awkward.getItemMeta();
        awkwardMeta.setBasePotionType(PotionType.AWKWARD);
        awkward.setItemMeta(awkwardMeta);

        var adrenaline = new org.bukkit.inventory.ItemStack(Material.POTION);
        PotionMeta adrenalineMeta = (PotionMeta) adrenaline.getItemMeta();
        adrenalineMeta.setBasePotionType(PotionType.MUNDANE);
        adrenalineMeta.addCustomEffect(new PotionEffect(PotionEffectType.ABSORPTION, RESISTANCE_DURATION_TICKS, 1), true);
        adrenalineMeta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, RESISTANCE_DURATION_TICKS, 1), true);
        adrenalineMeta.setColor(Color.fromRGB(0xE0, 0x1B, 0x24));
        adrenalineMeta.displayName(Component.text("Adrenaline Potion", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        adrenaline.setItemMeta(adrenalineMeta);

        NamespacedKey key = new NamespacedKey(this.plugin, "adrenaline_from_cocoa_beans");
        Bukkit.getPotionBrewer().removePotionMix(key);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, adrenaline,
                new RecipeChoice.ExactChoice(awkward), new RecipeChoice.MaterialChoice(Material.COCOA_BEANS)));
    }

    /**
     * Archery Potion: no duration was specified ("cada nível aumentará em 12,5% o dano com
     * arco e flecha") - a Feather as the "central material" (this potion's own Collections
     * source, Feather M6 - {@link Material#ARROW} was tried first but doesn't actually collide
     * with any real vanilla recipe, it was simply swapped to match the potion's own collection
     * instead) and {@link #ARCHERY_POTION_DURATION_TICKS}'s same 8-minute judgment call. No real
     * vanilla {@link PotionEffectType} exists for a bow-damage bonus, so this carries no actual
     * potion effect at all - just the {@link #ARCHERY_POTION_KEY} marker {@code
     * ArcheryPotionService} reads on drink, same "a plain flag on an otherwise ordinary potion"
     * shape the Mushroom Soups use for their own flight.
     */
    private void registerArcheryPotionMix() {
        var awkward = new org.bukkit.inventory.ItemStack(Material.POTION);
        PotionMeta awkwardMeta = (PotionMeta) awkward.getItemMeta();
        awkwardMeta.setBasePotionType(PotionType.AWKWARD);
        awkward.setItemMeta(awkwardMeta);

        var archery = new org.bukkit.inventory.ItemStack(Material.POTION);
        PotionMeta archeryMeta = (PotionMeta) archery.getItemMeta();
        archeryMeta.setBasePotionType(PotionType.MUNDANE);
        archeryMeta.getPersistentDataContainer().set(ARCHERY_POTION_KEY, PersistentDataType.BYTE, (byte) 1);
        archeryMeta.setColor(Color.fromRGB(0x4C, 0xAF, 0x50));
        archeryMeta.displayName(Component.text("Archery Potion", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        archery.setItemMeta(archeryMeta);

        NamespacedKey key = new NamespacedKey(this.plugin, "archery_from_feather");
        Bukkit.getPotionBrewer().removePotionMix(key);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, archery,
                new RecipeChoice.ExactChoice(awkward), new RecipeChoice.MaterialChoice(Material.FEATHER)));
    }

    /**
     * Mana Potion: no duration was specified either - raw Mutton as the "central material"
     * (this potion's own Collections source, Raw Mutton M2 - Lapis Lazuli was tried first but
     * was swapped to match the potion's own collection instead) and the same 8-minute judgment
     * call as {@link #registerArcheryPotionMix}. Same "no real potion effect, just a marker"
     * shape too - see {@code ManaPotionService}, which reads {@link #MANA_POTION_KEY}.
     */
    private void registerManaPotionMix() {
        var awkward = new org.bukkit.inventory.ItemStack(Material.POTION);
        PotionMeta awkwardMeta = (PotionMeta) awkward.getItemMeta();
        awkwardMeta.setBasePotionType(PotionType.AWKWARD);
        awkward.setItemMeta(awkwardMeta);

        var mana = new org.bukkit.inventory.ItemStack(Material.POTION);
        PotionMeta manaMeta = (PotionMeta) mana.getItemMeta();
        manaMeta.setBasePotionType(PotionType.MUNDANE);
        manaMeta.getPersistentDataContainer().set(MANA_POTION_KEY, PersistentDataType.BYTE, (byte) 1);
        manaMeta.setColor(Color.fromRGB(0x00, 0xBF, 0xFF));
        manaMeta.displayName(Component.text("Mana Potion", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        mana.setItemMeta(manaMeta);

        NamespacedKey key = new NamespacedKey(this.plugin, "mana_from_mutton");
        Bukkit.getPotionBrewer().removePotionMix(key);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, mana,
                new RecipeChoice.ExactChoice(awkward), new RecipeChoice.MaterialChoice(Material.MUTTON)));
    }

    private org.bukkit.inventory.ItemStack cactusCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.CACTUS_CORE, CACTUS_CORE_PROFILE);
        meta.displayName(Component.text("Cactus Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** Cactus Collection M8's own upgrade to {@link #cactusCore()} - same "8 of the base Core around a Netherite Block" shape {@link #mutantNetherWartCore()}/{@link #milkshakeCore()}/{@link #rainbowWoolCore()} already use for their own second-tier core, per the player's own explicit spec. */
    private org.bukkit.inventory.ItemStack flowerCactusCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.FLOWER_CACTUS_CORE, FLOWER_CACTUS_CORE_PROFILE);
        meta.displayName(Component.text("Flower Cactus Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
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

    /** Carrot Collection M7's own upgrade to {@link #carrotCore()} - same "8 of the base Core around a Netherite Block" shape as {@link #flowerCactusCore()}, per the player's own explicit spec. */
    private org.bukkit.inventory.ItemStack goldenCarrotCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.GOLDEN_CARROT_CORE, GOLDEN_CARROT_CORE_PROFILE);
        meta.displayName(Component.text("Golden Carrot Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One piece of Cactus Armor: dyed-green leather, {@code health}/{@code defense} forced
     * (flat, no per-level or day/night scaling - a real {@link Attribute#MAX_HEALTH} modifier
     * baked once here, same as Farmer Boots' own baseline Health) plus the full-set 33% damage
     * reflect ({@link CactusArmorService#REFLECT_PERCENT}) mentioned in every piece's own lore
     * regardless of which one the player is looking at.
     */
    private org.bukkit.inventory.ItemStack cactusPiece(Material material, String name, int health, int defense) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(CACTUS_ARMOR_COLOR);
        }
        EquipmentSlotGroup slot = material.name().endsWith("_HELMET") ? EquipmentSlotGroup.HEAD
                : material.name().endsWith("_CHESTPLATE") ? EquipmentSlotGroup.CHEST
                : material.name().endsWith("_LEGGINGS") ? EquipmentSlotGroup.LEGS
                : EquipmentSlotGroup.FEET;
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(CACTUS_HEALTH_KEY, health, AttributeModifier.Operation.ADD_NUMBER, slot));
        ArmorDefenseService.forceDefense(meta, defense);
        ArmorDefenseService.markOwnDefenseLore(meta);
        CactusArmorService.markCactusPiece(meta);
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Health: +" + health, NamedTextColor.RED),
                Component.text("Defense: +" + defense, NamedTextColor.GREEN),
                Component.empty(),
                Component.text("Full Set Bonus:", NamedTextColor.GRAY),
                Component.text("Reflects " + Math.round(CactusArmorService.REFLECT_PERCENT * 100) + "% of damage taken to the attacker", NamedTextColor.LIGHT_PURPLE));
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

    /** Cocoa Beans Collection M7's own upgrade to {@link #chocolateCore()} - same "8 of the base Core around a Netherite Block" shape as {@link #flowerCactusCore()}/{@link #goldenCarrotCore()}, per the player's own explicit spec. */
    private org.bukkit.inventory.ItemStack trueChocolateCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.TRUE_CHOCOLATE_CORE, TRUE_CHOCOLATE_CORE_PROFILE);
        meta.displayName(Component.text("True Chocolate Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
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

    /**
     * Feather Collection M4's own accessory, the first of the Talisman → Ring → Artifact
     * line ({@link #featherRing}/{@link #featherArtifact} each upgrade the one before,
     * consuming it as an ingredient) - equipped in the Accessory Bag's own Talisman slot
     * ({@code skills.AccessoryBagService}), Tier D. No custom head texture was given for
     * this one (unlike the Core items), so it's represented by a plain enchanted Feather
     * instead - easy to swap for a real head texture later.
     */
    private org.bukkit.inventory.ItemStack featherTalisman() {
        var item = new org.bukkit.inventory.ItemStack(Material.FEATHER);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Feather Talisman", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.TALISMAN, 5, 0.0);
        this.tiers.forceTier(meta, ItemTier.D);
        addStatLore(meta,
                Component.text("+5 blocos de altura sem dano de queda", NamedTextColor.AQUA),
                Component.empty(),
                Component.text("Equipe na Bolsa de Acessórios.", NamedTextColor.GRAY),
                Component.text("Só um Talismã pode ficar equipado por vez.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Feather Collection M7's own upgrade to {@link #featherTalisman}, Tier C - equipped
     * in the Accessory Bag's own Ring slot.
     */
    private org.bukkit.inventory.ItemStack featherRing() {
        var item = new org.bukkit.inventory.ItemStack(Material.FEATHER);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Feather Ring", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.RING, 7, 5.0);
        this.tiers.forceTier(meta, ItemTier.C);
        addStatLore(meta,
                Component.text("+7 blocos de altura sem dano de queda", NamedTextColor.AQUA),
                Component.text("-5% de dano de queda", NamedTextColor.AQUA),
                Component.empty(),
                Component.text("Equipe na Bolsa de Acessórios.", NamedTextColor.GRAY),
                Component.text("Só um Anel pode ficar equipado por vez.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Feather Collection M9's own upgrade to {@link #featherRing}, Tier B - equipped in
     * the Accessory Bag's own Artifact slot, the top of this Collection's accessory line.
     */
    private org.bukkit.inventory.ItemStack featherArtifact() {
        var item = new org.bukkit.inventory.ItemStack(Material.FEATHER);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Feather Artifact", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.ARTIFACT, 10, 15.0);
        this.tiers.forceTier(meta, ItemTier.B);
        addStatLore(meta,
                Component.text("+10 blocos de altura sem dano de queda", NamedTextColor.AQUA),
                Component.text("-15% de dano de queda", NamedTextColor.AQUA),
                Component.empty(),
                Component.text("Equipe na Bolsa de Acessórios.", NamedTextColor.GRAY),
                Component.text("Só um Artefato pode ficar equipado por vez.", NamedTextColor.DARK_GRAY));
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

    /**
     * One piece of Chocolate Armor: dyed-brown leather, +{@value #CHOCOLATE_HEALTH_PER_PIECE}
     * Health forced (a real {@link Attribute#MAX_HEALTH} modifier, same pattern as Cactus
     * Armor's own) plus the full-set permanent Saturation bonus ({@link ChocolateArmorService})
     * mentioned in every piece's own lore regardless of which one the player is looking at.
     */
    private org.bukkit.inventory.ItemStack chocolatePiece(Material material, String name) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(CHOCOLATE_ARMOR_COLOR);
        }
        EquipmentSlotGroup slot = material.name().endsWith("_HELMET") ? EquipmentSlotGroup.HEAD
                : material.name().endsWith("_CHESTPLATE") ? EquipmentSlotGroup.CHEST
                : material.name().endsWith("_LEGGINGS") ? EquipmentSlotGroup.LEGS
                : EquipmentSlotGroup.FEET;
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(CHOCOLATE_HEALTH_KEY, CHOCOLATE_HEALTH_PER_PIECE, AttributeModifier.Operation.ADD_NUMBER, slot));
        ChocolateArmorService.markChocolatePiece(meta);
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Health: +" + CHOCOLATE_HEALTH_PER_PIECE, NamedTextColor.RED),
                Component.empty(),
                Component.text("Full Set Bonus:", NamedTextColor.GRAY),
                Component.text("Permanent Saturation", NamedTextColor.LIGHT_PURPLE));
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
    private org.bukkit.inventory.ItemStack mushroomPiece(Material material, String name, int health, int defense) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(MUSHROOM_ARMOR_COLOR);
        }
        if (defense > 0) {
            ArmorDefenseService.forceDefense(meta, defense);
            ArmorDefenseService.markOwnDefenseLore(meta);
        }
        MushroomArmorService.markMushroomPiece(meta);
        meta.displayName(Component.text(name, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Health: +" + health, NamedTextColor.RED));
        if (defense > 0) {
            lore.add(Component.text("Defense: +" + defense, NamedTextColor.GREEN));
        }
        lore.add(Component.text("×3 all stats at night (reforges included)", NamedTextColor.LIGHT_PURPLE));
        if (material == Material.LEATHER_HELMET) {
            lore.add(Component.text("Grants Night Vision while worn", NamedTextColor.AQUA));
        }
        addStatLore(meta, lore.toArray(new Component[0]));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack melonCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.MELON_CORE, MELON_CORE_PROFILE);
        meta.displayName(Component.text("Melon Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack potatoCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.POTATO_CORE, POTATO_CORE_PROFILE);
        meta.displayName(Component.text("Potato Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack pumpkinCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.PUMPKIN_CORE, PUMPKIN_CORE_PROFILE);
        meta.displayName(Component.text("Pumpkin Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack wheatCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.WHEAT_CORE, WHEAT_CORE_PROFILE);
        meta.displayName(Component.text("Wheat Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack milkCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.MILK_CORE, MILK_CORE_PROFILE);
        meta.displayName(Component.text("Milk Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack milkshakeCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.MILKSHAKE_CORE, MILKSHAKE_CORE_PROFILE);
        meta.displayName(Component.text("Milkshake Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack woolCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.WOOL_CORE, WOOL_CORE_PROFILE);
        meta.displayName(Component.text("Wool Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack rainbowWoolCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.RAINBOW_WOOL_CORE, RAINBOW_WOOL_CORE_PROFILE);
        meta.displayName(Component.text("Rainbow Wool Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack netherWartCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.NETHER_WART_CORE, NETHER_WART_CORE_PROFILE);
        meta.displayName(Component.text("Nether Wart Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack mutantNetherWartCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.MUTANT_NETHER_WART_CORE, MUTANT_NETHER_WART_CORE_PROFILE);
        meta.displayName(Component.text("Mutant Nether Wart Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.inventory.ItemStack sugarCaneCore() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.SUGAR_CANE_CORE, SUGAR_CANE_CORE_PROFILE);
        meta.displayName(Component.text("Sugar Cane Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** Cow Hat: worn as a helmet, grants immunity to negative status effects - see {@code CowHatService}, which reads {@link #COW_HAT_KEY} to tell this apart from a plain custom head. */
    private org.bukkit.inventory.ItemStack cowHat() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.COW_HAT, COW_HAT_PROFILE);
        meta.getPersistentDataContainer().set(COW_HAT_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Cow Hat", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta, Component.text("Grants immunity to negative status effects", NamedTextColor.LIGHT_PURPLE));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Enchanted Carrot on a Stick: doubles any mount's speed while held in hand (main or
     * off) - see {@code EnchantedCarrotStickService}, which reads
     * {@link #ENCHANTED_CARROT_STICK_KEY} to tell this apart from vanilla's own plain Carrot
     * on a Stick.
     */
    private org.bukkit.inventory.ItemStack enchantedCarrotOnAStick() {
        var item = new org.bukkit.inventory.ItemStack(Material.CARROT_ON_A_STICK);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ENCHANTED_CARROT_STICK_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Enchanted Carrot on a Stick", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta, Component.text("Doubles any mount's speed while held", NamedTextColor.LIGHT_PURPLE));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Lantern Helmet: +{@value #LANTERN_HELMET_BASE_HEALTH} Health/+{@value
     * #LANTERN_HELMET_BASE_DEFENSE} Defense baked once (Health via a real {@link
     * Attribute#MAX_HEALTH} modifier refreshed every tick by {@link
     * #applyLanternHelmetHealth} to add its own per-level term, same pattern {@link
     * #applyFarmerBootsSpeed} uses for Farmer Boots' own Speed) - Defense's own per-level
     * term is late-bound instead (see {@link #farmerBootsDefenseBonus}), and Farming
     * Fortune only while an axe is held (see {@link #farmingFortuneBonus}).
     */
    private org.bukkit.inventory.ItemStack lanternHelmet() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.LANTERN_HELMET, LANTERN_HELMET_PROFILE);
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(LANTERN_HELMET_HEALTH_KEY, LANTERN_HELMET_BASE_HEALTH, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
        ArmorDefenseService.forceDefense(meta, LANTERN_HELMET_BASE_DEFENSE);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.getPersistentDataContainer().set(LANTERN_HELMET_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Lantern Helmet", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Health: +" + LANTERN_HELMET_BASE_HEALTH, NamedTextColor.RED),
                Component.text("Defense: +" + LANTERN_HELMET_BASE_DEFENSE, NamedTextColor.GREEN),
                Component.empty(),
                Component.text("Per Farming level:", NamedTextColor.GRAY),
                Component.text("Health: +" + LANTERN_HELMET_HEALTH_PER_LEVEL, NamedTextColor.RED),
                Component.text("Defense: +" + LANTERN_HELMET_DEFENSE_PER_LEVEL, NamedTextColor.GREEN),
                Component.text("☘ Farming Fortune: +" + LANTERN_HELMET_FORTUNE_PER_LEVEL + " (axe in hand)", NamedTextColor.GOLD));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One piece of Rabbit Armor: dyed #CBD2DB leather (except the helmet, a custom head -
     * see below), {@code defense} forced plus a flat +{@value #RABBIT_ARMOR_SPEED_PER_PIECE}
     * Speed baked as a real {@link Attribute#MOVEMENT_SPEED} modifier (no per-level scaling,
     * unlike Farmer Boots' own) - the full-set sneaking Jump Boost II bonus lives in {@code
     * RabbitArmorService}.
     */
    private org.bukkit.inventory.ItemStack rabbitPiece(Material material, String name, int defense) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(RABBIT_ARMOR_COLOR);
        }
        EquipmentSlotGroup slot = material.name().endsWith("_HELMET") ? EquipmentSlotGroup.HEAD
                : material.name().endsWith("_CHESTPLATE") ? EquipmentSlotGroup.CHEST
                : material.name().endsWith("_LEGGINGS") ? EquipmentSlotGroup.LEGS
                : EquipmentSlotGroup.FEET;
        ArmorDefenseService.forceDefense(meta, defense);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                new AttributeModifier(RABBIT_ARMOR_SPEED_KEY, RABBIT_ARMOR_SPEED_PER_PIECE * SPEED_POINT_TO_ATTRIBUTE, AttributeModifier.Operation.ADD_NUMBER, slot));
        RabbitArmorService.markRabbitPiece(meta);
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Defense: +" + defense, NamedTextColor.GREEN),
                Component.text("Speed: +" + RABBIT_ARMOR_SPEED_PER_PIECE, NamedTextColor.WHITE),
                Component.empty(),
                Component.text("Full Set Bonus:", NamedTextColor.GRAY),
                Component.text("Permanent Jump Boost II while sneaking", NamedTextColor.LIGHT_PURPLE));
        item.setItemMeta(meta);
        return item;
    }

    /** The Rabbit Armor's own helmet: a custom head (Rabbit texture) instead of dyed leather - see {@link #rabbitPiece}'s own doc, everything else about it (Defense/Speed/markers) is identical. */
    private org.bukkit.inventory.ItemStack rabbitHelmet() {
        var item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.RABBIT_ARMOR_HELMET, RABBIT_ARMOR_HELMET_PROFILE);
        ArmorDefenseService.forceDefense(meta, RABBIT_HELMET_DEFENSE);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                new AttributeModifier(RABBIT_ARMOR_SPEED_KEY, RABBIT_ARMOR_SPEED_PER_PIECE * SPEED_POINT_TO_ATTRIBUTE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
        RabbitArmorService.markRabbitPiece(meta);
        meta.displayName(Component.text("Rabbit Helmet", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Defense: +" + RABBIT_HELMET_DEFENSE, NamedTextColor.GREEN),
                Component.text("Speed: +" + RABBIT_ARMOR_SPEED_PER_PIECE, NamedTextColor.WHITE),
                Component.empty(),
                Component.text("Full Set Bonus:", NamedTextColor.GRAY),
                Component.text("Permanent Jump Boost II while sneaking", NamedTextColor.LIGHT_PURPLE));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One piece of Speedster Armor: dyed #E0FCF7 leather, {@code defense} forced plus a flat
     * +{@value #SPEEDSTER_SPEED_PER_PIECE} Speed baked as a real {@link
     * Attribute#MOVEMENT_SPEED} modifier per piece (same "no per-level scaling" shape as
     * Rabbit Armor's own) - the full-set +{@value SpeedsterArmorService#FULL_SET_SPEED_BONUS}
     * Speed bonus lives in {@code SpeedsterArmorService}.
     */
    private org.bukkit.inventory.ItemStack speedsterPiece(Material material, String name, int defense) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(SPEEDSTER_ARMOR_COLOR);
        }
        EquipmentSlotGroup slot = material.name().endsWith("_HELMET") ? EquipmentSlotGroup.HEAD
                : material.name().endsWith("_CHESTPLATE") ? EquipmentSlotGroup.CHEST
                : material.name().endsWith("_LEGGINGS") ? EquipmentSlotGroup.LEGS
                : EquipmentSlotGroup.FEET;
        ArmorDefenseService.forceDefense(meta, defense);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                new AttributeModifier(SPEEDSTER_ARMOR_SPEED_KEY, SPEEDSTER_SPEED_PER_PIECE * SPEED_POINT_TO_ATTRIBUTE, AttributeModifier.Operation.ADD_NUMBER, slot));
        SpeedsterArmorService.markSpeedsterPiece(meta);
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Defense: +" + defense, NamedTextColor.GREEN),
                Component.text("Speed: +" + SPEEDSTER_SPEED_PER_PIECE, NamedTextColor.WHITE),
                Component.empty(),
                Component.text("Full Set Bonus:", NamedTextColor.GRAY),
                Component.text("Speed: +" + SpeedsterArmorService.FULL_SET_SPEED_BONUS, NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    /** One piece of Farmhand Armor: +{@value #FARMHAND_DEFENSE_PER_PIECE} Defense/+{@value #FARMHAND_FARMING_FORTUNE_PER_PIECE} Farming Fortune, flat (see {@link #farmingFortuneBonus}) - {@code color} is {@code null} for the one piece left undyed ("cor padrão" - the boots). */
    private org.bukkit.inventory.ItemStack farmhandPiece(Material material, String name, Color color) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather && color != null) {
            leather.setColor(color);
        }
        ArmorDefenseService.forceDefense(meta, FARMHAND_DEFENSE_PER_PIECE);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.getPersistentDataContainer().set(FARMHAND_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Defense: +" + FARMHAND_DEFENSE_PER_PIECE, NamedTextColor.GREEN),
                Component.text("☘ Farming Fortune: +" + FARMHAND_FARMING_FORTUNE_PER_PIECE, NamedTextColor.GOLD));
        item.setItemMeta(meta);
        return item;
    }

    /** One piece of Haymaker Armor (the Farmhand upgrade): +{@value #HAYMAKER_DEFENSE_PER_PIECE} Defense/+{@value #HAYMAKER_FARMING_FORTUNE_PER_PIECE} Farming Fortune, flat - see {@link #farmingFortuneBonus} and {@link #newHaymakerRecipe}. */
    private org.bukkit.inventory.ItemStack haymakerPiece(Material material, String name) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(HAYMAKER_COLOR);
        }
        ArmorDefenseService.forceDefense(meta, HAYMAKER_DEFENSE_PER_PIECE);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.getPersistentDataContainer().set(HAYMAKER_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Defense: +" + HAYMAKER_DEFENSE_PER_PIECE, NamedTextColor.GREEN),
                Component.text("☘ Farming Fortune: +" + HAYMAKER_FARMING_FORTUNE_PER_PIECE, NamedTextColor.GOLD));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Farmer Boots: {@value #FARMER_BOOTS_BASE_HEALTH} Health baked once (flat, no per-level
     * term in the player's own spec, unlike Defense/Speed/Fortune below) as a real {@link
     * Attribute#MAX_HEALTH} modifier - Defense ({@link #farmerBootsDefenseBonus}, wired into
     * {@code ArmorDefenseService#farmerBootsBonus}) and Speed ({@link #applyFarmerBootsSpeed},
     * a per-tick baked modifier - see its own doc) both scale with the wearer's current
     * Farming level, so they can't be baked once here the way Health can.
     */
    private org.bukkit.inventory.ItemStack farmerBoots() {
        var item = new org.bukkit.inventory.ItemStack(Material.LEATHER_BOOTS);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(FARMER_BOOTS_COLOR);
        }
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(FARMER_BOOTS_HEALTH_KEY, FARMER_BOOTS_BASE_HEALTH, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.getPersistentDataContainer().set(FARMER_BOOTS_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Farmer Boots", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Health: +" + FARMER_BOOTS_BASE_HEALTH, NamedTextColor.RED),
                Component.text("Defense: +" + FARMER_BOOTS_BASE_DEFENSE, NamedTextColor.GREEN),
                Component.text("Speed: +" + FARMER_BOOTS_BASE_SPEED, NamedTextColor.WHITE),
                Component.empty(),
                Component.text("Per Farming level:", NamedTextColor.GRAY),
                Component.text("☘ Farming Fortune: +" + FARMER_BOOTS_FORTUNE_PER_LEVEL, NamedTextColor.GOLD),
                Component.text("Defense: +" + FARMER_BOOTS_DEFENSE_PER_LEVEL, NamedTextColor.GREEN),
                Component.text("Speed: +" + FARMER_BOOTS_SPEED_PER_LEVEL, NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Sum of every equipped piece's own flat Farming Fortune (Sprout/Farmhand/Haymaker Armor)
     * plus Farmer Boots' own {@value #FARMER_BOOTS_FORTUNE_PER_LEVEL}-per-Farming-level bonus -
     * wired into {@code GeneralSkillService#armorFarmingFortuneBonus} from {@code
     * FoodTooltipsPlugin}, the same late-bound callback shape {@code LapisArmorService
     * #equippedMiningFortuneBonus} already uses for Mining.
     */
    public int farmingFortuneBonus(Player p) {
        int total = 0;
        for (ItemStack piece : new ItemStack[]{p.getInventory().getHelmet(), p.getInventory().getChestplate(),
                p.getInventory().getLeggings(), p.getInventory().getBoots()}) {
            if (piece == null || piece.isEmpty()) {
                continue;
            }
            ItemMeta meta = piece.getItemMeta();
            if (meta == null) {
                continue;
            }
            var pdc = meta.getPersistentDataContainer();
            if (pdc.has(SPROUT_ARMOR_KEY, PersistentDataType.BYTE)) {
                total += SPROUT_FARMING_FORTUNE_PER_PIECE;
            }
            if (pdc.has(FARMHAND_ARMOR_KEY, PersistentDataType.BYTE)) {
                total += FARMHAND_FARMING_FORTUNE_PER_PIECE;
            }
            if (pdc.has(HAYMAKER_ARMOR_KEY, PersistentDataType.BYTE)) {
                total += HAYMAKER_FARMING_FORTUNE_PER_PIECE;
            }
            if (pdc.has(FARMER_BOOTS_KEY, PersistentDataType.BYTE)) {
                total += this.general.progress(p, SkillType.FARMING).level() * FARMER_BOOTS_FORTUNE_PER_LEVEL;
            }
            // Lantern Helmet's own per-level Farming Fortune only applies with an axe in the
            // main hand, per the player's own spec - unlike every other Farming Fortune
            // source above, which always applies regardless of what's held.
            if (pdc.has(LANTERN_HELMET_KEY, PersistentDataType.BYTE) && p.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) {
                total += this.general.progress(p, SkillType.FARMING).level() * LANTERN_HELMET_FORTUNE_PER_LEVEL;
            }
        }
        return total;
    }

    /**
     * Every level-scaling Defense source this class owns - Farmer Boots and the Lantern
     * Helmet alike (both {@code ArmorDefenseService#farmerBootsBonus} bonus, added outside
     * its own multiplier - see that field's own doc) - summed for whichever of the two
     * {@code e} is actually wearing right now. 0 for a non-{@link Player} or one wearing
     * neither.
     */
    public int farmerBootsDefenseBonus(LivingEntity e) {
        if (!(e instanceof Player p)) {
            return 0;
        }
        int total = 0;
        if (isFarmerBoots(p.getInventory().getBoots())) {
            total += FARMER_BOOTS_BASE_DEFENSE + FARMER_BOOTS_DEFENSE_PER_LEVEL * this.general.progress(p, SkillType.FARMING).level();
        }
        if (isLanternHelmet(p.getInventory().getHelmet())) {
            total += LANTERN_HELMET_BASE_DEFENSE + LANTERN_HELMET_DEFENSE_PER_LEVEL * this.general.progress(p, SkillType.FARMING).level();
        }
        return total;
    }

    private static boolean isFarmerBoots(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(FARMER_BOOTS_KEY, PersistentDataType.BYTE);
    }

    private static boolean isLanternHelmet(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(LANTERN_HELMET_KEY, PersistentDataType.BYTE);
    }

    /**
     * Refreshes the Lantern Helmet's own real {@link Attribute#MAX_HEALTH} modifier to match
     * {@code player}'s current Farming level - same "recompute every tick from current
     * equipment" pattern {@link #applyFarmerBootsSpeed} already uses for Farmer Boots' own
     * level-scaling Speed, needed here for the same reason (a level-scaling bonus can't just
     * be baked once at crafting time). Call from the same periodic per-player pass.
     */
    public void applyLanternHelmetHealth(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (!isLanternHelmet(helmet)) {
            return;
        }
        ItemMeta meta = helmet.getItemMeta();
        if (meta == null) {
            return;
        }
        int level = this.general.progress(player, SkillType.FARMING).level();
        double amount = LANTERN_HELMET_BASE_HEALTH + LANTERN_HELMET_HEALTH_PER_LEVEL * level;
        AttributeModifier existing = null;
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.MAX_HEALTH);
        if (modifiers != null) {
            for (AttributeModifier m : modifiers) {
                if (m.getKey().equals(LANTERN_HELMET_HEALTH_KEY)) {
                    existing = m;
                    break;
                }
            }
        }
        boolean needsUpdate = existing == null || Math.abs(existing.getAmount() - amount) > 1.0E-6;
        if (!needsUpdate) {
            return;
        }
        if (existing != null) {
            meta.removeAttributeModifier(Attribute.MAX_HEALTH, existing);
        }
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(LANTERN_HELMET_HEALTH_KEY, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
        helmet.setItemMeta(meta);
    }

    /**
     * Refreshes Farmer Boots' own real {@link Attribute#MOVEMENT_SPEED} modifier to match
     * {@code player}'s current Farming level - same "recompute every tick from current
     * equipment" pattern {@code ReforgeService#updateArmorSpeedModifier} already uses for
     * Attack Speed, needed here for the same reason: a level-scaling bonus can't just be
     * baked once at crafting time. Call from the same periodic per-player pass.
     */
    public void applyFarmerBootsSpeed(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        if (!isFarmerBoots(boots)) {
            return;
        }
        ItemMeta meta = boots.getItemMeta();
        if (meta == null) {
            return;
        }
        int level = this.general.progress(player, SkillType.FARMING).level();
        double amount = (FARMER_BOOTS_BASE_SPEED + FARMER_BOOTS_SPEED_PER_LEVEL * level) * SPEED_POINT_TO_ATTRIBUTE;
        AttributeModifier existing = null;
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.MOVEMENT_SPEED);
        if (modifiers != null) {
            for (AttributeModifier m : modifiers) {
                if (m.getKey().equals(FARMER_BOOTS_SPEED_KEY)) {
                    existing = m;
                    break;
                }
            }
        }
        boolean needsUpdate = existing == null || Math.abs(existing.getAmount() - amount) > 1.0E-6;
        if (!needsUpdate) {
            return;
        }
        if (existing != null) {
            meta.removeAttributeModifier(Attribute.MOVEMENT_SPEED, existing);
        }
        meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                new AttributeModifier(FARMER_BOOTS_SPEED_KEY, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
        boots.setItemMeta(meta);
    }

    /**
     * One piece of Sprout Armor: +{@value #SPROUT_DEFENSE_PER_PIECE} Defense (forced, same
     * per-item override {@code ArmorDefenseService#forceDefense} already gives Miner's/Lapis
     * Lazuli Armor) and +{@value #SPROUT_FARMING_FORTUNE_PER_PIECE} Farming Fortune per the
     * player's own spec, now wired into {@link #farmingFortuneBonus} above like every other
     * Farming Fortune source - nothing can obtain this piece at all until a recipe exists to
     * craft it (see this class's own doc), so the bonus stays unreachable in practice, but no
     * longer needs any further design work once one is added. Not called from anywhere yet.
     */
    public org.bukkit.inventory.ItemStack createSproutPiece(Material material, String name) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            Color color = material.name().endsWith("_CHESTPLATE") ? SPROUT_CHESTPLATE_COLOR
                    : material.name().endsWith("_LEGGINGS") ? SPROUT_LEGGINGS_COLOR
                    : material.name().endsWith("_BOOTS") ? SPROUT_BOOTS_COLOR
                    : SPROUT_HELMET_COLOR;
            leather.setColor(color);
        }
        ArmorDefenseService.forceDefense(meta, SPROUT_DEFENSE_PER_PIECE);
        ArmorDefenseService.markOwnDefenseLore(meta);
        this.tiers.forceTier(meta, ItemTier.B);
        meta.getPersistentDataContainer().set(SPROUT_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Defense: +" + SPROUT_DEFENSE_PER_PIECE, NamedTextColor.GREEN),
                Component.text("☘ Farming Fortune: +" + SPROUT_FARMING_FORTUNE_PER_PIECE, NamedTextColor.GOLD));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Appends {@code lines} to whatever lore {@code meta} already has - used to bake each
     * item's own real gameplay stats directly into its tooltip at creation time, since none of
     * these numbers (Farming Fortune especially - nothing else in this plugin ever puts it in
     * an item's own lore at all) would otherwise be visible anywhere before the item is
     * actually equipped, if ever ({@code ArmorDefenseService}'s own periodic Defense-tooltip
     * pass only touches items already sitting in a player's live inventory - never a bare
     * preview clone like {@code collections.CollectionsMenuService}'s own milestone tiles or
     * {@code collections.CollectionsItemsMenuService}'s {@code /rpgitems} tiles read via {@code
     * Bukkit#getRecipe}).
     */
    private static void addStatLore(ItemMeta meta, Component... lines) {
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        for (Component line : lines) {
            lore.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
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
