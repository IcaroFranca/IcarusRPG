package dev.icaro.foodtooltips.enchant;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Wires up the plugin's own bow enchant family - see {@link IcarusEnchant}'s own
 * class doc for the full list (Aiming, Chance, Piercing, Snipe, plus Cubism/Ender
 * Slayer/Impaling extended here to bows too). Chance has no code here at all - it's
 * read straight off the killer's main hand in {@code CombatListener#rollEquipmentDrops},
 * the same place real Looting already is, since it's meant to be that enchant's
 * bow-usable equivalent. Everything else needs the bow's own custom levels to survive
 * from shoot time (this class's own {@link #bowShoot}) to hit time, since by the time
 * an arrow lands the shooter could easily be holding something else entirely - the
 * same problem {@code CustomEnchantEffectListener#arrowHit}'s own "read whichever hand
 * holds a bow" guess works around for Flame, solved properly here instead by tagging
 * the arrow's own {@link PersistentDataContainer} at shoot time. {@code
 * CombatListener#arrowEnchantPercent} reads the Cubism/Ender Slayer/Impaling/Snipe
 * keys back out; this class keeps their {@link NamespacedKey}s public for exactly
 * that reason - same "read a key defined on the class that owns the write" shape
 * {@code CombatListener} already uses for {@code MinerVariantService#VARIANT_KEY}.
 */
public final class BowEnchantEffectListener implements Listener {
    /** Extra targets a Piercing arrow hits take this fraction of the damage the first target took - see {@link #piercingHit}. */
    private static final double PIERCE_EXTRA_TARGET_FRACTION = 0.25;
    /** Real vanilla pierce level Piercing sets on the arrow at shoot time - only one tier exists (see {@link IcarusEnchant#PIERCING}'s own doc, max level 1), so this is a constant rather than scaled by level. */
    private static final int PIERCE_LEVEL = 1;
    /** Aiming's own per-tick homing loop safety cap, in ticks - well past any realistic arrow flight time, just here so a stuck/never-landing arrow can't leave a task running forever. */
    private static final int AIMING_MAX_TICKS = 200;

    /** Cubism's custom level, tagged onto the arrow at shoot time - see {@code CombatListener#arrowEnchantPercent} (that class, not this one, reads it back). */
    public static final NamespacedKey CUBISM_LEVEL_KEY = new NamespacedKey("foodtooltips", "bow_cubism_level");
    /** Ender Slayer's custom level, same shape as {@link #CUBISM_LEVEL_KEY}. */
    public static final NamespacedKey ENDER_SLAYER_LEVEL_KEY = new NamespacedKey("foodtooltips", "bow_ender_slayer_level");
    /** Impaling's custom level, same shape as {@link #CUBISM_LEVEL_KEY}. */
    public static final NamespacedKey IMPALING_LEVEL_KEY = new NamespacedKey("foodtooltips", "bow_impaling_level");
    /** Snipe's custom level, same shape as {@link #CUBISM_LEVEL_KEY}. */
    public static final NamespacedKey SNIPE_LEVEL_KEY = new NamespacedKey("foodtooltips", "snipe_level");
    /** Where the arrow was when it was fired (Snipe's own distance-traveled math reads these back at hit time) - three separate doubles rather than one combined key, since {@link PersistentDataType} has no native Location/Vector type. */
    public static final NamespacedKey ORIGIN_X_KEY = new NamespacedKey("foodtooltips", "snipe_origin_x");
    public static final NamespacedKey ORIGIN_Y_KEY = new NamespacedKey("foodtooltips", "snipe_origin_y");
    public static final NamespacedKey ORIGIN_Z_KEY = new NamespacedKey("foodtooltips", "snipe_origin_z");
    /** How many entities this specific arrow has already hit - see {@link #piercingHit}. Private: nothing outside this class needs it. */
    private static final NamespacedKey PIERCE_HITS_KEY = new NamespacedKey("foodtooltips", "pierce_hits");

    private final Plugin plugin;
    private final EnchantService enchants;

    public BowEnchantEffectListener(Plugin plugin, EnchantService enchants) {
        this.plugin = plugin;
        this.enchants = enchants;
    }

    /**
     * Tags the freshly-fired arrow with every bow enchant level that needs to survive
     * to hit time (Cubism/Ender Slayer/Impaling/Snipe - 0 skipped entirely, so an
     * unenchanted bow's arrows carry no extra PDC at all), records Snipe's own launch
     * position, sets real vanilla {@link AbstractArrow#setPierceLevel} for Piercing,
     * and starts Aiming's homing task. Player shots only, same guard shape {@code
     * CustomEnchantEffectListener#bowShoot} uses (a mob-shot BOW - skeletons in
     * particular - never carries any of this plugin's own custom enchants anyway,
     * but the {@code instanceof Player} guard is what actually matters for casting
     * {@link EntityShootBowEvent#getEntity()} down to a shooter for Aiming's task).
     */
    @EventHandler
    public void bowShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player) || e.getBow() == null || !IcarusEnchant.isBow(e.getBow().getType())
                || !(e.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        ItemStack bow = e.getBow();
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        this.tagLevel(pdc, CUBISM_LEVEL_KEY, bow, IcarusEnchant.CUBISM);
        this.tagLevel(pdc, ENDER_SLAYER_LEVEL_KEY, bow, IcarusEnchant.ENDER_SLAYER);
        this.tagLevel(pdc, IMPALING_LEVEL_KEY, bow, IcarusEnchant.IMPALING);
        boolean sniped = this.tagLevel(pdc, SNIPE_LEVEL_KEY, bow, IcarusEnchant.SNIPE);
        if (sniped) {
            Location origin = arrow.getLocation();
            pdc.set(ORIGIN_X_KEY, PersistentDataType.DOUBLE, origin.getX());
            pdc.set(ORIGIN_Y_KEY, PersistentDataType.DOUBLE, origin.getY());
            pdc.set(ORIGIN_Z_KEY, PersistentDataType.DOUBLE, origin.getZ());
        }
        if (this.enchants.customLevel(bow, IcarusEnchant.PIERCING) > 0) {
            arrow.setPierceLevel(PIERCE_LEVEL);
        }
        int aimingLevel = this.enchants.customLevel(bow, IcarusEnchant.AIMING);
        if (aimingLevel > 0) {
            this.startHoming(arrow, aimingLevel * 2.0);
        }
    }

    /** Sets {@code key} to {@code enchant}'s level on {@code bow}, but only if it's actually enchanted (an unenchanted bow's arrows carry no PDC clutter at all) - returns whether it was set, so callers with extra per-enchant setup (Snipe's own origin) know whether to bother. */
    private boolean tagLevel(PersistentDataContainer pdc, NamespacedKey key, ItemStack bow, IcarusEnchant enchant) {
        int level = this.enchants.customLevel(bow, enchant);
        if (level <= 0) {
            return false;
        }
        pdc.set(key, PersistentDataType.INTEGER, level);
        return true;
    }

    /**
     * Piercing: real vanilla pass-through ({@link AbstractArrow#setPierceLevel}, set
     * in {@link #bowShoot}) already lets the arrow hit more than one entity - this
     * only adds the "extra targets take {@value #PIERCE_EXTRA_TARGET_FRACTION}
     * fraction of the damage" half, tracked per-arrow via {@link #PIERCE_HITS_KEY}
     * (a plain non-piercing arrow only ever fires this event once anyway, so the
     * reduction branch below never triggers for one). Runs at NORMAL - before {@code
     * CombatListener#damage}'s own HIGH-priority multiplier stack - so the reduction
     * applies to the raw arrow damage those multipliers then scale, same ordering
     * {@code CombatListener}'s own melee/projectile split already relies on elsewhere.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void piercingHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof AbstractArrow arrow) || !(e.getEntity() instanceof LivingEntity)) {
            return;
        }
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        int priorHits = pdc.getOrDefault(PIERCE_HITS_KEY, PersistentDataType.INTEGER, 0);
        if (priorHits > 0) {
            e.setDamage(e.getDamage() * PIERCE_EXTRA_TARGET_FRACTION);
        }
        pdc.set(PIERCE_HITS_KEY, PersistentDataType.INTEGER, priorHits + 1);
    }

    /**
     * Aiming: a repeating, self-cancelling per-tick loop (same shape {@code
     * MeleeEnchantEffectListener#startVenomTicker} uses for Venomous) that redirects
     * the arrow's velocity toward the nearest {@link Enemy} within {@code range}
     * blocks every tick, preserving its current speed rather than snapping it -
     * cancels itself the moment the arrow is no longer actually flying (invalid,
     * dead, landed) or {@link #AIMING_MAX_TICKS} is hit. A tick with no qualifying
     * target nearby just leaves the arrow's own vanilla trajectory alone.
     */
    private void startHoming(AbstractArrow arrow, double range) {
        BukkitTask[] holder = new BukkitTask[1];
        int[] ticks = {0};
        holder[0] = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround() || ticks[0]++ > AIMING_MAX_TICKS) {
                holder[0].cancel();
                return;
            }
            LivingEntity nearest = nearestEnemy(arrow, range);
            if (nearest == null) {
                return;
            }
            Vector toTarget = nearest.getEyeLocation().toVector().subtract(arrow.getLocation().toVector());
            if (toTarget.lengthSquared() < 1.0E-4) {
                return;
            }
            double speed = arrow.getVelocity().length();
            arrow.setVelocity(toTarget.normalize().multiply(speed));
        }, 0L, 1L);
    }

    /** The closest living {@link Enemy} within {@code range} blocks of {@code arrow}, or null if none qualify. */
    private static LivingEntity nearestEnemy(AbstractArrow arrow, double range) {
        LivingEntity nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Entity nearby : arrow.getWorld().getNearbyEntities(arrow.getLocation(), range, range, range)) {
            if (!(nearby instanceof Enemy enemy) || !enemy.isValid() || enemy.isDead()) {
                continue;
            }
            double distanceSquared = enemy.getLocation().distanceSquared(arrow.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = enemy;
            }
        }
        return nearest;
    }
}
