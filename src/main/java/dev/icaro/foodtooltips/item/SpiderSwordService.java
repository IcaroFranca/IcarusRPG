package dev.icaro.foodtooltips.item;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Spider Eye Collection M2 - a plain Iron Pickaxe-style passive, per the player's own explicit
 * "mesma coisa da undead, mas para artropodes" (the same thing as the Undead one, but for
 * Arthropods): {@value #PERCENT}% bonus damage against any {@link #ARTHROPOD_TYPES} mob
 * (Spider, Cave Spider, Silverfish, Endermite, Bee - the same vanilla category Bane of
 * Arthropods itself targets), mirroring {@code item.legendary.LegendaryWeaponService}'s own
 * Undead's Sword multiplier (also {@value #PERCENT}%) but for a different mob family, and
 * without that item's own Legendary-only trappings (no custom resource-pack model, no inflated
 * max durability) since this is a plain Collections reward, not a Legendary weapon. No tier is
 * forced - a plain Iron Sword's own Material-based tier is exactly right, same "ability only, no
 * other stat" shape {@code item.ZombiePickaxeService} already uses.
 *
 * <p>{@link #damageMultiplier} is wired into {@code combat.CombatListener}'s own late-bound
 * {@code arthropodMultiplier} field, multiplied into the outgoing melee/ranged damage formula
 * the exact same way {@code item.legendary.LegendaryWeaponService#undeadMultiplier} already is.
 */
public final class SpiderSwordService {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "spider_sword");
    public static final double MULTIPLIER = 2.0;
    public static final int PERCENT = 100;
    /**
     * Real vanilla's own "arthropod" mob group (what Bane of Arthropods targets) - used to be
     * readable via {@code LivingEntity#getCategory() == EntityCategory.ARTHROPOD}, but that API
     * is deprecated-for-removal (and its own {@code @Contract("-> fail")} annotation as of
     * Paper 26.2 means it actually throws now, not just a soft deprecation) - this plain
     * {@link EntityType} set is the direct, type-safe replacement, same shape {@code
     * combat.CombatListener}'s own {@code ARTHROPOD_TYPES} already uses for the vanilla Bane of
     * Arthropods damage formula (that one's missing {@link EntityType#BEE} - a separate,
     * pre-existing inconsistency, not touched here since fixing it isn't what this change is
     * about).
     */
    private static final Set<EntityType> ARTHROPOD_TYPES = EnumSet.of(EntityType.SPIDER, EntityType.CAVE_SPIDER,
            EntityType.SILVERFISH, EntityType.ENDERMITE, EntityType.BEE);

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Spider Sword").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Ability: Arthropod Slayer", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Deals +" + PERCENT + "% damage against", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Arthropod mobs (Spiders, Silverfish,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Endermites, Bees).", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSpiderSword(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** {@value #MULTIPLIER} if {@code weapon} is this exact sword and {@code target} is an {@link #ARTHROPOD_TYPES} mob, else 1.0 - wired into {@code combat.CombatListener#arthropodMultiplier}. */
    public double damageMultiplier(LivingEntity target, ItemStack weapon) {
        if (!isSpiderSword(weapon)) {
            return 1.0;
        }
        return ARTHROPOD_TYPES.contains(target.getType()) ? MULTIPLIER : 1.0;
    }
}
