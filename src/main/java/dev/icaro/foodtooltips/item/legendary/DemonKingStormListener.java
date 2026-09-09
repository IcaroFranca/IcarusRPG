package dev.icaro.foodtooltips.item.legendary;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

/**
 * Demon King's Longsword's "Storm of White Flames": same F-key trigger as {@code
 * SwordThrowListener} (ignored while sneaking, so it never collides with {@code
 * SkillsListener}'s sneak+swap shortcut), gated by its own cooldown and a flat Mana
 * cost rather than the combat tree. On activation, finds every living entity within
 * {@link #TARGET_SEARCH_RADIUS} of wherever the player is looking and strikes cosmetic
 * lightning directly on each one (up to {@link #STRIKE_COUNT} of them) - not random
 * points in the area, so every bolt actually lands on an enemy the player was aiming
 * at. Damage is dealt via {@link CombatAbilityService#dealAbilityDamage} so {@code
 * CombatListener} doesn't reprocess it through the melee multiplier stack, same as
 * Sword Throw.
 */
public final class DemonKingStormListener implements Listener {
    private static final long COOLDOWN_MILLIS = 30_000L;
    private static final int MANA_COST = 40;
    private static final int STRIKE_COUNT = 6;
    private static final double TARGET_SEARCH_RADIUS = 4.0;
    private static final double STRIKE_DAMAGE = 100.0;
    private static final double MAX_RANGE = 20.0;
    private static final long TICKS_BETWEEN_STRIKES = 4L;

    private final Plugin plugin;
    private final PlayerStatsService stats;
    private final CombatAbilityService abilities;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public DemonKingStormListener(Plugin plugin, PlayerStatsService stats, CombatAbilityService abilities) {
        this.plugin = plugin;
        this.stats = stats;
        this.abilities = abilities;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void activate(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (p.isSneaking()) {
            return;
        }
        if (this.attempt(p)) {
            e.setCancelled(true);
        }
    }

    /** True if {@code p} was even holding the Longsword (whether or not the cast actually fired) - lets the caller decide to cancel the swap-hands event either way. */
    public boolean attempt(Player p) {
        ItemStack weapon = p.getInventory().getItemInMainHand();
        if (LegendaryWeaponService.of(weapon) != LegendaryWeapon.DEMON_KING_LONGSWORD) {
            return false;
        }
        Language l = Language.of(p);
        long now = System.currentTimeMillis();
        long ready = this.cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < ready) {
            p.sendActionBar(Component.text(l.choose("Storm of White Flames em recarga: ", "Storm of White Flames cooldown: ")
                    + String.format(Locale.US, "%.1fs", (ready - now) / 1000.0), NamedTextColor.RED));
            return true;
        }
        if (!this.stats.withdrawMana(p, MANA_COST)) {
            p.sendActionBar(Component.text(l.choose("Mana insuficiente para Storm of White Flames.", "Not enough Mana for Storm of White Flames."), NamedTextColor.RED));
            return true;
        }
        this.cooldowns.put(p.getUniqueId(), now + COOLDOWN_MILLIS);
        this.cast(p);
        return true;
    }

    /** Every living entity (excluding {@code p} itself) within {@link #TARGET_SEARCH_RADIUS} of {@code origin}, closest first, capped at {@link #STRIKE_COUNT}. */
    private List<LivingEntity> nearbyTargets(Player p, Location origin, World world) {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity nearby : world.getNearbyEntities(origin, TARGET_SEARCH_RADIUS, TARGET_SEARCH_RADIUS, TARGET_SEARCH_RADIUS)) {
            if (nearby instanceof LivingEntity target && target != p) {
                targets.add(target);
            }
        }
        targets.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(origin), b.getLocation().distanceSquared(origin)));
        return targets.size() > STRIKE_COUNT ? targets.subList(0, STRIKE_COUNT) : targets;
    }

    private void cast(Player p) {
        Location origin = this.targetPoint(p);
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        List<LivingEntity> targets = this.nearbyTargets(p, origin, world);
        if (targets.isEmpty()) {
            // Nothing in the area the player was looking at - one cosmetic strike there
            // so the ability still gives feedback instead of silently doing nothing.
            world.strikeLightningEffect(origin);
            return;
        }
        new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                if (this.index >= targets.size() || !p.isOnline()) {
                    this.cancel();
                    return;
                }
                LivingEntity target = targets.get(this.index);
                if (target.isValid() && !target.isDead()) {
                    world.strikeLightningEffect(target.getLocation());
                    DemonKingStormListener.this.abilities.dealAbilityDamage(p, target, STRIKE_DAMAGE);
                }
                this.index++;
            }
        }.runTaskTimer(this.plugin, 0L, TICKS_BETWEEN_STRIKES);
    }

    /** Where the player is looking, up to {@link #MAX_RANGE} - the first block hit, or the max-range point if none. */
    private Location targetPoint(Player p) {
        Location eye = p.getEyeLocation();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), MAX_RANGE, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            return hit.getHitPosition().toLocation(p.getWorld());
        }
        return eye.clone().add(eye.getDirection().normalize().multiply(MAX_RANGE));
    }
}
