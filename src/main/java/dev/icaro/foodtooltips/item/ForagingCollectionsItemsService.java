package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.biome.BiomeWandService;
import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The craftable rewards Foraging's own {@link CollectionsCatalog} Oak Log entry unlocks -
 * same "one class per Collections domain" split as {@code LapisArmorService} (Mining) next
 * to {@code FarmingCollectionsItemsService} (Farming): every item here is Oak-Log-specific,
 * so it gets its own class instead of growing that already-large Farming one.
 *
 * <ul>
 *   <li>Leaflet Armor: dyed #4DCC4D leather chestplate/leggings/boots, but the helmet is
 *   the real {@link Material#OAK_LEAVES} block itself worn on the head (an {@code
 *   EQUIPPABLE} data component makes an otherwise non-armor block right-click-equippable -
 *   same component {@code combat.MinerVariantService} already uses to swap Miner's Armor's
 *   own model), not a custom head texture. Health only, no forced Defense (kept as plain
 *   Leather/Oak Leaves' own default) - see {@link #leafletPiece}/{@link #leafletHelmet}.
 *   <li>Enchanted Logs (one per wood, see {@link Wood}): the log, glinting - 5 slots of 32 logs,
 *   enforced by {@link #prepare}/{@link #craft}. Some woods also get Enchanted Planks.
 *   <li>Biome's Wand (restricted): the one crafting recipe for {@link BiomeWandService} in
 *   the whole plugin (the admin one is command-only, no recipe at all) - which biomes it
 *   can actually paint is checked live per player, not baked in here, see {@link
 *   BiomeWandService#createRestricted()}.
 * </ul>
 *
 * <p>Names/lore are hardcoded English, no {@code Language} parameter - same convention
 * {@code FarmingCollectionsItemsService}'s own Rabbit/Cactus/Speedster pieces already use
 * for every Collections-reward item (recipes are registered once at startup, long before
 * any specific player/language is known).
 */
public final class ForagingCollectionsItemsService implements Listener {
    private static final UUID EMBER_TALISMAN_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:ember_talisman".getBytes(StandardCharsets.UTF_8));
    private static final UUID EMBER_RING_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:ember_ring".getBytes(StandardCharsets.UTF_8));
    private static final UUID EMBER_ARTIFACT_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:ember_artifact".getBytes(StandardCharsets.UTF_8));
    private static final Color LEAFLET_ARMOR_COLOR = Color.fromRGB(0x4D, 0xCC, 0x4D);
    private static final int LEAFLET_HELMET_HEALTH = 70;
    private static final int LEAFLET_CHESTPLATE_HEALTH = 80;
    private static final int LEAFLET_LEGGINGS_HEALTH = 20;
    private static final int LEAFLET_BOOTS_HEALTH = 25;
    private static final org.bukkit.NamespacedKey LEAFLET_HEALTH_KEY = new org.bukkit.NamespacedKey("foodtooltips", "leaflet_armor_health");
    private static final Color GROWTH_ARMOR_COLOR = Color.fromRGB(0x00, 0xBE, 0x00);
    private static final int GROWTH_HELMET_HEALTH = 50;
    private static final int GROWTH_HELMET_DEFENSE = 30;
    private static final int GROWTH_CHESTPLATE_HEALTH = 100;
    private static final int GROWTH_CHESTPLATE_DEFENSE = 50;
    private static final int GROWTH_LEGGINGS_HEALTH = 80;
    private static final int GROWTH_LEGGINGS_DEFENSE = 40;
    private static final int GROWTH_BOOTS_HEALTH = 50;
    private static final int GROWTH_BOOTS_DEFENSE = 25;
    private static final org.bukkit.NamespacedKey GROWTH_HEALTH_KEY = new org.bukkit.NamespacedKey("foodtooltips", "growth_armor_piece_health");
    /** Per the player's own explicit spec: Talisman 12, Ring 20, Artifact 32 blocks of Creaking Sight range. */
    private static final int CREAKING_SIGHT_TALISMAN_RANGE = 12;
    private static final int CREAKING_SIGHT_RING_RANGE = 20;
    private static final int CREAKING_SIGHT_ARTIFACT_RANGE = 32;
    /** Same 1/3/5 scale {@link #CREAKING_SIGHT_TALISMAN_RANGE} and up use, here for the Crimson Stem line's own Heat Resistance stat (see {@code heat.HeatService}). */
    private static final int EMBER_TALISMAN_HEAT_RESISTANCE = 1;
    private static final int EMBER_RING_HEAT_RESISTANCE = 3;
    private static final int EMBER_ARTIFACT_HEAT_RESISTANCE = 5;
    /** Every Enchanted Log: {@value #ENCHANTED_LOG_SLOTS} slots of {@value #ENCHANTED_LOG_STACK} of its log each (160 total). */
    static final int ENCHANTED_LOG_SLOTS = 5;
    static final int ENCHANTED_LOG_STACK = 32;

    /**
     * One wood Collection's enchanted ingredients: the Enchanted Log (its M2 reward, replacing
     * the old "&lt;Wood&gt; Core" head) and, for the woods that had an upgraded Core, the Enchanted
     * Planks (8 Enchanted Logs around a Diamond Block). Each item's PDC marker uses the same
     * name as its recipe key ({@code enchanted_<material>}).
     */
    enum Wood {
        OAK(Material.OAK_LOG, Material.OAK_PLANKS, CollectionsCatalog.ENCHANTED_OAK_LOG_RECIPE, null),
        BIRCH(Material.BIRCH_LOG, Material.BIRCH_PLANKS, CollectionsCatalog.ENCHANTED_BIRCH_LOG_RECIPE, null),
        SPRUCE(Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, CollectionsCatalog.ENCHANTED_SPRUCE_LOG_RECIPE, null),
        DARK_OAK(Material.DARK_OAK_LOG, Material.DARK_OAK_PLANKS, CollectionsCatalog.ENCHANTED_DARK_OAK_LOG_RECIPE, null),
        ACACIA(Material.ACACIA_LOG, Material.ACACIA_PLANKS, CollectionsCatalog.ENCHANTED_ACACIA_LOG_RECIPE, null),
        JUNGLE(Material.JUNGLE_LOG, Material.JUNGLE_PLANKS, CollectionsCatalog.ENCHANTED_JUNGLE_LOG_RECIPE, null),
        MANGROVE(Material.MANGROVE_LOG, Material.MANGROVE_PLANKS, CollectionsCatalog.ENCHANTED_MANGROVE_LOG_RECIPE, CollectionsCatalog.ENCHANTED_MANGROVE_PLANKS_RECIPE),
        CHERRY(Material.CHERRY_LOG, Material.CHERRY_PLANKS, CollectionsCatalog.ENCHANTED_CHERRY_LOG_RECIPE, CollectionsCatalog.ENCHANTED_CHERRY_PLANKS_RECIPE),
        PALE_OAK(Material.PALE_OAK_LOG, Material.PALE_OAK_PLANKS, CollectionsCatalog.ENCHANTED_PALE_OAK_LOG_RECIPE, CollectionsCatalog.ENCHANTED_PALE_OAK_PLANKS_RECIPE),
        CRIMSON(Material.CRIMSON_STEM, Material.CRIMSON_PLANKS, CollectionsCatalog.ENCHANTED_CRIMSON_STEM_RECIPE, CollectionsCatalog.ENCHANTED_CRIMSON_PLANKS_RECIPE);

        final Material log;
        final Material planks;
        final org.bukkit.NamespacedKey logRecipe;
        /** Null for a wood with no Enchanted Planks. */
        final org.bukkit.NamespacedKey planksRecipe;

        Wood(Material log, Material planks, org.bukkit.NamespacedKey logRecipe, org.bukkit.NamespacedKey planksRecipe) {
            this.log = log;
            this.planks = planks;
            this.logRecipe = logRecipe;
            this.planksRecipe = planksRecipe;
        }

        static Wood ofLogRecipe(org.bukkit.NamespacedKey key) {
            for (Wood w : values()) {
                if (w.logRecipe.equals(key)) {
                    return w;
                }
            }
            return null;
        }
    }

    private final Plugin plugin;
    private final BiomeWandService biomeWand;
    private final SculptorsAxeService sculptorsAxe;
    private final SpruceAxeService spruceAxe;
    private final WoodcuttingCrystalService woodcuttingCrystal;
    private final SavannaBowService savannaBow;
    private final TreecapitatorService treecapitator;
    private final ItemTierService tiers;

    public ForagingCollectionsItemsService(Plugin plugin, BiomeWandService biomeWand, SculptorsAxeService sculptorsAxe,
            SpruceAxeService spruceAxe, WoodcuttingCrystalService woodcuttingCrystal, SavannaBowService savannaBow,
            TreecapitatorService treecapitator, ItemTierService tiers) {
        this.plugin = plugin;
        this.biomeWand = biomeWand;
        this.sculptorsAxe = sculptorsAxe;
        this.spruceAxe = spruceAxe;
        this.woodcuttingCrystal = woodcuttingCrystal;
        this.savannaBow = savannaBow;
        this.treecapitator = treecapitator;
        this.tiers = tiers;
    }

    /**
     * Eagerly applies {@code item}'s own tier badge/name-color (instead of waiting for {@code
     * ItemTierService}'s next periodic inventory sweep) - required for the Mangrove Sweep
     * Talisman → Ring → Artifact line specifically, since {@link #mangroveSweepRing}/{@link
     * #mangroveSweepArtifact} each use the tier below as a {@code RecipeChoice.ExactChoice}
     * ingredient: an un-tagged "pristine" reference item built once at startup would never
     * {@code ItemStack#isSimilar} match a real, already-swept copy sitting in a player's
     * inventory, silently breaking the recipe within seconds - the exact bug class fixed
     * across every other Talisman/Ring/Artifact line this session (see {@code
     * FarmingCollectionsItemsService#tagged}/{@code LapisExperienceService#tagged}). The
     * {@link Language} passed is unused by {@code ItemTierService#applyTier} itself.
     */
    private ItemStack tagged(ItemStack item) {
        this.tiers.applyTier(item, Language.PT);
        return item;
    }

    /** Registers every recipe this class owns - every wood's Enchanted Log/Planks, the 4 Leaflet Armor pieces, the restricted Biome's Wand, both Foraging axes, and the Woodcutting Crystal. */
    public void registerRecipes() {
        for (Wood wood : Wood.values()) {
            // 5 logs in any slots, but each stack needs ENCHANTED_LOG_STACK of them - a
            // vanilla recipe always takes one per slot, so #prepare/#craft enforce the amount.
            Bukkit.removeRecipe(wood.logRecipe);
            ShapelessRecipe enchantedLog = new ShapelessRecipe(wood.logRecipe, this.enchantedLog(wood));
            enchantedLog.addIngredient(ENCHANTED_LOG_SLOTS, wood.log);
            Bukkit.addRecipe(enchantedLog);
            if (wood.planksRecipe != null) {
                this.newShapedRecipe(wood.planksRecipe, this.enchantedPlanks(wood),
                        new String[]{"CCC", "CDC", "CCC"}, r -> {
                            r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(wood)));
                            r.setIngredient('D', Material.DIAMOND_BLOCK);
                        });
            }
        }
        this.newShapedRecipe(CollectionsCatalog.LEAFLET_HELMET_RECIPE, this.leafletHelmet(),
                new String[]{"XXX", "X X"}, r -> r.setIngredient('X', Material.OAK_LEAVES));
        this.newShapedRecipe(CollectionsCatalog.LEAFLET_CHESTPLATE_RECIPE, this.leafletPiece(Material.LEATHER_CHESTPLATE, "Leaflet Chestplate", LEAFLET_CHESTPLATE_HEALTH),
                new String[]{"X X", "XXX", "XXX"}, r -> r.setIngredient('X', Material.OAK_LEAVES));
        this.newShapedRecipe(CollectionsCatalog.LEAFLET_LEGGINGS_RECIPE, this.leafletPiece(Material.LEATHER_LEGGINGS, "Leaflet Leggings", LEAFLET_LEGGINGS_HEALTH),
                new String[]{"XXX", "X X", "X X"}, r -> r.setIngredient('X', Material.OAK_LEAVES));
        this.newShapedRecipe(CollectionsCatalog.LEAFLET_BOOTS_RECIPE, this.leafletPiece(Material.LEATHER_BOOTS, "Leaflet Boots", LEAFLET_BOOTS_HEALTH),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', Material.OAK_LEAVES));
        this.newShapedRecipe(CollectionsCatalog.BIOME_WAND_FOREST_PLAINS_RECIPE, this.biomeWand.createRestricted(),
                new String[]{"SGS", "GTG", "SGS"}, r -> {
                    r.setIngredient('S', Material.OAK_SAPLING);
                    r.setIngredient('G', Material.GRASS_BLOCK);
                    r.setIngredient('T', Material.STICK);
                });
        this.newShapedRecipe(CollectionsCatalog.SCULPTORS_AXE_RECIPE, this.sculptorsAxe.create(),
                new String[]{"LL", "LS", " S"}, r -> {
                    r.setIngredient('L', Material.BIRCH_LOG);
                    r.setIngredient('S', Material.STICK);
                });
        this.newShapedRecipe(CollectionsCatalog.SPRUCE_AXE_RECIPE, this.spruceAxe.create(),
                new String[]{"LL", "LS", " S"}, r -> {
                    r.setIngredient('L', Material.SPRUCE_LOG);
                    r.setIngredient('S', Material.STICK);
                });
        this.newShapedRecipe(CollectionsCatalog.WOODCUTTING_CRYSTAL_RECIPE, this.woodcuttingCrystal.createItem(),
                new String[]{"CCC", "CDC", "CCC"}, r -> {
                    r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.SPRUCE)));
                    r.setIngredient('D', Material.DIAMOND);
                });
        // Same "vanilla-empty center slot filled with the set's own power ingredient"
        // trick LapisArmorService uses for its own Diamond-piece center - here it's a
        // single Enchanted Dark Oak Log per piece instead.
        this.newShapedRecipe(CollectionsCatalog.GROWTH_HELMET_RECIPE,
                this.growthPiece(Material.LEATHER_HELMET, "Growth Helmet", GROWTH_HELMET_HEALTH, GROWTH_HELMET_DEFENSE),
                new String[]{"XXX", "XCX"}, r -> {
                    r.setIngredient('X', Material.LEATHER);
                    r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.DARK_OAK)));
                });
        this.newShapedRecipe(CollectionsCatalog.GROWTH_CHESTPLATE_RECIPE,
                this.growthPiece(Material.LEATHER_CHESTPLATE, "Growth Chestplate", GROWTH_CHESTPLATE_HEALTH, GROWTH_CHESTPLATE_DEFENSE),
                new String[]{"XCX", "XXX", "XXX"}, r -> {
                    r.setIngredient('X', Material.LEATHER);
                    r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.DARK_OAK)));
                });
        this.newShapedRecipe(CollectionsCatalog.GROWTH_LEGGINGS_RECIPE,
                this.growthPiece(Material.LEATHER_LEGGINGS, "Growth Leggings", GROWTH_LEGGINGS_HEALTH, GROWTH_LEGGINGS_DEFENSE),
                new String[]{"XXX", "XCX", "X X"}, r -> {
                    r.setIngredient('X', Material.LEATHER);
                    r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.DARK_OAK)));
                });
        this.newShapedRecipe(CollectionsCatalog.GROWTH_BOOTS_RECIPE,
                this.growthPiece(Material.LEATHER_BOOTS, "Growth Boots", GROWTH_BOOTS_HEALTH, GROWTH_BOOTS_DEFENSE),
                new String[]{"X X", "XCX"}, r -> {
                    r.setIngredient('X', Material.LEATHER);
                    r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.DARK_OAK)));
                });
        // Vanilla's own bow shape (3 Stick + 3 String, in the same diagonal zigzag) with
        // the Enchanted Acacia Log standing in for every Stick - same "power ingredient replaces
        // the plain one" idea as this class's own Growth Armor/Woodcutting Crystal
        // recipes above, just applied to a weapon instead of an armor piece/block.
        this.newShapedRecipe(CollectionsCatalog.SAVANNA_BOW_RECIPE, this.savannaBow.create(),
                new String[]{" CT", "C T", " CT"}, r -> {
                    r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.ACACIA)));
                    r.setIngredient('T', Material.STRING);
                });
        // Explicit request: a Spruce Axe in the center surrounded by 8 Enchanted Jungle Logs,
        // not the "vanilla-empty center slot" trick every other axe/armor recipe in
        // this class uses - Treecapitator's own power ingredient IS the previous axe.
        this.newShapedRecipe(CollectionsCatalog.TREECAPITATOR_RECIPE, this.treecapitator.create(),
                new String[]{"JJJ", "JSJ", "JJJ"}, r -> {
                    r.setIngredient('J', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.JUNGLE)));
                    r.setIngredient('S', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.spruceAxe.create()));
                });
        this.newShapedRecipe(CollectionsCatalog.MANGROVE_SWEEP_TALISMAN_RECIPE, this.mangroveSweepTalisman(),
                new String[]{"LLL", "LIL", "LLL"}, r -> {
                    r.setIngredient('L', Material.MANGROVE_LOG);
                    r.setIngredient('I', Material.IRON_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.MANGROVE_SWEEP_RING_RECIPE, this.mangroveSweepRing(),
                new String[]{"CCC", "CTC", "CCC"}, r -> {
                    r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.MANGROVE)));
                    r.setIngredient('T', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.mangroveSweepTalisman()));
                });
        this.newShapedRecipe(CollectionsCatalog.MANGROVE_SWEEP_ARTIFACT_RECIPE, this.mangroveSweepArtifact(),
                new String[]{"FFF", "FRF", "FFF"}, r -> {
                    r.setIngredient('F', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedPlanks(Wood.MANGROVE)));
                    r.setIngredient('R', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.mangroveSweepRing()));
                });
        this.newShapedRecipe(CollectionsCatalog.CHERRY_FORTUNE_TALISMAN_RECIPE, this.cherryFortuneTalisman(),
                new String[]{"LLL", "LIL", "LLL"}, r -> {
                    r.setIngredient('L', Material.CHERRY_LOG);
                    r.setIngredient('I', Material.IRON_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.CHERRY_FORTUNE_RING_RECIPE, this.cherryFortuneRing(),
                new String[]{"CCC", "CTC", "CCC"}, r -> {
                    r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.CHERRY)));
                    r.setIngredient('T', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.cherryFortuneTalisman()));
                });
        this.newShapedRecipe(CollectionsCatalog.CHERRY_FORTUNE_ARTIFACT_RECIPE, this.cherryFortuneArtifact(),
                new String[]{"PPP", "PRP", "PPP"}, r -> {
                    r.setIngredient('P', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedPlanks(Wood.CHERRY)));
                    r.setIngredient('R', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.cherryFortuneRing()));
                });
        this.newShapedRecipe(CollectionsCatalog.CREAKING_SIGHT_TALISMAN_RECIPE, this.creakingSightTalisman(),
                new String[]{"PPP", "PEP", "PPP"}, r -> {
                    r.setIngredient('P', Material.PALE_OAK_LOG);
                    r.setIngredient('E', Material.ENDER_PEARL);
                });
        this.newShapedRecipe(CollectionsCatalog.CREAKING_SIGHT_RING_RECIPE, this.creakingSightRing(),
                new String[]{"CCC", "CTC", "CCC"}, r -> {
                    r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.PALE_OAK)));
                    r.setIngredient('T', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.creakingSightTalisman()));
                });
        this.newShapedRecipe(CollectionsCatalog.CREAKING_SIGHT_ARTIFACT_RECIPE, this.creakingSightArtifact(),
                new String[]{"AAA", "ARA", "AAA"}, r -> {
                    r.setIngredient('A', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedPlanks(Wood.PALE_OAK)));
                    r.setIngredient('R', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.creakingSightRing()));
                });
        this.newShapedRecipe(CollectionsCatalog.EMBER_TALISMAN_RECIPE, this.emberTalisman(),
                new String[]{"CCC", "CIC", "CCC"}, r -> {
                    r.setIngredient('C', Material.CRIMSON_STEM);
                    r.setIngredient('I', Material.IRON_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.EMBER_RING_RECIPE, this.emberRing(),
                new String[]{"CCC", "CTC", "CCC"}, r -> {
                    r.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedLog(Wood.CRIMSON)));
                    r.setIngredient('T', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.emberTalisman()));
                });
        this.newShapedRecipe(CollectionsCatalog.EMBER_ARTIFACT_RECIPE, this.emberArtifact(),
                new String[]{"TTT", "TRT", "TTT"}, r -> {
                    r.setIngredient('T', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.enchantedPlanks(Wood.CRIMSON)));
                    r.setIngredient('R', new org.bukkit.inventory.RecipeChoice.ExactChoice(this.emberRing()));
                });
    }

    /**
     * Mangrove Log Collection M3's own accessory, the first of the Talisman → Ring → Artifact
     * line ({@link #mangroveSweepRing}/{@link #mangroveSweepArtifact} each upgrade the one
     * before, consuming it as an ingredient) - stored in the Accessory Bag, Tier D, +1 Sweep
     * (see {@link AccessoryItems#sweepBonus}/{@code skills.GeneralSkillService#sweep}).
     */
    private ItemStack mangroveSweepTalisman() {
        ItemStack item = new ItemStack(Material.MANGROVE_PROPAGULE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Mangrove Sweep Talisman", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.TALISMAN, "mangrove_sweep", 0, 0.0, 0.0, 0.0, 1, 0, 0, 0);
        this.tiers.forceTier(meta, ItemTier.D);
        addStatLore(meta,
                Component.text("Sweep: +1", NamedTextColor.DARK_GREEN),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Mangrove Sweep accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /** Mangrove Log Collection M5's own upgrade to {@link #mangroveSweepTalisman}, Tier C, +3 Sweep - same {@code "mangrove_sweep"} family tag as the rest of this line. */
    private ItemStack mangroveSweepRing() {
        ItemStack item = new ItemStack(Material.MANGROVE_PROPAGULE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Mangrove Sweep Ring", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.RING, "mangrove_sweep", 0, 0.0, 0.0, 0.0, 3, 0, 0, 0);
        this.tiers.forceTier(meta, ItemTier.C);
        addStatLore(meta,
                Component.text("Sweep: +3", NamedTextColor.DARK_GREEN),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Mangrove Sweep accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /** Mangrove Log Collection M8's own upgrade to {@link #mangroveSweepRing}, Tier B, +5 Sweep - the top of this Collection's accessory line. */
    private ItemStack mangroveSweepArtifact() {
        ItemStack item = new ItemStack(Material.MANGROVE_PROPAGULE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Mangrove Sweep Artifact", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.ARTIFACT, "mangrove_sweep", 0, 0.0, 0.0, 0.0, 5, 0, 0, 0);
        this.tiers.forceTier(meta, ItemTier.B);
        addStatLore(meta,
                Component.text("Sweep: +5", NamedTextColor.DARK_GREEN),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Mangrove Sweep accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /**
     * Cherry Log Collection M3's own accessory, the first of the Talisman → Ring → Artifact
     * line ({@link #cherryFortuneRing}/{@link #cherryFortuneArtifact} each upgrade the one
     * before, consuming it as an ingredient) - stored in the Accessory Bag, Tier D, +10
     * Foraging Fortune (see {@link AccessoryItems#foragingFortuneBonus}/{@code
     * skills.GeneralSkillService#accessoryFortuneBonus}). Same recipe/tier/family-tag shape
     * as the Mangrove Sweep line, per the player's own explicit spec.
     */
    private ItemStack cherryFortuneTalisman() {
        ItemStack item = new ItemStack(Material.CHERRY_SAPLING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Cherry Fortune Talisman", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.TALISMAN, "cherry_fortune", 0, 0.0, 0.0, 0.0, 0, 10, 0, 0);
        this.tiers.forceTier(meta, ItemTier.D);
        addStatLore(meta,
                Component.text("Foraging Fortune: +10", NamedTextColor.GOLD),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Cherry Fortune accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /** Cherry Log Collection M5's own upgrade to {@link #cherryFortuneTalisman}, Tier C, +25 Foraging Fortune - same {@code "cherry_fortune"} family tag as the rest of this line. */
    private ItemStack cherryFortuneRing() {
        ItemStack item = new ItemStack(Material.CHERRY_SAPLING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Cherry Fortune Ring", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.RING, "cherry_fortune", 0, 0.0, 0.0, 0.0, 0, 25, 0, 0);
        this.tiers.forceTier(meta, ItemTier.C);
        addStatLore(meta,
                Component.text("Foraging Fortune: +25", NamedTextColor.GOLD),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Cherry Fortune accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /** Cherry Log Collection M8's own upgrade to {@link #cherryFortuneRing}, Tier B, +50 Foraging Fortune - the top of this Collection's accessory line. */
    private ItemStack cherryFortuneArtifact() {
        ItemStack item = new ItemStack(Material.CHERRY_SAPLING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Cherry Fortune Artifact", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.ARTIFACT, "cherry_fortune", 0, 0.0, 0.0, 0.0, 0, 50, 0, 0);
        this.tiers.forceTier(meta, ItemTier.B);
        addStatLore(meta,
                Component.text("Foraging Fortune: +50", NamedTextColor.GOLD),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Cherry Fortune accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /** A wood Collection's M2 reward: its log, glinting - crafted from {@value #ENCHANTED_LOG_SLOTS} stacks of {@value #ENCHANTED_LOG_STACK} logs. */
    private ItemStack enchantedLog(Wood wood) {
        return enchantedIngredient(wood.log, wood.logRecipe, "Enchanted " + pretty(wood.log));
    }

    /** {@link #enchantedLog}'s upgrade: the wood's planks, glinting - 8 Enchanted Logs around a Diamond Block. */
    private ItemStack enchantedPlanks(Wood wood) {
        return enchantedIngredient(wood.planks, wood.planksRecipe, "Enchanted " + pretty(wood.planks));
    }

    /** "DARK_OAK_LOG" -> "Dark Oak Log". */
    private static String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String w : m.name().toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static ItemStack enchantedIngredient(Material material, org.bukkit.NamespacedKey key, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    /** No Enchanted Log/Planks is meant to be placed. */
    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (!item.hasItemMeta()) {
            return;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        for (Wood wood : Wood.values()) {
            if (pdc.has(wood.logRecipe, PersistentDataType.BYTE) || (wood.planksRecipe != null && pdc.has(wood.planksRecipe, PersistentDataType.BYTE))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** Hides an Enchanted Log result until every filled slot has {@value #ENCHANTED_LOG_STACK} logs. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent e) {
        if (isEnchantedLogRecipe(e.getRecipe()) && !hasFullStacks(e.getInventory().getMatrix())) {
            e.getInventory().setResult(null);
        }
    }

    /** Crafts an Enchanted Log by hand, taking {@value #ENCHANTED_LOG_STACK} logs from each filled slot instead of vanilla's one - same approach as {@code CobblestoneCollectionService#craft}. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        if (!isEnchantedLogRecipe(e.getRecipe()) || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        e.setCancelled(true);
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (!hasFullStacks(matrix) || inv.getResult() == null) {
            return;
        }
        ItemStack result = inv.getResult().clone();
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] == null || matrix[i].isEmpty()) {
                continue;
            }
            int left = matrix[i].getAmount() - ENCHANTED_LOG_STACK;
            matrix[i] = left > 0 ? matrix[i].asQuantity(left) : null;
        }
        inv.setMatrix(matrix);
        ItemStack cursor = e.getView().getCursor();
        if (!e.isShiftClick() && (cursor == null || cursor.isEmpty())) {
            e.getView().setCursor(result);
        } else if (!e.isShiftClick() && cursor.isSimilar(result) && cursor.getAmount() < cursor.getMaxStackSize()) {
            e.getView().setCursor(cursor.add(result.getAmount()));
        } else {
            for (ItemStack overflow : p.getInventory().addItem(result).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
    }

    private static boolean isEnchantedLogRecipe(Recipe recipe) {
        return recipe instanceof org.bukkit.Keyed keyed && Wood.ofLogRecipe(keyed.getKey()) != null;
    }

    /** Whether {@code matrix} holds exactly {@value #ENCHANTED_LOG_SLOTS} filled slots of at least {@value #ENCHANTED_LOG_STACK} items each (the recipe itself already checks which item). */
    static boolean hasFullStacks(ItemStack[] matrix) {
        int filled = 0;
        for (ItemStack stack : matrix) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (stack.getAmount() < ENCHANTED_LOG_STACK) {
                return false;
            }
            filled++;
        }
        return filled == ENCHANTED_LOG_SLOTS;
    }

    /**
     * Pale Oak Log Collection M3's own accessory, the first of the Talisman → Ring → Artifact
     * line ({@link #creakingSightRing}/{@link #creakingSightArtifact} each upgrade the one
     * before, consuming it as an ingredient) - stored in the Accessory Bag, Tier D, grants
     * {@value #CREAKING_SIGHT_TALISMAN_RANGE} blocks of Creaking Sight (see {@link
     * AccessoryItems#creakingSightRange}/{@code creaking.CreakingSightService}). {@link
     * Material#SPYGLASS} - a real vision/detection tool, matching "Sight" - represents the
     * whole line, same "no custom head, a themed vanilla item instead" shape {@link
     * #mangroveSweepTalisman}/{@link #cherryFortuneTalisman} already use.
     */
    private ItemStack creakingSightTalisman() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Creaking Sight Talisman", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.TALISMAN, "creaking_sight", 0, 0.0, 0.0, 0.0, 0, 0, CREAKING_SIGHT_TALISMAN_RANGE, 0);
        this.tiers.forceTier(meta, ItemTier.D);
        addStatLore(meta,
                Component.text("Creaking Sight: " + CREAKING_SIGHT_TALISMAN_RANGE + " blocks", NamedTextColor.LIGHT_PURPLE),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Creaking Sight accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /** Pale Oak Log Collection M5's own upgrade to {@link #creakingSightTalisman}, Tier C, {@value #CREAKING_SIGHT_RING_RANGE} blocks of Creaking Sight - same {@code "creaking_sight"} family tag as the rest of this line. */
    private ItemStack creakingSightRing() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Creaking Sight Ring", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.RING, "creaking_sight", 0, 0.0, 0.0, 0.0, 0, 0, CREAKING_SIGHT_RING_RANGE, 0);
        this.tiers.forceTier(meta, ItemTier.C);
        addStatLore(meta,
                Component.text("Creaking Sight: " + CREAKING_SIGHT_RING_RANGE + " blocks", NamedTextColor.LIGHT_PURPLE),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Creaking Sight accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /** Pale Oak Log Collection M8's own upgrade to {@link #creakingSightRing}, Tier B, {@value #CREAKING_SIGHT_ARTIFACT_RANGE} blocks of Creaking Sight - the top of this Collection's accessory line. */
    private ItemStack creakingSightArtifact() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Creaking Sight Artifact", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.ARTIFACT, "creaking_sight", 0, 0.0, 0.0, 0.0, 0, 0, CREAKING_SIGHT_ARTIFACT_RANGE, 0);
        this.tiers.forceTier(meta, ItemTier.B);
        addStatLore(meta,
                Component.text("Creaking Sight: " + CREAKING_SIGHT_ARTIFACT_RANGE + " blocks", NamedTextColor.LIGHT_PURPLE),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Creaking Sight accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /**
     * Crimson Stem Collection M3's own accessory, the first of the Talisman → Ring → Artifact
     * line ({@link #emberRing}/{@link #emberArtifact} each upgrade the one before, consuming
     * it as an ingredient) - stored in the Accessory Bag, Tier D, grants {@value
     * #EMBER_TALISMAN_HEAT_RESISTANCE} Heat Resistance (see {@link
     * AccessoryItems#heatResistance}/{@code heat.HeatService}), which slows down how often
     * the player accumulates Heat while in the Nether.
     */
    private ItemStack emberTalisman() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.EMBER_TALISMAN, EMBER_TALISMAN_PROFILE);
        meta.displayName(Component.text("Ember Talisman", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.TALISMAN, "ember", 0, 0.0, 0.0, 0.0, 0, 0, 0, EMBER_TALISMAN_HEAT_RESISTANCE);
        this.tiers.forceTier(meta, ItemTier.D);
        addStatLore(meta,
                Component.text("Heat Resistance: +" + EMBER_TALISMAN_HEAT_RESISTANCE, NamedTextColor.RED),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Ember accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /** Crimson Stem Collection M5's own upgrade to {@link #emberTalisman}, Tier C, +{@value #EMBER_RING_HEAT_RESISTANCE} Heat Resistance - same {@code "ember"} family tag as the rest of this line. */
    private ItemStack emberRing() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.EMBER_RING, EMBER_RING_PROFILE);
        meta.displayName(Component.text("Ember Ring", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.RING, "ember", 0, 0.0, 0.0, 0.0, 0, 0, 0, EMBER_RING_HEAT_RESISTANCE);
        this.tiers.forceTier(meta, ItemTier.C);
        addStatLore(meta,
                Component.text("Heat Resistance: +" + EMBER_RING_HEAT_RESISTANCE, NamedTextColor.RED),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Ember accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /** Crimson Stem Collection M8's own upgrade to {@link #emberRing}, Tier B, +{@value #EMBER_ARTIFACT_HEAT_RESISTANCE} Heat Resistance - the top of this Collection's accessory line. */
    private ItemStack emberArtifact() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.EMBER_ARTIFACT, EMBER_ARTIFACT_PROFILE);
        meta.displayName(Component.text("Ember Artifact", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.ARTIFACT, "ember", 0, 0.0, 0.0, 0.0, 0, 0, 0, EMBER_ARTIFACT_HEAT_RESISTANCE);
        this.tiers.forceTier(meta, ItemTier.B);
        addStatLore(meta,
                Component.text("Heat Resistance: +" + EMBER_ARTIFACT_HEAT_RESISTANCE, NamedTextColor.RED),
                Component.empty(),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY),
                Component.text("Only one Ember accessory at a time.", NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return this.tagged(item);
    }

    /**
     * One piece of Armor of Growth: dyed #00BE00 leather, {@code health}/{@code defense}
     * forced (flat, same "real {@link Attribute#MAX_HEALTH} modifier plus {@code
     * ArmorDefenseService#forceDefense}" pattern {@code FarmingCollectionsItemsService
     * #cactusPiece} already uses) plus the full-set bonus ({@link GrowthArmorService}: +1
     * permanent Max Health per mob killed while the full set is worn, capped at +{@value
     * GrowthArmorService#MAX_BONUS_HEALTH}, and increased Regeneration) mentioned in every
     * piece's own lore regardless of which one the player is looking at.
     */
    private ItemStack growthPiece(Material material, String name, int health, int defense) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(GROWTH_ARMOR_COLOR);
        }
        EquipmentSlotGroup slot = material.name().endsWith("_HELMET") ? EquipmentSlotGroup.HEAD
                : material.name().endsWith("_CHESTPLATE") ? EquipmentSlotGroup.CHEST
                : material.name().endsWith("_LEGGINGS") ? EquipmentSlotGroup.LEGS
                : EquipmentSlotGroup.FEET;
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(GROWTH_HEALTH_KEY, health, AttributeModifier.Operation.ADD_NUMBER, slot));
        ArmorDefenseService.forceDefense(meta, defense);
        ArmorDefenseService.markOwnDefenseLore(meta);
        GrowthArmorService.markGrowthPiece(meta);
        meta.displayName(Component.text(name, NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Health: +" + health, NamedTextColor.RED),
                Component.text("Defense: +" + defense, NamedTextColor.GREEN),
                Component.empty(),
                Component.text("Full Set Bonus:", NamedTextColor.GRAY),
                Component.text("Each mob killed grants +1 Max Health (up to +" + GrowthArmorService.MAX_BONUS_HEALTH + ")", NamedTextColor.LIGHT_PURPLE),
                Component.text("Increased Regeneration", NamedTextColor.LIGHT_PURPLE));
        item.setItemMeta(meta);
        return item;
    }

    /** One piece of Leaflet Armor: dyed-green leather, {@code health} forced (a real {@link Attribute#MAX_HEALTH} modifier) plus +3 Foraging Fortune ({@link LeafletArmorService}) - no forced Defense, kept as plain Leather's own default. */
    private ItemStack leafletPiece(Material material, String name, int health) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(LEAFLET_ARMOR_COLOR);
        }
        EquipmentSlotGroup slot = material.name().endsWith("_CHESTPLATE") ? EquipmentSlotGroup.CHEST
                : material.name().endsWith("_LEGGINGS") ? EquipmentSlotGroup.LEGS
                : EquipmentSlotGroup.FEET;
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(LEAFLET_HEALTH_KEY, health, AttributeModifier.Operation.ADD_NUMBER, slot));
        LeafletArmorService.markLeafletPiece(meta);
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Health: +" + health, NamedTextColor.RED),
                Component.text("Foraging Fortune: +3", NamedTextColor.GOLD));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Leaflet Armor's own helmet: the real {@link Material#OAK_LEAVES} block, not a leather
     * piece or a custom head - {@code DataComponentTypes.EQUIPPABLE} is what makes an
     * otherwise non-armor block right-click-equippable in the head slot at all;
     * {@code equipOnInteract} is what actually lets a right-click put it on (without it, it
     * would only ever reach the slot via a shift-click/drag), {@code swappable} lets a
     * right-click take it back off again like a normal helmet.
     */
    private ItemStack leafletHelmet() {
        ItemStack item = new ItemStack(Material.OAK_LEAVES);
        ItemMeta meta = item.getItemMeta();
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(LEAFLET_HEALTH_KEY, LEAFLET_HELMET_HEALTH, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
        LeafletArmorService.markLeafletPiece(meta);
        meta.displayName(Component.text("Leaflet Helmet", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        addStatLore(meta,
                Component.text("Health: +" + LEAFLET_HELMET_HEALTH, NamedTextColor.RED),
                Component.text("Foraging Fortune: +3", NamedTextColor.GOLD));
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
                .equipOnInteract(true)
                .swappable(true));
        return item;
    }

    /** Appends {@code lines} to whatever lore {@code meta} already has - same helper {@code FarmingCollectionsItemsService} keeps for its own item builders. */
    private static void addStatLore(ItemMeta meta, Component... lines) {
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        for (Component line : lines) {
            lore.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
    }

    private void newShapedRecipe(org.bukkit.NamespacedKey key, ItemStack result, String[] shape, java.util.function.Consumer<ShapedRecipe> ingredients) {
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
