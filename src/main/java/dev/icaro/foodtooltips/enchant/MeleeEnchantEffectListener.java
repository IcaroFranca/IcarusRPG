package dev.icaro.foodtooltips.enchant;

import dev.icaro.foodtooltips.combat.MobVisualService;
import dev.icaro.foodtooltips.item.SwordDamageService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Wires up the on-hit/on-kill half of the plugin's melee-weapon enchant family (see
 * {@link IcarusEnchant}'s own doc for the full list) - the damage-percentage half
 * (Critical, Cubism, Ender Slayer, Execute, First Strike, Giant Killer, Impaling) and
 * Lethality's Defense-reduction debuff live in {@code CombatListener} instead, since
 * they need to fold directly into that class's own outgoing-damage formula. This class
 * covers the rest: Life Steal, Vampirism, Thunderlord, Venomous (all triggered by a
 * genuine melee hit - {@code e.getDamager() instanceof Player}, which already excludes
 * arrows, same guard {@code CustomEnchantEffectListener#fireAspectHit} uses) and
 * Experience (mob kills and ore breaks alike). Luck lives in {@code
 * CombatListener#rollEquipmentDrops} instead, alongside real vanilla Looting, since
 * both now scale the same per-slot equipped-gear drop-chance formula.
 */
public final class MeleeEnchantEffectListener implements Listener {
    /** Thunderlord fires on every 3rd qualifying hit against the SAME target - see {@link #thunderlord}. */
    private static final int THUNDERLORD_HIT_INTERVAL = 3;
    /** Venomous' own stack/duration caps - see {@link #addVenomStack}. */
    private static final int VENOM_MAX_STACKS = 40;
    private static final long VENOM_DURATION_MILLIS = 5000L;

    private final Plugin plugin;
    private final EnchantService enchants;
    private final MobVisualService visuals;
    private final NamespacedKey venomSlowKey;
    /** Thunderlord's own per-attacker hit counter, keyed by attacker and remembering which target it's counting against - see {@link #thunderlord}. Cleared on quit. */
    private final Map<UUID, ThunderlordState> thunderlordHits = new HashMap<>();
    /** Venomous' own per-target stacking state - self-pruning (see {@link #startVenomTicker}), so unlike {@code CombatListener}'s Lethality map this one needs no separate periodic sweep. */
    private final Map<UUID, VenomState> venom = new HashMap<>();

    /** One target's current Venomous stack state - {@code magnitudePercent} and {@code weaponBase} are whichever hit most recently refreshed the debuff, same "latest hit wins" simplification {@code CombatListener}'s own Lethality debuff makes. */
    private record VenomState(int stacks, double magnitudePercent, double weaponBase, long expiry) {
    }

    /** Thunderlord's own hit counter for one attacker - {@code hits} only keeps counting up while {@code target} stays the same; hitting a different target restarts it at 1 (see {@link #thunderlord}). */
    private record ThunderlordState(UUID target, int hits) {
    }

    public MeleeEnchantEffectListener(Plugin plugin, EnchantService enchants, MobVisualService visuals) {
        this.plugin = plugin;
        this.enchants = enchants;
        this.visuals = visuals;
        this.venomSlowKey = new NamespacedKey(plugin, "venomous_slow");
    }

    /** Life Steal: heals the attacker for a percentage of their own max health on every melee hit against a non-player target. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void lifeSteal(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof LivingEntity target) || target instanceof Player) {
            return;
        }
        int level = this.enchants.customLevel(attacker.getInventory().getItemInMainHand(), IcarusEnchant.LIFE_STEAL);
        if (level <= 0) {
            return;
        }
        AttributeInstance a = attacker.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? attacker.getHealth() : a.getValue();
        attacker.setHealth(Math.min(max, attacker.getHealth() + max * 0.005 * level));
    }

    /** Vampirism: heals the killer for a percentage of their own missing health whenever their melee weapon carries it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void vampirism(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) {
            return;
        }
        int level = this.enchants.customLevel(p.getInventory().getItemInMainHand(), IcarusEnchant.VAMPIRISM);
        if (level <= 0) {
            return;
        }
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? p.getHealth() : a.getValue();
        double missing = max - p.getHealth();
        if (missing <= 0.0) {
            return;
        }
        p.setHealth(Math.min(max, p.getHealth() + missing * 0.01 * level));
    }

    /**
     * Thunderlord: every 3rd qualifying melee hit against the SAME target strikes it
     * with a lightning bolt (visual only - {@code strikeLightningEffect}, not a real
     * lightning strike, so there's no risk of double-dipping with vanilla's own
     * lightning damage or starting fires) dealing a percentage of that hit's own final
     * damage directly ({@code target.damage}, no source, same as Flame/Fire Aspect's
     * own burn in {@code CustomEnchantEffectListener} - so it doesn't re-enter {@code
     * CombatListener}'s multiplier pipeline a second time). Switching to a different
     * target restarts the count at 1 rather than carrying it over. Deferred a tick,
     * same as {@code CombatListener}'s own Ferocity extra hits - calling {@code
     * target.damage} synchronously here would recursively fire (and fully resolve) a
     * second damage event on the same target before this hit's own event has even
     * finished applying its damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void thunderlord(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof LivingEntity target) || target instanceof Player) {
            return;
        }
        int level = this.enchants.customLevel(attacker.getInventory().getItemInMainHand(), IcarusEnchant.THUNDERLORD);
        if (level <= 0) {
            return;
        }
        UUID attackerId = attacker.getUniqueId();
        UUID targetId = target.getUniqueId();
        ThunderlordState prev = this.thunderlordHits.get(attackerId);
        int hits = prev != null && prev.target().equals(targetId) ? prev.hits() + 1 : 1;
        if (hits < THUNDERLORD_HIT_INTERVAL) {
            this.thunderlordHits.put(attackerId, new ThunderlordState(targetId, hits));
            return;
        }
        this.thunderlordHits.put(attackerId, new ThunderlordState(targetId, 0));
        double bonus = e.getFinalDamage() * (0.08 * level);
        if (bonus <= 0.0) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (target.isValid() && !target.isDead()) {
                target.getWorld().strikeLightningEffect(target.getLocation());
                target.damage(bonus);
            }
        });
    }

    /**
     * Venomous: every melee hit against a non-player target adds a stack (capped at
     * {@value #VENOM_MAX_STACKS}, refreshing the whole debuff's {@value
     * #VENOM_DURATION_MILLIS}ms duration - same "every hit refreshes everything" shape
     * {@code CombatListener}'s own Lethality debuff uses) of a slow (a transient
     * {@link Attribute#MOVEMENT_SPEED} percentage reduction, capped at 90% so a
     * fully-stacked target is slowed, never fully frozen) and a damage-over-time tick
     * once per second, both scaled by {@code 0.3%-per-level * current stacks} - the DoT
     * is a percentage of the weapon's own known flat total (see {@code
     * SwordDamageService}), the same clean base {@code
     * CombatListener#applyMeleeEnchantBonus} reads from, rather than the noisy final
     * hit damage. A no-op for a weapon {@code SwordDamageService} doesn't recognize -
     * Venomous is sword-only ({@link IcarusEnchant}'s own doc), so this never has a
     * tool to fall back to the way {@code CombatListener#applyMeleeEnchantBonus} does
     * for Sharpness/Smite/Bane.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void venomousHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof LivingEntity target) || target instanceof Player) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        int level = this.enchants.customLevel(weapon, IcarusEnchant.VENOMOUS);
        if (level <= 0) {
            return;
        }
        Double base = SwordDamageService.totalDamage(weapon.getType());
        if (base == null) {
            return;
        }
        this.addVenomStack(target, level, base);
    }

    private void addVenomStack(LivingEntity target, int level, double weaponBase) {
        UUID id = target.getUniqueId();
        long now = System.currentTimeMillis();
        VenomState prev = this.venom.get(id);
        boolean fresh = prev == null || prev.expiry() <= now;
        int stacks = fresh ? 1 : Math.min(VENOM_MAX_STACKS, prev.stacks() + 1);
        double magnitude = level * 0.3;
        this.venom.put(id, new VenomState(stacks, magnitude, weaponBase, now + VENOM_DURATION_MILLIS));
        this.applyVenomSlow(target, stacks, magnitude);
        if (fresh) {
            this.startVenomTicker(target);
        }
    }

    /** Rewrites the slow modifier from scratch to match {@code stacks}/{@code magnitudePercent} right now - {@code stacks} 0 (or the debuff simply expiring) clears it entirely. */
    private void applyVenomSlow(LivingEntity target, int stacks, double magnitudePercent) {
        AttributeInstance speed = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier old = speed.getModifier(Key.key(this.venomSlowKey.getNamespace(), this.venomSlowKey.getKey()));
        if (old != null) {
            speed.removeModifier(old);
        }
        double fraction = Math.min(0.9, magnitudePercent * stacks / 100.0);
        if (fraction > 0.0) {
            speed.addTransientModifier(new AttributeModifier(this.venomSlowKey, -fraction, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    /** One repeating, self-cancelling tick loop per target - started only on a fresh debuff (see {@link #addVenomStack}) since later hits just update the same map entry this loop keeps re-reading, never spawning a second one. Cancels itself (and clears the slow) the moment the debuff's current state says it's expired, dead, or gone. */
    private void startVenomTicker(LivingEntity target) {
        UUID id = target.getUniqueId();
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            VenomState state = this.venom.get(id);
            if (state == null || state.expiry() <= System.currentTimeMillis() || !target.isValid() || target.isDead()) {
                this.venom.remove(id);
                this.applyVenomSlow(target, 0, 0.0);
                holder[0].cancel();
                return;
            }
            double perSecond = state.weaponBase() * (state.magnitudePercent() * state.stacks() / 100.0);
            if (perSecond > 0.0) {
                // Read health before/after (rather than showing perSecond itself) since
                // target.damage() here runs the real EntityDamageEvent pipeline to
                // completion - ArmorDefenseListener's Defense mitigation included -
                // before returning, so the theoretical perSecond amount and what the
                // target's health bar actually drops by can diverge for anything with
                // Defense (see MobVisualService#queueDamageNumber's own doc for the
                // same divergence on the event-handler side of this bug).
                double before = this.visuals.effectiveHealth(target);
                target.damage(perSecond);
                double actual = before - this.visuals.effectiveHealth(target);
                if (actual > 0.0) {
                    // Same dark green ElementalDamageListener gives real Poison damage -
                    // a DoT reads as "poison" at a glance regardless of which one dealt it.
                    this.visuals.damageNumber(target, actual, NamedTextColor.DARK_GREEN);
                }
            }
        }, 20L, 20L);
    }

    /** Experience: a percentage chance (12.5%/level) to double a hostile mob's dropped XP orbs. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void experienceOnKill(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || e.getDroppedExp() <= 0) {
            return;
        }
        int level = this.enchants.customLevel(p.getInventory().getItemInMainHand(), IcarusEnchant.EXPERIENCE);
        if (level <= 0 || ThreadLocalRandom.current().nextDouble() >= level * 0.125) {
            return;
        }
        e.setDroppedExp(e.getDroppedExp() * 2);
    }

    /** Experience's other half: the same chance to double an XP-dropping ore/block's own dropped orbs, read from whatever's in the breaking player's main hand - Experience is sword-only ({@link IcarusEnchant}'s own doc), so this only ever procs if that happens to be an enchanted sword rather than the pickaxe actually doing the mining. */
    @EventHandler(ignoreCancelled = true)
    public void experienceOnBreak(BlockBreakEvent e) {
        if (e.getExpToDrop() <= 0) {
            return;
        }
        int level = this.enchants.customLevel(e.getPlayer().getInventory().getItemInMainHand(), IcarusEnchant.EXPERIENCE);
        if (level <= 0 || ThreadLocalRandom.current().nextDouble() >= level * 0.125) {
            return;
        }
        e.setExpToDrop(e.getExpToDrop() * 2);
    }

    // Luck moved to CombatListener#rollEquipmentDrops - it now scales every
    // equipped slot's own drop chance (weapon included, not just armor) by the
    // same Looting/Luck multiplier formula instead of a standalone bonus roll.

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.thunderlordHits.remove(e.getPlayer().getUniqueId());
    }
}
