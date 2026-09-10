package dev.icaro.foodtooltips.enchant;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Wires up the actual gameplay effect for the plugin's custom enchants that need
 * one beyond a menu/lore entry - see {@link IcarusEnchant}'s own class doc for why
 * these three (Flame, Lure, Infinite Quiver) exist as custom entries instead of
 * their vanilla counterparts. Also sets a flat base bow damage (30), unrelated to
 * any enchant, replacing vanilla's own draw-force-based number the same way {@code
 * SwordDamageService}/{@code ToolDamageService} give melee weapons a flat total.
 *
 * <p>The bow-shoot hook (damage + Infinite Quiver's arrow-save roll) mirrors
 * vanilla's own Infinity implementation, which uses this exact same {@code
 * EntityShootBowEvent#setConsumeItem} flag. Flame's burn damage is dealt directly
 * ({@code LivingEntity#damage(double)}, no source) rather than via {@code
 * setFireTicks} - vanilla's own fire-tick damage is a fixed 1 HP/second regardless
 * of level, which can't express "Y% of your damage", and layering our own damage on
 * top of vanilla's fire ticks would double up (and get multiplied a second time by
 * ElementalDamageListener's fire-tick handling) - so hit mobs don't visibly catch
 * fire here, only take the scheduled damage.
 */
public final class CustomEnchantEffectListener implements Listener {
    private static final double BASE_BOW_DAMAGE = 30.0;
    /** Flame's level 1/2 (duration seconds, damage % of the hit per second) - see IcarusEnchant's own doc for why this is a lookup, not a formula. */
    private static final double[] FLAME_DURATION = {0, 3.5, 4.0};
    private static final double[] FLAME_PERCENT = {0, 3, 6};

    private final Plugin plugin;
    private final EnchantService enchants;

    public CustomEnchantEffectListener(Plugin plugin, EnchantService enchants) {
        this.plugin = plugin;
        this.enchants = enchants;
    }

    /** Sets every arrow's base damage to {@link #BASE_BOW_DAMAGE} (the usual combat multiplier pipeline in CombatListener still applies on top at hit time) and rolls Infinite Quiver's arrow-save chance, exactly the way vanilla's own Infinity sets this same flag. */
    @EventHandler
    public void bowShoot(EntityShootBowEvent e) {
        if (e.getBow() == null || e.getBow().getType() != Material.BOW || !(e.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        arrow.setDamage(BASE_BOW_DAMAGE);
        int quiverLevel = this.enchants.levelOf(e.getBow(), new CustomEnchantEntry(IcarusEnchant.INFINITE_QUIVER));
        if (quiverLevel > 0 && ThreadLocalRandom.current().nextDouble() < quiverLevel * 0.10) {
            e.setConsumeItem(false);
        }
    }

    /** Flame: schedules {@code duration} seconds of {@code percent}%-of-this-hit damage, read from whichever hand is holding the bow at hit time (see this class's own doc for why there's no fire-tick visual). Runs at MONITOR so the damage read is CombatListener's final number, not the raw arrow damage. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void arrowHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)
                || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        if (bow.getType() != Material.BOW) {
            bow = shooter.getInventory().getItemInOffHand();
        }
        if (bow.getType() != Material.BOW) {
            return;
        }
        int level = this.enchants.levelOf(bow, new CustomEnchantEntry(IcarusEnchant.FLAME));
        if (level <= 0) {
            return;
        }
        int lvl = Math.min(level, FLAME_DURATION.length - 1);
        double finalDamage = e.getFinalDamage();
        double perTick = finalDamage * (FLAME_PERCENT[lvl] / 100.0);
        int ticks = (int) Math.floor(FLAME_DURATION[lvl]);
        for (int i = 1; i <= ticks; i++) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (target.isValid() && !target.isDead()) {
                    target.damage(perTick);
                }
            }, i * 20L);
        }
    }

    /** Lure: shortens the fishing bobber's wait time by a percentage instead of vanilla's own flat-tick-per-level reduction. */
    @EventHandler
    public void fish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack rod = p.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD) {
            rod = p.getInventory().getItemInOffHand();
        }
        if (rod.getType() != Material.FISHING_ROD) {
            return;
        }
        int level = this.enchants.levelOf(rod, new CustomEnchantEntry(IcarusEnchant.LURE));
        if (level <= 0) {
            return;
        }
        double factor = 1.0 - Math.min(1.0, 0.05 * level);
        e.getHook().setMinWaitTime((int) (e.getHook().getMinWaitTime() * factor));
        e.getHook().setMaxWaitTime((int) (e.getHook().getMaxWaitTime() * factor));
    }
}
