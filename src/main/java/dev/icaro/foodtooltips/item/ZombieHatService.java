package dev.icaro.foodtooltips.item;

import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Rotten Flesh Collection M5 - a plain {@link Material#ZOMBIE_HEAD} (the vanilla head block
 * worn as a helmet, no custom texture - per the player's own explicit "representado por uma
 * cabeça de zumbi normal"), {@link ItemTier#D}. No static Defense of its own ({@code
 * ZOMBIE_HEAD} isn't in {@code skills.ArmorDefenseService}'s own Material table, so its base
 * piece Defense is already 0) - the whole stat is {@value #DEFENSE_PER_ZOMBIE} Defense per real
 * {@link EntityType#ZOMBIE} within {@value #RANGE} blocks of the wearer, live, read by {@code
 * skills.ArmorDefenseService#defense} via its own {@code zombieHatBonus} late-bound field (same
 * shape {@code farmerBootsBonus} already uses) - no cap, per the player's own spec (unlike the
 * Zombie Pickaxe's own "Rotten" ability, which IS capped).
 *
 * <p>Deliberately only {@link EntityType#ZOMBIE} itself, not the wider "Undead" family {@link
 * ZombiePickaxeService}'s own ability counts - the player's own spec says "Zombie", not
 * "Undead mob", for this item specifically.
 */
public final class ZombieHatService {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "zombie_hat");
    public static final int DEFENSE_PER_ZOMBIE = 10;
    public static final int RANGE = 8;

    private final ItemTierService tiers;

    public ZombieHatService(ItemTierService tiers) {
        this.tiers = tiers;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.ZOMBIE_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.D);
        meta.displayName(Component.text("Zombie Hat").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Defense: +" + DEFENSE_PER_ZOMBIE, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Gives +" + DEFENSE_PER_ZOMBIE + " Defense for each Zombie", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("within " + RANGE + " blocks.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isZombieHat(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /**
     * {@value #DEFENSE_PER_ZOMBIE} per real Zombie within {@value #RANGE} blocks of {@code e},
     * or 0 if {@code e} isn't wearing this exact helmet - wired into {@code
     * skills.ArmorDefenseService#zombieHatBonus}.
     */
    public int defenseBonus(LivingEntity e) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null || !isZombieHat(eq.getHelmet())) {
            return 0;
        }
        int count = 0;
        for (Entity nearby : e.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (nearby.getType() == EntityType.ZOMBIE) {
                count++;
            }
        }
        return defenseForCount(count);
    }

    /** Pure {@value #DEFENSE_PER_ZOMBIE}-per-zombie math, no cap - broken out from {@link #defenseBonus} so it's unit-testable without a live {@link LivingEntity}/world. */
    static int defenseForCount(int zombieCount) {
        return Math.max(0, zombieCount) * DEFENSE_PER_ZOMBIE;
    }
}
