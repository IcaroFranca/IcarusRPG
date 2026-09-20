package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.reforge.ReforgeService;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Collection;

/**
 * Mushroom Armor's own mechanic (Farming Collections, Mushroom entry M2) - a fixed per-piece
 * Health/Defense kit (Helmet +20 HP; Chestplate +10 HP/+10 Defense; Leggings +10 HP/+5
 * Defense; Boots +15 HP) that triples ALL of its own stats at night, "isso inclui dos
 * reforges" (including whatever a reforge later adds to that same piece) - plus continuous
 * Night Vision while the helmet is worn.
 *
 * <p>Defense's own tripling needs no code here at all: {@code ArmorDefenseService#defense}
 * already multiplies (armor Defense + reforge Defense) by {@code defenseMultiplier} before
 * anything else, so wiring {@link #bonusActive} into that same late-bound callback (from
 * {@code FoodTooltipsPlugin}, composed alongside Miner's Armor's own doubling) triples both
 * halves for free - {@link ArmorDefenseService#forceDefense} still gives each piece its own
 * base Defense number exactly like Miner's/Sprout Armor already do.
 *
 * <p>Health has no such shared multiplier to hook into (it's a real vanilla {@link
 * Attribute#MAX_HEALTH}, not a custom-computed number) so {@link #applyToInventory} bakes
 * one combined modifier per equipped piece directly onto that {@link ItemStack}, the same
 * "recompute every tick from current equipment" pattern {@code ReforgeService
 * #applyArmorAttackSpeedModifiers} already uses for Attack Speed - reading this piece's own
 * base Health for its slot PLUS {@code ReforgeService#armorStatsOf(item).health()} so a
 * reforge's own Health bonus is tripled too, then multiplying the total by 3 at night. This
 * is the ONLY Health modifier the piece ever carries while worn - {@link ReforgeService
 * #applyArmorAttributeModifiers} checks {@link #isMushroomPiece} and skips baking its own
 * separate Health modifier for these pieces, so the two never double-count the same reforge
 * roll.
 */
public final class MushroomArmorService {
    private static final NamespacedKey MUSHROOM_ARMOR_KEY = new NamespacedKey("foodtooltips", "mushroom_armor_piece");
    private static final NamespacedKey HEALTH_HEAD_KEY = new NamespacedKey("foodtooltips", "mushroom_health_head");
    private static final NamespacedKey HEALTH_CHEST_KEY = new NamespacedKey("foodtooltips", "mushroom_health_chest");
    private static final NamespacedKey HEALTH_LEGS_KEY = new NamespacedKey("foodtooltips", "mushroom_health_legs");
    private static final NamespacedKey HEALTH_FEET_KEY = new NamespacedKey("foodtooltips", "mushroom_health_feet");
    /** Per slot, per the player's own spec - Chestplate/Leggings' own Defense halves live in {@code FarmingCollectionsItemsService} instead, via {@link ArmorDefenseService#forceDefense} (a completely separate mechanism from this class's own Health handling). */
    public static final int HELMET_HEALTH = 20;
    public static final int CHESTPLATE_HEALTH = 10;
    public static final int LEGGINGS_HEALTH = 10;
    public static final int BOOTS_HEALTH = 15;
    public static final int CHESTPLATE_DEFENSE = 10;
    public static final int LEGGINGS_DEFENSE = 5;
    private static final int NIGHT_MULTIPLIER = 3;
    /** Comfortably longer than the periodic loop's own interval (see {@code FoodTooltipsPlugin}'s HUD tick) so continuous re-application never visibly flickers, short enough that taking the helmet off lets it fade out naturally within a few seconds - see this class's own doc on why it's never actively removed. */
    private static final int NIGHT_VISION_DURATION_TICKS = 220;

    private ReforgeService reforge;

    /** Wired in after construction (same reason {@code ArmorDefenseService#reforge} is) - lets {@link #applyToInventory} fold a piece's own reforge Health bonus into the same tripled modifier instead of leaving it to apply separately, un-tripled, from {@link ReforgeService}'s own bookkeeping. */
    public void reforge(ReforgeService reforge) {
        this.reforge = reforge;
    }

    public static void markMushroomPiece(ItemMeta meta) {
        meta.getPersistentDataContainer().set(MUSHROOM_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isMushroomPiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(MUSHROOM_ARMOR_KEY, PersistentDataType.BYTE);
    }

    /** Whether {@code e} is wearing at least one Mushroom Armor piece and it's currently night in its world - what {@code ArmorDefenseService#defenseMultiplier} composes in from {@code FoodTooltipsPlugin}, and what {@link #applyToInventory} itself checks for the Health side. */
    public boolean bonusActive(LivingEntity e) {
        return !e.getWorld().isDayTime() && wearingAnyPiece(e);
    }

    private static boolean wearingAnyPiece(LivingEntity e) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return false;
        }
        return isMushroomPiece(eq.getHelmet()) || isMushroomPiece(eq.getChestplate())
                || isMushroomPiece(eq.getLeggings()) || isMushroomPiece(eq.getBoots());
    }

    /**
     * Refreshes {@code player}'s currently equipped Mushroom Armor pieces' own baked Health
     * modifier (tripled at night, reforge included - see this class's own doc) and, while the
     * helmet is one of these pieces, tops up a short Night Vision effect. Call from the same
     * periodic per-player pass {@code ReforgeService#applyArmorAttackSpeedModifiers} already
     * runs on.
     */
    public void applyToInventory(Player player) {
        boolean night = !player.getWorld().isDayTime();
        PlayerInventory inv = player.getInventory();
        this.updateHealthModifier(inv.getHelmet(), EquipmentSlotGroup.HEAD, HEALTH_HEAD_KEY, HELMET_HEALTH, night);
        this.updateHealthModifier(inv.getChestplate(), EquipmentSlotGroup.CHEST, HEALTH_CHEST_KEY, CHESTPLATE_HEALTH, night);
        this.updateHealthModifier(inv.getLeggings(), EquipmentSlotGroup.LEGS, HEALTH_LEGS_KEY, LEGGINGS_HEALTH, night);
        this.updateHealthModifier(inv.getBoots(), EquipmentSlotGroup.FEET, HEALTH_FEET_KEY, BOOTS_HEALTH, night);
        if (isMushroomPiece(inv.getHelmet())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, NIGHT_VISION_DURATION_TICKS, 0, false, false, false));
        }
    }

    private void updateHealthModifier(ItemStack item, EquipmentSlotGroup slot, NamespacedKey key, int baseHealth, boolean night) {
        if (!isMushroomPiece(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        double reforgeHealth = this.reforge != null ? this.reforge.armorStatsOf(item).health() : 0.0;
        double total = (baseHealth + reforgeHealth) * (night ? NIGHT_MULTIPLIER : 1);
        AttributeModifier existing = null;
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.MAX_HEALTH);
        if (modifiers != null) {
            for (AttributeModifier m : modifiers) {
                if (m.getKey().equals(key)) {
                    existing = m;
                    break;
                }
            }
        }
        boolean needsUpdate = existing == null || Math.abs(existing.getAmount() - total) > 1.0E-4;
        if (!needsUpdate) {
            return;
        }
        if (existing != null) {
            meta.removeAttributeModifier(Attribute.MAX_HEALTH, existing);
        }
        if (Math.abs(total) > 1.0E-4) {
            meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(key, total, AttributeModifier.Operation.ADD_NUMBER, slot));
        }
        item.setItemMeta(meta);
    }
}
