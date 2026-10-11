package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The craftable rewards Mining's own {@link CollectionsCatalog} entries unlock - same role
 * {@code CombatCollectionsItemsService}/{@code FarmingCollectionsItemsService} play for their
 * own categories. Today only Redstone: the Redstone Core (M2), the Weather Wand (M4) built from
 * it, and the Redstone Block Core (M8). Both Cores also brew longer potions - see {@code
 * potion.RedstoneDurationService}. The Weather Wand has no ability yet - just the item and
 * its recipe, same "collection first, behavior later" step the Teleport Pad took - so it
 * carries {@link #WEATHER_WAND_KEY} for whatever service eventually gives it one.
 *
 * <p>Must be registered BEFORE {@code CombatCollectionsItemsService}: the Weather Wand's own
 * "core/core/stick" grid is the exact shape of the Zombie Sword's plain-{@code PLAYER_HEAD}
 * recipe, which Paper would otherwise match first (see {@code
 * CombatCollectionsItemsService#registerZombiesHeartRecipes}'s own doc).
 */
public final class MiningCollectionsItemsService {
    public static final NamespacedKey WEATHER_WAND_KEY = new NamespacedKey("foodtooltips", "weather_wand");
    private static final UUID REDSTONE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:redstone_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID REDSTONE_BLOCK_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:redstone_block_core".getBytes(StandardCharsets.UTF_8));

    /** Registers every recipe this class owns - Redstone Collection M2, M4 and M8. */
    public void registerRecipes() {
        this.newShapedRecipe(CollectionsCatalog.REDSTONE_CORE_RECIPE, this.redstoneCore(),
                new String[]{"RRR", "RDR", "RRR"}, r -> {
                    r.setIngredient('R', Material.REDSTONE);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.REDSTONE_BLOCK_CORE_RECIPE, this.redstoneBlockCore(),
                new String[]{"CCC", "CNC", "CCC"}, r -> {
                    r.setIngredient('C', new RecipeChoice.ExactChoice(this.redstoneCore()));
                    r.setIngredient('N', Material.NETHERITE_INGOT);
                });
        // Real vanilla Sword shape with both blade slots swapped for a Redstone Core, per the
        // player's own "padrão da espada, 1 stick e 2 redstone cores".
        this.newShapedRecipe(CollectionsCatalog.WEATHER_WAND_RECIPE, this.weatherWand(),
                new String[]{"C", "C", "S"}, r -> {
                    r.setIngredient('C', new RecipeChoice.ExactChoice(this.redstoneCore()));
                    r.setIngredient('S', Material.STICK);
                });
    }

    public ItemStack redstoneCore() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.REDSTONE_CORE, REDSTONE_CORE_PROFILE);
        meta.displayName(Component.text("Redstone Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack redstoneBlockCore() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.REDSTONE_BLOCK_CORE, REDSTONE_BLOCK_CORE_PROFILE);
        meta.displayName(Component.text("Redstone Block Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** An enchanted-looking Stick ({@link ItemMeta#setEnchantmentGlintOverride}, no real enchantment) - see this class's own doc on why it has no ability yet. */
    public ItemStack weatherWand() {
        var item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(WEATHER_WAND_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Weather Wand", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isWeatherWand(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(WEATHER_WAND_KEY, PersistentDataType.BYTE);
    }

    private void newShapedRecipe(NamespacedKey key, ItemStack result, String[] shape, Consumer<ShapedRecipe> ingredients) {
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
