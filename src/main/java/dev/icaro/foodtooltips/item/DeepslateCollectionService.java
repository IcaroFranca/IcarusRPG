package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.AccessoryBagService;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Everything the Cobbled Deepslate Collection (Mining) unlocks - the Inferno line:
 * <ul>
 * <li><b>Enchanted Cobbled Deepslate</b> (M2, 8 Cobbled Deepslate around a Diamond Block) and
 * <b>Enchanted Deepslate Core</b> (M5, 8 Enchanted Cobbled Deepslate around a Magma Block) -
 * crafting ingredients, never placeable.
 * <li><b>Inferno Ring</b> (M4, the lava-fished Inferno Talisman inside 8 Enchanted Cobbled
 * Deepslate; Tier C, +{@value #RING_HEAT_RESISTANCE} Heat Resistance) and <b>Inferno Artifact</b>
 * (M7, the Ring inside 8 Enchanted Deepslate Cores; Tier B, +{@value #ARTIFACT_HEAT_RESISTANCE}
 * Heat Resistance, and Heat's own Mining Speed/Fortune bonus is +{@value
 * #ARTIFACT_HEAT_BONUS_PERCENT}%) - the Talisman's upgrade line, the same "inferno_talisman" family,
 * so only the best one in the Accessory Bag counts.
 * <li><b>Inferno Armor</b> (M9, the four armor shapes in Enchanted Deepslate Core; Tier A) - the
 * Hardened Diamond Armor's own Defense, plus +{@value #ARMOR_HEAT_RESISTANCE} Heat Resistance per
 * piece worn ({@link #armorHeatResistance}). Leather dyed #FA003F, except the helmet: a Leather
 * Helmet that looks like a Red Stained Glass block - its item model is the glass, and its
 * equippable component has no armor asset, so the client draws the item itself on the head
 * (the way a Carved Pumpkin sits there) instead of a leather cap.
 * </ul>
 */
public final class DeepslateCollectionService implements Listener {
    static final int RING_HEAT_RESISTANCE = 2;
    static final int ARTIFACT_HEAT_RESISTANCE = 3;
    static final int ARTIFACT_HEAT_BONUS_PERCENT = 50;
    static final int ARMOR_HEAT_RESISTANCE = 5;
    static final int HELMET_DEFENSE = 60;
    static final int CHESTPLATE_DEFENSE = 120;
    static final int LEGGINGS_DEFENSE = 95;
    static final int BOOTS_DEFENSE = 55;
    private static final Color ARMOR_COLOR = Color.fromRGB(0xFA003F);
    private static final TextColor NAME_COLOR = TextColor.color(0xFA003F);
    private static final Key GLASS_MODEL = Key.key("minecraft", "red_stained_glass");

    private static final NamespacedKey ENCHANTED_DEEPSLATE_KEY = new NamespacedKey("foodtooltips", "enchanted_cobbled_deepslate");
    private static final NamespacedKey DEEPSLATE_CORE_KEY = new NamespacedKey("foodtooltips", "enchanted_deepslate_core");
    private static final NamespacedKey ARTIFACT_KEY = new NamespacedKey("foodtooltips", "inferno_artifact");
    /** Heat Resistance an Inferno Armor piece gives while worn. */
    private static final NamespacedKey ARMOR_HEAT_KEY = new NamespacedKey("foodtooltips", "armor_heat_resistance");

    private final ItemTierService tiers;
    private final AccessoryBagService accessoryBag;
    private final Supplier<ItemStack> infernoTalisman;

    public DeepslateCollectionService(ItemTierService tiers, AccessoryBagService accessoryBag, Supplier<ItemStack> infernoTalisman) {
        this.tiers = tiers;
        this.accessoryBag = accessoryBag;
        this.infernoTalisman = infernoTalisman;
    }

    // ---- Recipes ----

    public void registerRecipes() {
        RecipeChoice enchanted = new RecipeChoice.ExactChoice(this.enchantedCobbledDeepslate());
        RecipeChoice core = new RecipeChoice.ExactChoice(this.enchantedDeepslateCore());
        this.ring(CollectionsCatalog.ENCHANTED_COBBLED_DEEPSLATE_RECIPE, this.enchantedCobbledDeepslate(),
                new RecipeChoice.MaterialChoice(Material.COBBLED_DEEPSLATE), new RecipeChoice.MaterialChoice(Material.DIAMOND_BLOCK));
        this.ring(CollectionsCatalog.INFERNO_RING_RECIPE, this.infernoRing(), enchanted, new RecipeChoice.ExactChoice(this.infernoTalisman.get()));
        this.ring(CollectionsCatalog.ENCHANTED_DEEPSLATE_CORE_RECIPE, this.enchantedDeepslateCore(), enchanted, new RecipeChoice.MaterialChoice(Material.MAGMA_BLOCK));
        this.ring(CollectionsCatalog.INFERNO_ARTIFACT_RECIPE, this.infernoArtifact(), core, new RecipeChoice.ExactChoice(this.infernoRing()));
        this.shaped(CollectionsCatalog.INFERNO_HELMET_RECIPE, this.infernoHelmet(), new String[]{"MMM", "M M"}, r -> r.setIngredient('M', core));
        this.shaped(CollectionsCatalog.INFERNO_CHESTPLATE_RECIPE, this.infernoChestplate(), new String[]{"M M", "MMM", "MMM"}, r -> r.setIngredient('M', core));
        this.shaped(CollectionsCatalog.INFERNO_LEGGINGS_RECIPE, this.infernoLeggings(), new String[]{"MMM", "M M", "M M"}, r -> r.setIngredient('M', core));
        this.shaped(CollectionsCatalog.INFERNO_BOOTS_RECIPE, this.infernoBoots(), new String[]{"M M", "M M"}, r -> r.setIngredient('M', core));
    }

    /** "8 of {@code ring} around 1 {@code center}". */
    private void ring(NamespacedKey key, ItemStack result, RecipeChoice ring, RecipeChoice center) {
        this.shaped(key, result, new String[]{"RRR", "RXR", "RRR"}, r -> {
            r.setIngredient('R', ring);
            r.setIngredient('X', center);
        });
    }

    private void shaped(NamespacedKey key, ItemStack result, String[] shape, Consumer<ShapedRecipe> ingredients) {
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);
        ingredients.accept(recipe);
        Bukkit.addRecipe(recipe);
    }

    // ---- Ingredients ----

    public ItemStack enchantedCobbledDeepslate() {
        return this.ingredient(Material.COBBLED_DEEPSLATE, ENCHANTED_DEEPSLATE_KEY, "Enchanted Cobbled Deepslate");
    }

    public ItemStack enchantedDeepslateCore() {
        return this.ingredient(Material.DEEPSLATE_TILES, DEEPSLATE_CORE_KEY, "Enchanted Deepslate Core");
    }

    private ItemStack ingredient(Material material, NamespacedKey key, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Neither ingredient is meant to be placed. */
    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (item.hasItemMeta()) {
            var pdc = item.getItemMeta().getPersistentDataContainer();
            if (pdc.has(ENCHANTED_DEEPSLATE_KEY, PersistentDataType.BYTE) || pdc.has(DEEPSLATE_CORE_KEY, PersistentDataType.BYTE)) {
                e.setCancelled(true);
            }
        }
    }

    // ---- Accessories ----

    public ItemStack infernoRing() {
        return this.accessory(HeadTexture.INFERNO_TALISMAN, "inferno_ring", "Inferno Ring", AccessoryType.RING, ItemTier.C, RING_HEAT_RESISTANCE, false);
    }

    public ItemStack infernoArtifact() {
        return this.accessory(HeadTexture.INFERNO_TALISMAN, "inferno_artifact", "Inferno Artifact", AccessoryType.ARTIFACT, ItemTier.B, ARTIFACT_HEAT_RESISTANCE, true);
    }

    private ItemStack accessory(String texture, String id, String name, AccessoryType type, ItemTier tier, int heatResistance, boolean artifact) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("icarusrpg:" + id).getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, type, LavaFishingService.INFERNO_FAMILY, 0, 0.0, 0.0, 0.0, 0, 0, 0, heatResistance);
        if (artifact) {
            meta.getPersistentDataContainer().set(ARTIFACT_KEY, PersistentDataType.BYTE, (byte) 1);
        }
        this.tiers.forceTier(meta, tier);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Heat Resistance: +" + heatResistance, NamedTextColor.RED));
        if (artifact) {
            lore.add(line("Heat's Mining Speed and Fortune: +" + ARTIFACT_HEAT_BONUS_PERCENT + "%", NamedTextColor.GOLD));
        }
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        lore.add(line("Store in the Accessory Bag.", NamedTextColor.GRAY));
        lore.add(line("Only one Inferno accessory at a time.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    /** +{@value #ARTIFACT_HEAT_BONUS_PERCENT} while {@code p} has the Inferno Artifact in the Accessory Bag, else 0 - read by {@code heat.HeatService}. */
    public double heatMiningBonusPercent(Player p) {
        for (ItemStack item : this.accessoryBag.equippedAccessories(p)) {
            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(ARTIFACT_KEY, PersistentDataType.BYTE)) {
                return ARTIFACT_HEAT_BONUS_PERCENT;
            }
        }
        return 0.0;
    }

    // ---- Armor ----

    public ItemStack infernoHelmet() {
        return this.armor(Material.LEATHER_HELMET, "Inferno Helmet", HELMET_DEFENSE);
    }

    public ItemStack infernoChestplate() {
        return this.armor(Material.LEATHER_CHESTPLATE, "Inferno Chestplate", CHESTPLATE_DEFENSE);
    }

    public ItemStack infernoLeggings() {
        return this.armor(Material.LEATHER_LEGGINGS, "Inferno Leggings", LEGGINGS_DEFENSE);
    }

    public ItemStack infernoBoots() {
        return this.armor(Material.LEATHER_BOOTS, "Inferno Boots", BOOTS_DEFENSE);
    }

    private ItemStack armor(Material material, String name, int defense) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(ARMOR_COLOR);
        }
        ArmorDefenseService.forceDefense(meta, defense);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.getPersistentDataContainer().set(ARMOR_HEAT_KEY, PersistentDataType.INTEGER, ARMOR_HEAT_RESISTANCE);
        this.tiers.forceTier(meta, ItemTier.A);
        meta.displayName(Component.text(name, NAME_COLOR).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Defense: +" + defense, NamedTextColor.GREEN),
                line("Heat Resistance: +" + ARMOR_HEAT_RESISTANCE, NamedTextColor.RED)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_DYE);
        item.setItemMeta(meta);
        if (material == Material.LEATHER_HELMET) {
            // The glass block on the head: its model, and no armor asset so the item itself is drawn there.
            item.setData(DataComponentTypes.ITEM_MODEL, GLASS_MODEL);
            item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(org.bukkit.inventory.EquipmentSlot.HEAD)
                    .equipSound(net.kyori.adventure.key.Key.key("block.glass.place")).build());
        }
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    /** Heat Resistance from the Inferno Armor pieces {@code p} is wearing - read by {@code heat.HeatService}. */
    public static int armorHeatResistance(Player p) {
        int total = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece != null && !piece.isEmpty() && piece.hasItemMeta()) {
                total += piece.getItemMeta().getPersistentDataContainer().getOrDefault(ARMOR_HEAT_KEY, PersistentDataType.INTEGER, 0);
            }
        }
        return total;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
