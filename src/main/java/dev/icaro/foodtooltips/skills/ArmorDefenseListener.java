package dev.icaro.foodtooltips.skills;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Keeps {@link ArmorDefenseService}'s "Defense replaces vanilla armor" rule in effect
 * for players and mobs alike: re-zeroes vanilla ARMOR/ARMOR_TOUGHNESS on player join
 * (equipment changes are re-zeroed by the periodic HUD tick in {@code
 * FoodTooltipsPlugin}, not a dedicated armor-change event — Paper's exact event
 * class/package for that varies by version, and the existing "re-derive every tick"
 * pattern already used for Swing Range/bonus health is simpler and doesn't depend on
 * it — mobs get it once on spawn instead, see {@code CombatListener#spawn}), and
 * applies the custom Defense's damage reduction to any LivingEntity's incoming hits
 * (this used to be {@code GeneralSkillListener#defense}, moved here now that Defense
 * is armor-driven rather than Mining-level-driven, and used to be player-only).
 */
public final class ArmorDefenseListener implements Listener {
    private final ArmorDefenseService armor;

    public ArmorDefenseListener(ArmorDefenseService armor) {
        this.armor = armor;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.armor.neutralizeVanillaArmor(e.getPlayer());
        this.armor.applyDefenseTooltip(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void defense(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        LivingEntity source = null;
        if (e instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof LivingEntity direct) {
                source = direct;
            } else if (byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
                source = shooter;
            }
        }
        // source (projectile shooters included - the Ender Dragon's fireballs) only decides
        // how much Defense counts (see ArmorDefenseService#attackerDefensePierce); the mob-
        // type multiplier below keeps reacting to a direct hit only, same as before.
        double damage = e.getDamage() * (1.0 - this.armor.damageReduction(target, source));
        if (e instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof LivingEntity attacker) {
            damage *= this.armor.incomingMultiplier(target, attacker);
        }
        e.setDamage(damage);
    }
}
