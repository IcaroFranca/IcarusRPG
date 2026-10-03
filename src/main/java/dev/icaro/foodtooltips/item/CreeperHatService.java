package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gunpowder Collection M2 - a {@link ItemTier#D} helmet (a green-dyed Leather Cap, no custom
 * texture sent for this one - same "dyed leather stands in" trick {@code
 * combat.MinerVariantService}'s own Miner's Armor already uses, green for the Creeper theme).
 * Per the player's own explicit spec: Health +{@value #HEALTH} (a real {@link
 * Attribute#MAX_HEALTH} modifier baked into the item, scoped to {@link EquipmentSlotGroup#HEAD} -
 * same "no periodic sweep needed" shape {@code item.ZombieArmorService} already uses), Defense
 * +{@value #DEFENSE} (see {@link ArmorDefenseService#forceDefense}), Strength +{@value
 * #STRENGTH_BONUS}/Crit Chance +{@value #CRIT_CHANCE_BONUS}/Intelligence +{@value
 * #INTELLIGENCE_BONUS} while worn (combined into {@code stats.PlayerStatsService}'s/{@code
 * combat.CombatListener}'s already-existing late-bound fields at the wiring site in {@code
 * FoodTooltipsPlugin}, same shape {@code item.SpidersBootsService} already uses for its own
 * Intelligence/Speed), Crit Damage +{@value #CRIT_DAMAGE_BONUS}% while worn (its own new
 * late-bound {@code combat.CombatListener#creeperHatCritDamageBonus} field, since nothing
 * existing took a worn-item's Crit Damage bonus keyed by {@link Player} rather than by held
 * weapon), and a passive: full immunity to explosion damage while worn (see {@link #explosion}).
 */
public final class CreeperHatService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "creeper_hat");
    private static final NamespacedKey HEALTH_KEY = new NamespacedKey("foodtooltips", "creeper_hat_health");
    public static final int HEALTH = 5;
    public static final int DEFENSE = 5;
    public static final int STRENGTH_BONUS = 5;
    public static final double CRIT_CHANCE_BONUS = 5.0;
    public static final double CRIT_DAMAGE_BONUS = 5.0;
    public static final int INTELLIGENCE_BONUS = 5;

    private final ItemTierService tiers;

    public CreeperHatService(ItemTierService tiers) {
        this.tiers = tiers;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.GREEN);
        }
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.D);
        ArmorDefenseService.forceDefense(meta, DEFENSE);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(HEALTH_KEY, HEALTH, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
        meta.displayName(Component.text("Creeper Hat", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Health: +" + HEALTH, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Defense: +" + DEFENSE, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Strength: +" + STRENGTH_BONUS, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Crit Chance: +" + (int) CRIT_CHANCE_BONUS + "%", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false),
                Component.text("Crit Damage: +" + (int) CRIT_DAMAGE_BONUS + "%", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false),
                Component.text("Intelligence: +" + INTELLIGENCE_BONUS, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Grants immunity to explosion damage.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCreeperHat(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** {@value #STRENGTH_BONUS} while {@code p} wears this exact helmet, or 0 otherwise - combined with {@code item.ZombieSwordService#heldStrengthBonus}/{@code item.LeapingSwordService#heldStrengthBonus} at the wiring site. */
    public int strengthBonus(Player p) {
        return isCreeperHat(p.getInventory().getHelmet()) ? STRENGTH_BONUS : 0;
    }

    /** {@value #CRIT_CHANCE_BONUS} while {@code p} wears this exact helmet, or 0 otherwise - combined with {@code item.SpiderHatService#critChanceBonus} at the wiring site. */
    public double critChanceBonus(Player p) {
        return isCreeperHat(p.getInventory().getHelmet()) ? CRIT_CHANCE_BONUS : 0.0;
    }

    /** {@value #CRIT_DAMAGE_BONUS} while {@code p} wears this exact helmet, or 0 otherwise - wired into {@code combat.CombatListener#creeperHatCritDamageBonus}. */
    public double critDamageBonus(Player p) {
        return isCreeperHat(p.getInventory().getHelmet()) ? CRIT_DAMAGE_BONUS : 0.0;
    }

    /** {@value #INTELLIGENCE_BONUS} while {@code p} wears this exact helmet, or 0 otherwise - combined with {@code item.SkeletonHatService#intelligenceBonus}/{@code item.SpidersBootsService#intelligenceBonus} at the wiring site. */
    public int intelligenceBonus(Player p) {
        return isCreeperHat(p.getInventory().getHelmet()) ? INTELLIGENCE_BONUS : 0;
    }

    /** Full immunity to explosion damage while wearing this exact helmet - HIGHEST, same tier {@code combat.CombatListener#secondWind}/{@code item.SkeletonsHelmetService#boneShield} already nullify a hit at. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }
        if (isCreeperHat(p.getInventory().getHelmet())) {
            e.setCancelled(true);
        }
    }
}
