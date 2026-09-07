package dev.icaro.foodtooltips.item.legendary;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
 * cost rather than the combat tree. On activation, strikes cosmetic lightning at
 * several random points within a small radius of wherever the player is looking and
 * deals flat damage to any living entity caught near each strike - via {@link
 * CombatAbilityService#dealAbilityDamage} so {@code CombatListener} doesn't reprocess
 * it through the melee multiplier stack, same as Sword Throw.
 */
public final class DemonKingStormListener implements Listener {
    private static final long COOLDOWN_MILLIS = 30_000L;
    private static final int MANA_COST = 40;
    private static final int STRIKE_COUNT = 6;
    private static final double STRIKE_SPREAD_RADIUS = 4.0;
    private static final double STRIKE_DAMAGE_RADIUS = 2.5;
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

    private void cast(Player p) {
        Location origin = this.targetPoint(p);
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        new BukkitRunnable() {
            int strikes;

            @Override
            public void run() {
                if (this.strikes >= STRIKE_COUNT || !p.isOnline()) {
                    this.cancel();
                    return;
                }
                double dx = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * STRIKE_SPREAD_RADIUS;
                double dz = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * STRIKE_SPREAD_RADIUS;
                Location strikeLocation = origin.clone().add(dx, 0.0, dz);
                world.strikeLightningEffect(strikeLocation);
                for (Entity nearby : world.getNearbyEntities(strikeLocation, STRIKE_DAMAGE_RADIUS, STRIKE_DAMAGE_RADIUS, STRIKE_DAMAGE_RADIUS)) {
                    if (nearby instanceof LivingEntity target && target != p) {
                        DemonKingStormListener.this.abilities.dealAbilityDamage(p, target, STRIKE_DAMAGE);
                    }
                }
                this.strikes++;
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
