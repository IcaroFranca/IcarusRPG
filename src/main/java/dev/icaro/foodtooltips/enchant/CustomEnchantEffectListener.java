package dev.icaro.foodtooltips.enchant;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.plugin.Plugin;

/**
 * Wires up the actual gameplay effect for the plugin's custom enchants that need
 * one beyond a menu/lore entry - see {@link IcarusEnchant}'s own class doc for why
 * these four (Flame, Lure, Infinite Quiver, Luck of the Sea) exist as custom
 * entries instead of their vanilla counterparts. Also sets a flat base bow damage
 * (30), unrelated to any enchant, replacing vanilla's own draw-force-based number
 * the same way {@code SwordDamageService}/{@code ToolDamageService} give melee
 * weapons a flat total.
 *
 * <p>The bow-shoot hook (damage + Infinite Quiver's arrow-save roll) mirrors
 * vanilla's own Infinity implementation, which uses this exact same {@code
 * EntityShootBowEvent#setConsumeItem} flag. Flame's burn damage is dealt directly
 * ({@code LivingEntity#damage(double)}, no source) rather than through vanilla's own
 * fire-tick damage, which is a fixed 1 HP/second regardless of level and can't
 * express "Y% of your damage" - but the target IS set visually on fire ({@code
 * LivingEntity#setFireTicks}) for the same duration, so it still looks right. To
 * keep that purely visual (no double-dipping with vanilla's own fire-tick damage,
 * which {@code ElementalDamageListener} would also multiply by 5x), {@link
 * #fireTickDamage} cancels {@code EntityDamageEvent}s with cause {@code FIRE_TICK}
 * for exactly the entities and time window this class itself set on fire.
 *
 * <p>Luck of the Sea's extra treasure chance beyond vanilla's own level-3 cap is
 * re-implemented from scratch rather than faked by overleveling the vanilla
 * enchantment (which wouldn't scale further - see {@link IcarusEnchant}'s class
 * doc): {@link #fishCatch} rolls its own chance and, on a hit, replaces whatever
 * was caught with a fresh roll from vanilla's own {@code FISHING_TREASURE} loot
 * table, so the treasure items themselves (enchanted books, nautilus shells...)
 * still come straight from vanilla, only the odds of getting one are custom.
 */
public final class CustomEnchantEffectListener implements Listener {
    private static final double BASE_BOW_DAMAGE = 30.0;
    /** Flame's level 1/2 (duration seconds, damage % of the hit per second) - see IcarusEnchant's own doc for why this is a lookup, not a formula. */
    private static final double[] FLAME_DURATION = {0, 3.5, 4.0};
    private static final double[] FLAME_PERCENT = {0, 3, 6};

    private final Plugin plugin;
    private final EnchantService enchants;
    /** Entities currently on fire because of this class's own Flame effect, mapped to the real-time deadline (ms) their vanilla FIRE_TICK damage should stay suppressed until - see {@link #fireTickDamage}. */
    private final Map<UUID, Long> flameVisualUntil = new HashMap<>();

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

    /** Flame: schedules {@code duration} seconds of {@code percent}%-of-this-hit damage, read from whichever hand is holding the bow at hit time, and sets the target visually on fire for that same duration (see this class's own doc for how its damage stays vanilla-fire-tick-free). Runs at MONITOR so the damage read is CombatListener's final number, not the raw arrow damage. */
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
        int fireTicks = ticks * 20;
        target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        long deadline = System.currentTimeMillis() + (long) (FLAME_DURATION[lvl] * 1000);
        UUID targetId = target.getUniqueId();
        this.flameVisualUntil.merge(targetId, deadline, Math::max);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.flameVisualUntil.remove(targetId, deadline), fireTicks + 5L);
        for (int i = 1; i <= ticks; i++) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (target.isValid() && !target.isDead()) {
                    target.damage(perTick);
                }
            }, i * 20L);
        }
    }

    /** Suppresses vanilla's own FIRE_TICK damage (and ElementalDamageListener's 5x multiplier on it) for exactly the entities/time window {@link #arrowHit} itself set on fire, so the burn stays purely visual and {@code arrowHit}'s own scheduled damage is the only damage dealt. Runs at LOWEST, before ElementalDamageListener's LOW, so the cancellation is already in place by the time it checks. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fireTickDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK) {
            return;
        }
        Long until = this.flameVisualUntil.get(e.getEntity().getUniqueId());
        if (until != null && System.currentTimeMillis() < until) {
            e.setCancelled(true);
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

    /** Luck of the Sea: on a successful catch, rolls a 0.5%-per-level chance to replace whatever was caught with a fresh roll from vanilla's own fishing-treasure loot table (see this class's own doc for why this is reimplemented rather than overleveling the vanilla enchantment). */
    @EventHandler
    public void fishCatch(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item caughtItem)) {
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
        int level = this.enchants.levelOf(rod, new CustomEnchantEntry(IcarusEnchant.LUCK_OF_THE_SEA));
        if (level <= 0 || ThreadLocalRandom.current().nextDouble() >= level * 0.005) {
            return;
        }
        LootTable treasure = LootTables.FISHING_TREASURE.getLootTable();
        LootContext context = new LootContext.Builder(caughtItem.getLocation()).lootedEntity(p).killer(p).build();
        Collection<ItemStack> loot = treasure.populateLoot(ThreadLocalRandom.current(), context);
        if (!loot.isEmpty()) {
            caughtItem.setItemStack(loot.iterator().next());
        }
    }
}
