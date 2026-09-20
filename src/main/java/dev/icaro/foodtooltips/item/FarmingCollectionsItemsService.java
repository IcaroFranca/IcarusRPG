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
    private static final UUID MELON_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:melon_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID POTATO_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:potato_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID PUMPKIN_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:pumpkin_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID WHEAT_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:wheat_core".getBytes(StandardCharsets.UTF_8));
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
    /** See {@code MushroomSoupFlightService} - the actual flight-granting logic lives there, this class only builds the item and its recipe. */
    public static final NamespacedKey MAGICAL_MUSHROOM_SOUP_KEY = new NamespacedKey("foodtooltips", "magical_mushroom_soup");
    public static final NamespacedKey MYSTICAL_MUSHROOM_SOUP_KEY = new NamespacedKey("foodtooltips", "mystical_mushroom_soup");
    /** 2 minutes, per the player's own spec. */
    public static final int MAGICAL_MUSHROOM_SOUP_FLIGHT_TICKS = 2 * 60 * 20;
    /** 200 minutes, per the player's own spec. */
    public static final int MYSTICAL_MUSHROOM_SOUP_FLIGHT_TICKS = 200 * 60 * 20;

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
        this.newShapedRecipe(CollectionsCatalog.CARROT_CORE_RECIPE, this.carrotCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.CARROT);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
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
     * "aumenta absorção e velocidade" - no ingredient or exact numbers were specified, so
     * this follows the same judgment-call pattern {@link #registerResistancePotionMix} already
     * set: an Awkward Potion base, Sugar as the "central material" (vanilla's own real Speed
     * Potion ingredient, matching this potion's own Speed half), Absorption II + Speed II for
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

        NamespacedKey key = new NamespacedKey(this.plugin, "adrenaline_from_sugar");
        Bukkit.getPotionBrewer().removePotionMix(key);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, adrenaline,
                new RecipeChoice.ExactChoice(awkward), new RecipeChoice.MaterialChoice(Material.SUGAR)));
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
    private org.bukkit.inventory.ItemStack mushroomPiece(Material material, String name, int health, int defense) {
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

    /** One piece of Farmhand Armor: +{@value #FARMHAND_DEFENSE_PER_PIECE} Defense/+{@value #FARMHAND_FARMING_FORTUNE_PER_PIECE} Farming Fortune, flat (see {@link #farmingFortuneBonus}) - {@code color} is {@code null} for the one piece left undyed ("cor padrão" - the boots). */
    private org.bukkit.inventory.ItemStack farmhandPiece(Material material, String name, Color color) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather && color != null) {
            leather.setColor(color);
        }
        ArmorDefenseService.forceDefense(meta, FARMHAND_DEFENSE_PER_PIECE);
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
        }
        return total;
    }

    /** Farmer Boots' own level-scaling Defense bonus - wired into {@code ArmorDefenseService#farmerBootsBonus}, added outside its own multiplier (see that field's own doc). 0 for anything but a player actually wearing Farmer Boots. */
    public int farmerBootsDefenseBonus(LivingEntity e) {
        if (!(e instanceof Player p) || !isFarmerBoots(p.getInventory().getBoots())) {
            return 0;
        }
        return FARMER_BOOTS_BASE_DEFENSE + FARMER_BOOTS_DEFENSE_PER_LEVEL * this.general.progress(p, SkillType.FARMING).level();
    }

    private static boolean isFarmerBoots(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(FARMER_BOOTS_KEY, PersistentDataType.BYTE);
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
            leather.setColor(Color.fromRGB(0x4C, 0xAF, 0x50));
        }
        ArmorDefenseService.forceDefense(meta, SPROUT_DEFENSE_PER_PIECE);
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
