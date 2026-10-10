package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.i18n.Language;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Rotten Flesh Collection M6 - a custom-head item ("Heart (moldy)", minecraft-heads.com
 * Custom Head ID 60520), {@link ItemTier#B}, per the player's own explicit stats: +{@value
 * #HEALTH} Max Health, +{@value #VITALITY} Max Vitality, +{@value #MENDING} Mending while
 * stored in the Accessory Bag (see {@link AccessoryItems#markHealthVitalityMending}, summed by
 * {@code skills.AccessoryBagService#totalHealthBonus}/{@code #totalVitalityBonus}/{@code
 * #totalMendingBonus} - the Health portion turned into a real Max Health attribute by {@code
 * skills.AccessoryBagService#applyAccessoryHealth}, same "remove old, reapply if still earned"
 * idempotent pattern {@code item.SkeletonHatService#applySpeedAttribute} already uses).
 *
 * <p>Tagged {@link AccessoryType#CHARM} (a standalone accessory, not part of a Talisman/Ring/
 * Artifact upgrade chain) with its own single-member {@code "zombies_heart"} family, purely so
 * {@code skills.AccessoryBagService}'s own storage-slot filter recognizes it as a real accessory
 * at all (see that class's own {@code scheduleFilterSweep} doc: an untyped item gets ejected
 * from the bag on sight) - a player who stores it there gets these three stats passively, same
 * as any other standalone Orb/Charm. ALSO consumed as a crafting ingredient by {@link
 * ZombieSwordService}/{@code item.ZombieArmorService} (2 and several respectively) - being
 * storable for its own stats and being a crafting ingredient elsewhere aren't mutually
 * exclusive, same as every other accessory in this plugin.
 *
 * <p>"This item is Undead!" is flavor text only today (no functional hook) - same dark-green
 * "Undead" styling {@code item.legendary.LegendaryWeaponService}'s own Undead's Sword already
 * uses for the same category label.
 */
public final class ZombiesHeartService {
    private static final UUID PROFILE = UUID.nameUUIDFromBytes("icarusrpg:zombies_heart".getBytes(StandardCharsets.UTF_8));
    /** See {@link #isZombiesHeart} - a plain PDC byte marker, checked instead of an exact-NBT {@code RecipeChoice.ExactChoice} match for every recipe in {@link #RECIPES} (see {@code item.ZombieSwordService#guardZombiesHeartRecipes}'s own doc on why). */
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "zombies_heart");
    private static final String FAMILY = "zombies_heart";
    /**
     * Every recipe that consumes a Zombie's Heart - all registered with a plain {@link
     * Material#PLAYER_HEAD} ingredient and enforced by {@code
     * ZombieSwordService#guardZombiesHeartRecipes} (see its own doc), and shown with the real
     * item in the recipe book instead of that generic head (see {@code FoodTooltipsPlugin}'s
     * own {@code RecipeBookMenuService#ingredientDisplay} wiring).
     */
    public static final Set<NamespacedKey> RECIPES = Set.of(CollectionsCatalog.ZOMBIE_SWORD_RECIPE,
            CollectionsCatalog.ZOMBIE_CHESTPLATE_RECIPE, CollectionsCatalog.ZOMBIE_LEGGINGS_RECIPE, CollectionsCatalog.ZOMBIE_BOOTS_RECIPE);
    public static final int HEALTH = 50;
    public static final int VITALITY = 30;
    public static final int MENDING = 30;

    private final ItemTierService tiers;

    public ZombiesHeartService(ItemTierService tiers) {
        this.tiers = tiers;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(PROFILE);
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", HeadTexture.ZOMBIES_HEART));
        meta.setPlayerProfile(profile);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Zombie's Heart", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark((ItemMeta) meta, AccessoryType.CHARM, FAMILY, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0);
        AccessoryItems.markHealthVitalityMending((ItemMeta) meta, HEALTH, VITALITY, MENDING);
        this.tiers.forceTier((ItemMeta) meta, ItemTier.B);
        meta.lore(List.of(
                Component.text("Health: +" + HEALTH, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Vitality: +" + VITALITY, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Mending: +" + MENDING, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("This item is Undead!", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    /**
     * See {@link #KEY}'s own doc - whether {@code item} is genuinely a Zombie's Heart, checked by
     * PDC marker rather than exact-NBT identity. {@link #KEY} only exists since 0.76.219, so a
     * Zombie's Heart crafted before that carries just its {@link AccessoryItems#family} marker
     * ({@value #FAMILY}, set by {@link #createItem} since the item was first added) - accepted
     * too, or every older heart would be silently refused by {@code
     * ZombieSwordService#guardZombiesHeartRecipes} (reported: "ta pedindo uma player head
     * generica sem razão" - the recipe looked satisfied but never produced a result).
     */
    public static boolean isZombiesHeart(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && (meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE)
                || FAMILY.equals(AccessoryItems.family(item)));
    }
}
