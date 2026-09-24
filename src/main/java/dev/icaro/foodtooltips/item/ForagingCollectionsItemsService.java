package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.biome.BiomeWandService;
import dev.icaro.foodtooltips.collections.CollectionsCatalog;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
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
 *   <li>Oak Core: 8 Oak Log around a Diamond Block, a custom head (minecraft-heads.com
 *   Custom Head ID 89446) - same shape every other Core item in {@code
 *   FarmingCollectionsItemsService} uses.
 *   <li>Biome's Wand (Forest/Plains): the one crafting recipe for {@link BiomeWandService}
 *   in the whole plugin (the admin one is command-only, no recipe at all) - restricted to
 *   only Forest/Plains via {@link BiomeWandService#createForestPlains()}.
 * </ul>
 *
 * <p>Names/lore are hardcoded English, no {@code Language} parameter - same convention
 * {@code FarmingCollectionsItemsService}'s own Rabbit/Cactus/Speedster pieces already use
 * for every Collections-reward item (recipes are registered once at startup, long before
 * any specific player/language is known).
 */
public final class ForagingCollectionsItemsService {
    private static final UUID OAK_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:oak_core".getBytes(StandardCharsets.UTF_8));
    private static final Color LEAFLET_ARMOR_COLOR = Color.fromRGB(0x4D, 0xCC, 0x4D);
    private static final int LEAFLET_HELMET_HEALTH = 70;
    private static final int LEAFLET_CHESTPLATE_HEALTH = 80;
    private static final int LEAFLET_LEGGINGS_HEALTH = 20;
    private static final int LEAFLET_BOOTS_HEALTH = 25;
    private static final org.bukkit.NamespacedKey LEAFLET_HEALTH_KEY = new org.bukkit.NamespacedKey("foodtooltips", "leaflet_armor_health");

    private final Plugin plugin;
    private final BiomeWandService biomeWand;

    public ForagingCollectionsItemsService(Plugin plugin, BiomeWandService biomeWand) {
        this.plugin = plugin;
        this.biomeWand = biomeWand;
    }

    /** Registers every recipe this class owns - Oak Core, the 4 Leaflet Armor pieces, and the restricted Biome's Wand. */
    public void registerRecipes() {
        this.newShapedRecipe(CollectionsCatalog.OAK_CORE_RECIPE, this.oakCore(),
                new String[]{"XXX", "XDX", "XXX"}, r -> {
                    r.setIngredient('X', Material.OAK_LOG);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.LEAFLET_HELMET_RECIPE, this.leafletHelmet(),
                new String[]{"XXX", "X X"}, r -> r.setIngredient('X', Material.OAK_LEAVES));
        this.newShapedRecipe(CollectionsCatalog.LEAFLET_CHESTPLATE_RECIPE, this.leafletPiece(Material.LEATHER_CHESTPLATE, "Leaflet Chestplate", LEAFLET_CHESTPLATE_HEALTH),
                new String[]{"X X", "XXX", "XXX"}, r -> r.setIngredient('X', Material.OAK_LEAVES));
        this.newShapedRecipe(CollectionsCatalog.LEAFLET_LEGGINGS_RECIPE, this.leafletPiece(Material.LEATHER_LEGGINGS, "Leaflet Leggings", LEAFLET_LEGGINGS_HEALTH),
                new String[]{"XXX", "X X", "X X"}, r -> r.setIngredient('X', Material.OAK_LEAVES));
        this.newShapedRecipe(CollectionsCatalog.LEAFLET_BOOTS_RECIPE, this.leafletPiece(Material.LEATHER_BOOTS, "Leaflet Boots", LEAFLET_BOOTS_HEALTH),
                new String[]{"X X", "X X"}, r -> r.setIngredient('X', Material.OAK_LEAVES));
        this.newShapedRecipe(CollectionsCatalog.BIOME_WAND_FOREST_PLAINS_RECIPE, this.biomeWand.createForestPlains(),
                new String[]{"SGS", "GTG", "SGS"}, r -> {
                    r.setIngredient('S', Material.OAK_SAPLING);
                    r.setIngredient('G', Material.GRASS_BLOCK);
                    r.setIngredient('T', Material.STICK);
                });
    }

    /** One piece of Leaflet Armor: dyed-green leather, {@code health} forced (a real {@link Attribute#MAX_HEALTH} modifier) plus +3 Foraging Fortune ({@link LeafletArmorService}) - no forced Defense, kept as plain Leather's own default. */
    private ItemStack leafletPiece(Material material, String name, int health) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
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

    private ItemStack oakCore() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.OAK_CORE, OAK_CORE_PROFILE);
        meta.displayName(Component.text("Oak Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
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
