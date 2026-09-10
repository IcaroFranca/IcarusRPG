package dev.icaro.foodtooltips.enchant;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;

/**
 * Wires up the actual gameplay effect for the plugin's armor-slot custom enchants -
 * see {@link IcarusEnchant}'s own class doc for why Protection/Fire Protection/
 * Blast Protection/Projectile Protection/Feather Falling/Respiration/Thorns exist
 * as custom entries instead of their vanilla counterparts. Complements {@link
 * CustomEnchantEffectListener}, which covers held-item (bow/rod) effects instead.
 *
 * <p>Defense/True Defense from the Protection family stack with (not into) {@code
 * ArmorDefenseService}'s own armor+skill Defense: each is its own independent
 * multiplicative reduction using the exact same diminishing-returns curve ({@code
 * defense/(defense+100)}) {@code ArmorDefenseService#damageReduction} already uses,
 * composed on top of it rather than summed into one shared total, so this class
 * never needs to touch that service directly. {@link #protection} runs at {@link
 * EventPriority#HIGHEST}, registered after {@code ArmorDefenseListener}, so it
 * reduces whatever damage that listener's own reduction already left. Fire
 * Protection's True Defense is instead a flat subtraction (not a percentage) - the
 * "True" naming here means unconditional, bypassing every percentage-based
 * mitigation - applied before the percentage reductions above.
 */
public final class ArmorEnchantEffectListener implements Listener {
    private final EnchantService enchants;

    public ArmorEnchantEffectListener(EnchantService enchants) {
        this.enchants = enchants;
    }

    /** Sum of {@code enchant}'s level across every equipped armor piece (helmet/chest/legs/boots) - Protection-family entries can go on any of the four, and stack additively across pieces the way real vanilla Protection does. */
    private int armorLevel(LivingEntity target, IcarusEnchant enchant) {
        EntityEquipment eq = target.getEquipment();
        if (eq == null) {
            return 0;
        }
        CustomEnchantEntry entry = new CustomEnchantEntry(enchant);
        return this.enchants.levelOf(eq.getHelmet(), entry) + this.enchants.levelOf(eq.getChestplate(), entry)
                + this.enchants.levelOf(eq.getLeggings(), entry) + this.enchants.levelOf(eq.getBoots(), entry);
    }

    /** Fire Protection's flat True Defense (fire/lava only), then Protection's general Defense plus Blast/Projectile Protection's typed Defense (every cause, or the matching one) - each reduction independent of {@code ArmorDefenseService}'s own, see this class's own doc. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protection(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.LAVA) {
            int trueDefense = this.armorLevel(target, IcarusEnchant.FIRE_PROTECTION) * 2;
            if (trueDefense > 0) {
                e.setDamage(Math.max(0.0, e.getDamage() - trueDefense));
            }
        }
        int defense = this.armorLevel(target, IcarusEnchant.PROTECTION) * 5;
        defense += switch (cause) {
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> this.armorLevel(target, IcarusEnchant.BLAST_PROTECTION) * 30;
            case PROJECTILE -> this.armorLevel(target, IcarusEnchant.PROJECTILE_PROTECTION) * 7;
            default -> 0;
        };
        if (defense > 0) {
            e.setDamage(e.getDamage() * (1.0 - (double) defense / (defense + 100.0)));
        }
    }

    /** Feather Falling: extra safe fall height (1 block/level, flat subtraction) plus a 5%-per-level reduction on top, read from the boots - vanilla computed {@code e.getDamage()} here as if unenchanted, since the item can no longer carry the real vanilla enchantment. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void fall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        int level = this.armorLevel(target, IcarusEnchant.FEATHER_FALLING);
        if (level <= 0) {
            return;
        }
        double reduced = Math.max(0.0, e.getDamage() - level);
        e.setDamage(reduced * (1.0 - 0.05 * level));
    }

    /** Thorns: a flat 50% chance (not level-scaled) to reflect 3%-per-level of the incoming hit back at the attacker - unrelated to real vanilla Thorns' own chance/amount formula, so this entry is fully custom rather than an overleveled vanilla one. Reflected damage has no source (same reasoning as Flame's burn - see {@code CustomEnchantEffectListener}), so it doesn't re-enter CombatListener's own multiplier pipeline. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void thorns(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity target) || !(e.getDamager() instanceof LivingEntity attacker)) {
            return;
        }
        int level = this.armorLevel(target, IcarusEnchant.THORNS);
        if (level <= 0 || ThreadLocalRandom.current().nextDouble() >= 0.5) {
            return;
        }
        double reflect = e.getFinalDamage() * (0.03 * level);
        if (reflect > 0.0 && attacker.isValid() && !attacker.isDead()) {
            attacker.damage(reflect);
        }
    }

    /** Respiration: extends max underwater air by 10 ticks (0.5s) per point of the custom Respiration stat (15 per level), read from the helmet only (real vanilla Respiration is also helmet-only) - called every tick from {@code FoodTooltipsPlugin}'s existing periodic re-derivation loop, the same "re-derive every tick" pattern already used for Defense/Swing Range/bonus health. */
    public void applyRespiration(Player p) {
        int level = this.enchants.levelOf(p.getEquipment() == null ? null : p.getEquipment().getHelmet(), new CustomEnchantEntry(IcarusEnchant.RESPIRATION));
        int bonusTicks = level * 15 * 10;
        p.setMaximumAir(300 + bonusTicks);
    }
}
