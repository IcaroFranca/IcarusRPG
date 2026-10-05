package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Pitcher Pod Collection M5 - per the player's own exact spec: a "vara mágica" that throws a
 * homing ("teleguiado") Pitcher Pod, rooting the first {@link Enemy} it hits in place for
 * {@value #ROOT_SECONDS} seconds (a judgment call - the player's own spec gave the cost and
 * cooldown but not a duration), costing {@value #MANA_COST} Mana with a {@value
 * #COOLDOWN_SECONDS}-second cooldown. Represented by a plain, enchanted-looking {@link
 * Material#PITCHER_POD} ({@link ItemMeta#setEnchantmentGlintOverride}, same "no real
 * enchantment, just the glint" trick {@code biome.BiomeWandService}'s own wand already uses) -
 * no {@link ItemTier} forced, matching every other admin-utility wand in this plugin
 * (BuilderWand, DestroyerHand, Biome's Wand) that isn't itself a weapon.
 *
 * <p>Triggered on Swap Hands (F), not right-click - this plugin's own now-established fix for
 * {@code PlayerInteractEvent}'s {@code RIGHT_CLICK_AIR} not reliably firing with nothing in
 * reach (see {@code item.AspectOfTheEndService}'s own doc on the exact same bug, applied here
 * proactively since this ability's whole point is hitting something far away).
 *
 * <p>The homing projectile's own visual is an invisible, held-still {@link ArmorStand} wearing
 * a Pitcher Pod as its helmet, ray-marched one block per tick and spinning around its own
 * vertical axis as it flies - per the player's own explicit "quero o arremesso da pitcher wand
 * igual o arremesso do machado, mas teleguiado", the exact same visual {@code
 * item.SpruceAxeListener#launch} uses for its own thrown axe (an {@code ArmorStand}'s equipped
 * item avoids both a dropped {@link org.bukkit.entity.Item}'s own vanilla bob/spin animation and
 * {@code ItemDisplay}'s known Geyser/Bedrock rendering gaps - see that method's own doc), down to
 * the same {@link #SPIN_RADIANS_PER_TICK} "coin spinning on a table" vertical-axis spin rather
 * than {@code skills.SwordThrowListener}'s own forward-tumbling one. Unlike the axe's own
 * straight-line throw, {@link #launch} re-aims toward the nearest {@link Enemy} within {@value
 * #HOMING_RANGE} blocks every tick (same "redirect velocity/direction toward the nearest target,
 * preserving whatever's already in flight" idea {@code
 * enchant.BowEnchantEffectListener#startHoming} uses for the Aiming enchant - not reused directly
 * since that method is typed to a real {@link org.bukkit.entity.AbstractArrow}, and this
 * projectile is the {@code ArmorStand} visual above, not a real arrow) - that's the "teleguiado"
 * half of the player's own spec.
 *
 * <p>Rooting ({@link #root}) only zeroes the target's own horizontal velocity every tick for
 * the duration (vertical velocity left alone so a mid-air target still falls normally) rather
 * than freezing its AI outright ({@link org.bukkit.entity.Mob#setAware}) or teleporting it back
 * in place - a rooted mob can still turn and swing at whoever's attacking it, just can't walk
 * or be knocked away, matching "enraizado" (rooted) rather than a full stun. Crafted from 8
 * Pitcher Pod Core and a Stick ({@code item.FarmingCollectionsItemsService}'s own recipe),
 * unlocked at Pitcher Pod Collection M5 - right after the Core itself (M4), per the player's
 * own explicit spec.
 */
public final class PitcherWandService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "pitcher_wand");
    /** Tags the visual {@code ArmorStand} {@link #launch} spawns - same reason {@code skills.SwordThrowListener}'s own identical key exists (a flying decorative stand must never be mistaken for a valid target, including by a different player's concurrent cast). */
    private static final NamespacedKey THROWN_VISUAL_KEY = new NamespacedKey("foodtooltips", "thrown_pitcher_wand_visual");
    public static final int MANA_COST = 50;
    public static final int COOLDOWN_SECONDS = 10;
    private static final long COOLDOWN_MILLIS = COOLDOWN_SECONDS * 1000L;
    /** Judgment call - the player's own spec gave Mana cost and cooldown but not how long the root itself lasts. */
    public static final int ROOT_SECONDS = 3;
    private static final int ROOT_DURATION_TICKS = ROOT_SECONDS * 20;
    /** How far {@link #launch} looks for a target to redirect toward every tick - wide enough to lock on well before the projectile would otherwise fly past a nearby enemy. */
    private static final double HOMING_RANGE = 20.0;
    /** Flight time cap, in ticks, same safety-valve purpose as {@code enchant.BowEnchantEffectListener#AIMING_MAX_TICKS} - a bolt that never finds a wall or a target stops existing instead of flying forever. */
    private static final int MAX_TRAVEL_TICKS = 60;
    private static final double HEAD_HEIGHT_OFFSET = 0.889;
    /** Same vertical-axis "coin spinning on a table" spin {@code item.SpruceAxeListener}'s own identical constant uses - see this class's own doc. */
    private static final double SPIN_RADIANS_PER_TICK = Math.PI / 3.0;

    private final Plugin plugin;
    private final PlayerStatsService stats;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public PitcherWandService(Plugin plugin, PlayerStatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.PITCHER_POD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Pitcher Wand", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        meta.lore(List.of(
                Component.text("Ability: Root ", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("SWAP HANDS", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false)),
                Component.text("Throws a homing Pitcher Pod that roots", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("the first enemy it hits in place for", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(ROOT_SECONDS + " seconds.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Mana Cost: " + MANA_COST, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false),
                Component.text("Cooldown: " + COOLDOWN_SECONDS + "s", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isPitcherWand(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void swapHands(PlayerSwapHandItemsEvent e) {
        if (this.attemptCast(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** Returns whether {@code p} was even holding the Pitcher Wand (regardless of Mana/cooldown outcome), so {@link #swapHands} knows whether to cancel its own triggering event - same shape {@code item.SpruceAxeListener#attemptThrow} already uses. */
    public boolean attemptCast(Player p) {
        if (!isPitcherWand(p.getInventory().getItemInMainHand())) {
            return false;
        }
        long now = System.currentTimeMillis();
        long ready = this.cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < ready) {
            p.sendActionBar(Component.text("Root cooldown: "
                    + String.format(Locale.US, "%.1fs", (ready - now) / 1000.0), (TextColor) NamedTextColor.RED));
            return true;
        }
        if (!this.stats.withdrawMana(p, MANA_COST)) {
            p.sendActionBar(Component.text("Not enough Mana to cast Root.", NamedTextColor.RED));
            return true;
        }
        this.cooldowns.put(p.getUniqueId(), now + COOLDOWN_MILLIS);
        this.launch(p);
        return true;
    }

    private void launch(final Player p) {
        final Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6));
        final Vector[] direction = {p.getEyeLocation().getDirection().normalize()};
        ItemStack visual = new ItemStack(Material.PITCHER_POD);
        final ArmorStand display = p.getWorld().spawn(start.clone().subtract(0, HEAD_HEIGHT_OFFSET, 0), ArmorStand.class, d -> {
            d.setInvisible(true);
            d.setGravity(false);
            d.setBasePlate(false);
            d.setArms(false);
            d.setSmall(true);
            d.setMarker(false);
            d.setSilent(true);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setCanMove(false);
            d.setCustomNameVisible(false);
            d.getEquipment().setHelmet(visual);
            d.getPersistentDataContainer().set(THROWN_VISUAL_KEY, PersistentDataType.BYTE, (byte) 1);
            d.setHeadPose(new EulerAngle(0, 0, 0));
        });
        final Predicate<Entity> targetable = entity -> entity instanceof Enemy enemy && enemy.isValid() && !enemy.isDead()
                && !entity.getPersistentDataContainer().has(THROWN_VISUAL_KEY, PersistentDataType.BYTE);
        new BukkitRunnable() {
            int ticks;
            final Location at = start.clone();

            @Override
            public void run() {
                if (!p.isOnline() || !display.isValid() || this.ticks++ >= MAX_TRAVEL_TICKS) {
                    this.finish();
                    return;
                }
                LivingEntity nearest = nearestEnemy(this.at, HOMING_RANGE);
                if (nearest != null) {
                    Vector toTarget = nearest.getEyeLocation().toVector().subtract(this.at.toVector());
                    if (toTarget.lengthSquared() > 1.0E-4) {
                        direction[0] = toTarget.normalize();
                    }
                }
                RayTraceResult block = p.getWorld().rayTraceBlocks(this.at, direction[0], 1.0, FluidCollisionMode.NEVER, true);
                RayTraceResult hit = p.getWorld().rayTraceEntities(this.at, direction[0], 1.0, 0.65, targetable);
                if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                    PitcherWandService.this.root(target);
                    this.finish();
                    return;
                }
                if (block != null) {
                    this.finish();
                    return;
                }
                this.at.add(direction[0]);
                display.teleport(this.at.clone().subtract(0, HEAD_HEIGHT_OFFSET, 0));
                display.setHeadPose(new EulerAngle(0, this.ticks * SPIN_RADIANS_PER_TICK, 0));
            }

            private void finish() {
                if (display.isValid()) {
                    display.remove();
                }
                this.cancel();
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    /** The closest {@link Enemy} within {@code range} blocks of {@code from} - deliberately simpler than {@code enchant.BowEnchantEffectListener#nearestEnemy}'s own line-of-sight-checked version, since this is a single player-aimed cast (not a stray arrow hunting anything in a wide cone): a lock through thin cover just has the bolt redirect and then stop at the very next block it actually hits, not a real exploit. {@link EntityType#ZOMBIFIED_PIGLIN} is still excluded, same "neutral unless provoked" exception {@code enchant.BowEnchantEffectListener}'s own identical check already carves out, for the same reason. */
    private static LivingEntity nearestEnemy(Location from, double range) {
        LivingEntity nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Entity nearby : from.getWorld().getNearbyEntities(from, range, range, range)) {
            if (!(nearby instanceof Enemy enemy) || !enemy.isValid() || enemy.isDead() || enemy.getType() == EntityType.ZOMBIFIED_PIGLIN) {
                continue;
            }
            double distanceSquared = enemy.getLocation().distanceSquared(from);
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = enemy;
            }
        }
        return nearest;
    }

    /** Zeroes {@code target}'s own horizontal velocity every tick for {@value #ROOT_SECONDS} seconds - see this class's own doc on why this, not {@code Mob#setAware}/teleport-pinning. */
    private void root(LivingEntity target) {
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead() || this.ticks++ >= ROOT_DURATION_TICKS) {
                    this.cancel();
                    return;
                }
                Vector v = target.getVelocity();
                target.setVelocity(new Vector(0.0, Math.min(0.0, v.getY()), 0.0));
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.cooldowns.remove(e.getPlayer().getUniqueId());
    }
}
