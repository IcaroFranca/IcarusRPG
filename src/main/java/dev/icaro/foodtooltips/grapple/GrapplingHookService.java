package dev.icaro.foodtooltips.grapple;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * A test build of the Grappling Hook the player designed with Codex (see that
 * conversation's own "Minha escolha para começar": hook on blocks only, ~24 block range,
 * a progressive pull rather than an instant teleport, sneak cancels, collision/fall
 * damage/swing modes left for later). Not tied to any Collection - per the player's own
 * "não vao entrar em collections, serão pegos só pelo /rpgitems", the only way to get one
 * is {@code LegendaryItemsMenuService}'s own admin menu, same as the Grand/Titanic
 * Experience Bottles.
 *
 * <p>Built on a plain {@link Material#FISHING_ROD} (the item this whole concept started
 * from) with every real fishing behavior cancelled by {@link
 * dev.icaro.foodtooltips.grapple.GrapplingHookListener}, which owns the interact wiring
 * and just calls into {@link #interact} - same "service holds the item and the ability,
 * listener only wires the event" split as {@code sponge.MegaSpongeService}/{@code
 * sponge.MegaSpongeListener}.
 *
 * <p>Triggered on Swap Hands (F), not right-click - {@code PlayerInteractEvent}'s {@code
 * RIGHT_CLICK_AIR} is the same long-standing, Spigot-acknowledged client-side limitation
 * {@code item.SpruceAxeListener#throwAxe} already documents: the client doesn't reliably
 * send the interact packet for a right-click with nothing (no block, no entity) within
 * normal reach, which is exactly the common case for a 24-block-range hook aimed at
 * something far away - confirmed by the player's own testing ("não aparecem as
 * partículas se eu não estiver mirando em um bloco"). Same fix {@code
 * skills.SwordThrowListener}/{@code item.SpruceAxeListener}/{@code
 * item.TreecapitatorListener} already use for their own ranged abilities.
 *
 * <p>Firing runs a one-block-per-tick raytrace (same ray-march shape {@code
 * skills.SwordThrowListener} uses for its own thrown sword) up to {@link #MAX_RANGE}
 * blocks; hitting a block attaches the hook there and starts drawing a rope (the same
 * one-shot {@link Particle#END_ROD} trail {@code item.FarmCrystalService#beamEffect}/
 * {@code skills.AccessoryBagService#beamEffect} already use, just redrawn every {@link
 * #ROPE_INTERVAL_TICKS}). The next Swap Hands press while attached starts the pull - not
 * a single velocity burst, but the same impulse reapplied every tick for up to {@link
 * #PULL_MAX_TICKS} (or until close enough to stop), which is what makes it feel like a
 * sustained reel-in rather than a teleport, per the player's own "puxão progressivo"
 * spec. Sneaking at any point (flying, attached, or mid-pull) cancels everything - see
 * {@link #cancel}, called from the listener's own sneak/quit handlers.
 *
 * <p>Deliberately does not: pull entities, hook onto anything but a solid block, prevent
 * fall damage after a pull, or offer a Swing/Reel mode - all explicitly left for later
 * once this base version has been tested in-game.
 */
public final class GrapplingHookService {
    private static final int MAX_RANGE = 24;
    private static final long COOLDOWN_MILLIS = 3000L;
    private static final int PULL_MAX_TICKS = 20;
    private static final double PULL_STOP_DISTANCE = 2.0;
    /** How long an attached hook waits for a pull before releasing itself (5s) - a safety valve so a forgotten hook doesn't leave its rope task running forever. */
    private static final int ATTACHED_TIMEOUT_TICKS = 100;
    private static final int ROPE_INTERVAL_TICKS = 2;
    private static final double BEAM_PARTICLE_SPACING = 0.3;

    private enum Phase { FLYING, ATTACHED, PULLING }

    private static final class HookState {
        Phase phase;
        Location location;
        BukkitTask task;

        HookState(Phase phase) {
            this.phase = phase;
        }
    }

    private final Plugin plugin;
    private final NamespacedKey hookKey;
    private final Map<UUID, HookState> active = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public GrapplingHookService(Plugin plugin) {
        this.plugin = plugin;
        this.hookKey = new NamespacedKey(plugin, "grappling_hook");
    }

    public ItemStack create(Language l) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.hookKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(this.line("Grappling Hook", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true));
        List<Component> lore = List.of(
                this.line("Swap Hands (F): fires the hook.", NamedTextColor.GRAY),
                this.line("Swap Hands again: pulls you to it.", NamedTextColor.GRAY),
                this.line("Sneak to cancel the hook.", NamedTextColor.GRAY),
                Component.empty(),
                this.line("Range: 24 blocks", NamedTextColor.AQUA),
                this.line("Cooldown: 3 seconds", NamedTextColor.AQUA));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGrapplingHook(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.hookKey, PersistentDataType.BYTE);
    }

    /** {@code GrapplingHookListener}'s own entry point on every right-click while holding the hook - either fires a new one, starts the pull if one's already attached, or does nothing while a hook is still flying/pulling. */
    public void interact(Player p) {
        UUID id = p.getUniqueId();
        HookState state = this.active.get(id);
        if (state != null) {
            if (state.phase == Phase.ATTACHED) {
                this.beginPull(p, state);
            }
            return;
        }
        long now = System.currentTimeMillis();
        long ready = this.cooldowns.getOrDefault(id, 0L);
        if (now < ready) {
            Language l = Language.of(p);
            p.sendActionBar(this.line("Hook cooldown: "
                    + String.format(Locale.US, "%.1fs", (ready - now) / 1000.0), NamedTextColor.RED));
            return;
        }
        this.cooldowns.put(id, now + COOLDOWN_MILLIS);
        this.launch(p);
    }

    /** Cancels whatever {@code p}'s hook is currently doing (flying, attached or mid-pull) - called from the listener on sneak and on quit. */
    public void cancel(Player p) {
        HookState state = this.active.remove(p.getUniqueId());
        if (state != null && state.task != null) {
            state.task.cancel();
        }
    }

    private void launch(Player p) {
        UUID id = p.getUniqueId();
        HookState state = new HookState(Phase.FLYING);
        this.active.put(id, state);
        Location eye = p.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location at = eye.clone();
        p.getWorld().playSound(eye, Sound.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.6f, 1.4f);
        state.task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || GrapplingHookService.this.active.get(id) != state) {
                    this.cancel();
                    return;
                }
                if (this.ticks++ >= MAX_RANGE) {
                    GrapplingHookService.this.active.remove(id);
                    Language l = Language.of(p);
                    // A chat message, not an actionbar one - StatsHudService's own HP/Mana
                    // HUD overwrites the actionbar every 5 ticks (see FoodTooltipsPlugin's
                    // master per-player sweep), so anything sent there gets blotted out
                    // within a quarter second and is effectively invisible in practice.
                    p.sendMessage(GrapplingHookService.this.line(
                            "Hook found nothing to grab.", NamedTextColor.RED));
                    this.cancel();
                    return;
                }
                RayTraceResult hit = p.getWorld().rayTraceBlocks(at, direction, 1.0, FluidCollisionMode.NEVER, true);
                if (hit != null && hit.getHitBlock() != null) {
                    GrapplingHookService.this.attach(p, state, hit.getHitBlock().getLocation());
                    this.cancel();
                    return;
                }
                at.add(direction);
                GrapplingHookService.this.beamEffect(eye, at);
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    private void attach(Player p, HookState state, Location blockLocation) {
        state.phase = Phase.ATTACHED;
        state.location = blockLocation;
        UUID id = p.getUniqueId();
        p.getWorld().playSound(blockLocation, Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.PLAYERS, 0.8f, 0.7f);
        // A small burst (not just the thin line toward the player) so it's visible even
        // head-on, looking straight down the rope where a single-file particle line all
        // but disappears into one point from the shooter's own point of view.
        p.getWorld().spawnParticle(Particle.END_ROD, blockLocation.clone().add(0.5, 0.5, 0.5), 12, 0.2, 0.2, 0.2, 0.02);
        // Chat, not actionbar - see the miss message's own doc in #launch on why.
        Language l = Language.of(p);
        p.sendMessage(this.line("Hook attached! Right-click again to pull.", NamedTextColor.GREEN));
        state.task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                this.ticks += ROPE_INTERVAL_TICKS;
                if (!p.isOnline() || GrapplingHookService.this.active.get(id) != state || this.ticks >= ATTACHED_TIMEOUT_TICKS) {
                    GrapplingHookService.this.active.remove(id);
                    this.cancel();
                    return;
                }
                GrapplingHookService.this.beamEffect(p.getEyeLocation(), blockLocation.clone().add(0.5, 0.5, 0.5));
            }
        }.runTaskTimer(this.plugin, 0L, ROPE_INTERVAL_TICKS);
    }

    private void beginPull(Player p, HookState state) {
        if (state.task != null) {
            state.task.cancel();
        }
        state.phase = Phase.PULLING;
        Location hookCenter = state.location.clone().add(0.5, 0.5, 0.5);
        UUID id = p.getUniqueId();
        // A flying Creative/Survival player's own client-side flight movement overrides
        // any server-set velocity almost immediately, which would make the pull look like
        // it does nothing at all - the exact symptom the player reported testing with. A
        // Spectator has no physical body for setVelocity to move in the first place, so
        // there's nothing to turn off for them.
        if (p.isFlying() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            p.setFlying(false);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 0.8f, 1.0f);
        state.task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || GrapplingHookService.this.active.get(id) != state) {
                    this.cancel();
                    return;
                }
                Vector toHook = hookCenter.toVector().subtract(p.getLocation().toVector());
                double distance = toHook.length();
                if (distance < PULL_STOP_DISTANCE || this.ticks++ >= PULL_MAX_TICKS) {
                    GrapplingHookService.this.active.remove(id);
                    this.cancel();
                    return;
                }
                Vector direction = toHook.normalize();
                double strength = Math.min(2.2, 0.45 + distance * 0.08);
                Vector velocity = direction.multiply(strength);
                velocity.setY(Math.max(velocity.getY(), 0.35));
                p.setVelocity(velocity);
                GrapplingHookService.this.beamEffect(p.getEyeLocation(), hookCenter);
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    /** Same one-shot {@link Particle#END_ROD} trail {@code item.FarmCrystalService#beamEffect} draws, redrawn periodically by {@link #attach}/{@link #beginPull} to look like a rope rather than a single flash. */
    private void beamEffect(Location from, Location to) {
        World world = from.getWorld();
        double distance = from.distance(to);
        int steps = Math.max(1, (int) (distance / BEAM_PARTICLE_SPACING));
        double dx = (to.getX() - from.getX()) / steps;
        double dy = (to.getY() - from.getY()) / steps;
        double dz = (to.getZ() - from.getZ()) / steps;
        for (int i = 0; i <= steps; i++) {
            world.spawnParticle(Particle.END_ROD, from.getX() + dx * i, from.getY() + dy * i, from.getZ() + dz * i, 1, 0, 0, 0, 0);
        }
    }

    private Component line(String s, TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
