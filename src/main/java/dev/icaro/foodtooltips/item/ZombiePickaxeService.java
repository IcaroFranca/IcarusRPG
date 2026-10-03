package dev.icaro.foodtooltips.item;

import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Rotten Flesh Collection M2 - an Iron Pickaxe with the "Rotten" ability, per the player's own
 * explicit spec: {@value #FORTUNE_PER_MOB} Mining Fortune per Undead mob within {@value
 * #RANGE} blocks of the wielder, capped at {@value #MAX_BONUS}, active only while this exact
 * pickaxe is the held main-hand item - read by {@code skills.GeneralSkillService#fortune} (its
 * own {@code pickaxeMiningFortuneBonus} late-bound field, same shape {@code heat.HeatService}
 * already uses for its own live Mining Fortune bonus). No tier is forced - a plain Iron
 * Pickaxe's own Material-based tier (D) is exactly right, this item has no stat beyond the
 * ability itself.
 *
 * <p>{@link #UNDEAD_TYPES} is the same curated "every vanilla EntityType the game itself
 * treats as undead" list {@code item.legendary.LegendaryWeaponService}'s own Undead's Sword
 * uses (Smite/Instant Health-Harming's own target set) - duplicated rather than shared, same
 * "small hand-picked set, no shared util" precedent {@code creaking.EntityClassifier}'s own
 * NEUTRAL_TYPES/PASSIVE_TYPES already set.
 */
public final class ZombiePickaxeService {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "zombie_pickaxe");
    public static final int FORTUNE_PER_MOB = 5;
    public static final int RANGE = 30;
    public static final int MAX_BONUS = 25;

    private static final Set<EntityType> UNDEAD_TYPES = Set.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK,
            EntityType.DROWNED, EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.PHANTOM, EntityType.ZOGLIN, EntityType.WITHER);

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Zombie Pickaxe").decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text("Ability: Rotten", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Grants +" + FORTUNE_PER_MOB + " Mining Fortune per", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Undead mob within " + RANGE + " blocks of you.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("(Max " + MAX_BONUS + " Mining Fortune)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isZombiePickaxe(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /**
     * {@value #FORTUNE_PER_MOB} per Undead mob within {@value #RANGE} blocks, capped at
     * {@value #MAX_BONUS}, or 0 if {@code p} isn't currently holding this exact pickaxe in
     * their main hand - wired into {@code skills.GeneralSkillService#pickaxeMiningFortuneBonus}.
     */
    public int miningFortuneBonus(Player p) {
        if (!isZombiePickaxe(p.getInventory().getItemInMainHand())) {
            return 0;
        }
        int count = 0;
        for (Entity nearby : p.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (UNDEAD_TYPES.contains(nearby.getType())) {
                count++;
            }
        }
        return fortuneForCount(count);
    }

    /** Pure {@value #FORTUNE_PER_MOB}-per-mob, capped at {@value #MAX_BONUS}, math - broken out from {@link #miningFortuneBonus} so it's unit-testable without a live {@link Player}/world, same reasoning {@code HurricaneBowService#arrowCountFor} is its own static method. */
    static int fortuneForCount(int undeadCount) {
        return Math.min(MAX_BONUS, Math.max(0, undeadCount) * FORTUNE_PER_MOB);
    }
}
