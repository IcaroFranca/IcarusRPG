package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.util.RightClickTrigger;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Wires the Spruce Axe's two effects: normal felling ({@link SpruceAxeService#chop}) on any log/stem break while holding it, and the throw ability ({@link #launch}, same {@code ArmorStand}-ray-march visual as {@code skills.SwordThrowListener} - see that method's own doc for why not a dropped {@code Item} or an {@code ItemDisplay}) on Swap Hands (F) - see {@link #throwAxe}'s own doc for why not right-click. Also triggers on a real right-click ({@link #start}) when {@code ProtocolLib} is installed, via the same {@code util.RightClickTrigger} every other Swap-Hands ability in this plugin now uses. {@link #launch}'s own thrown axe trails {@link Particle#CRIT} particles (the same critical-hit "star" sparkles, per the player's own request) the whole way. {@link BedrockSpruceAxeThrowListener} triggers the same throw ({@link #attemptThrow}) via a double-crouch instead, for a Bedrock/Geyser player who can't reliably send - or, on a console controller, send at all - the F-key gesture. */
public final class SpruceAxeListener implements Listener {
    private static final long THROW_COOLDOWN_MILLIS = 1000L;
    /** Ray-march moves exactly 1 block/tick (see {@link #launch}), so this doubles as the thrown axe's max travel distance in blocks. */
    private static final int THROW_MAX_TICKS = 50;
    /**
     * {@code World#spawn}/{@code Entity#teleport} position an {@link ArmorStand} by its own
     * feet, not its head - a small stand's own standing eye height (vanilla's real formula,
     * {@code ArmorStand#getStandingEyeHeight}: {@code height * 0.9}, and a small stand's own
     * height is exactly half a full-size one's 1.975 - see the Minecraft Wiki's own Armor
     * Stand hitbox dimensions) puts its helmet-slot item about {@code 0.9875 * 0.9 ≈ 0.889}
     * blocks above that spawn point in its default pose. Every spawn/teleport in {@link
     * #launch} subtracts this back off, keeping the visible axe lined up with the ray-march's
     * own travel point (starting at the player's own eye/hand height) instead of floating
     * above it. Mojang's own MC-107156 documents the small-stand formula as slightly
     * inconsistent across versions - nudge this a little if it's ever visibly off.
     */
    private static final double HEAD_HEIGHT_OFFSET = 0.889;
    /**
     * headPose's Y component rotates the held item around the head bone's own original
     * up/down (vertical) axis - a plain yaw spin, the same one that {@code
     * start.setDirection} already uses for the {@link ArmorStand}'s own body. Animating it
     * continuously (see {@link #launch}) spins the axe like a coin standing on a table -
     * "gira em torno do próprio eixo... como o planeta Terra faz para ter o ciclo de dia e
     * noite" was the player's own exact spec for this: a fixed rotation axis through the
     * item's own center, completely decoupled from {@link #launch}'s own straight-line
     * {@code at.add(direction)} travel (a vertical-axis rotation never moves the pivot it
     * rotates around, so there's nothing for it to visually displace). Two earlier versions
     * of this both rotated around the axe's own HANDLE-TO-BLADE axis instead (first X, then,
     * when that "vanished edge-on periodically", Z) - correct in the narrow sense of "spins
     * in place, no precession", but an axe head sits offset to one side of that axis (unlike
     * a spear or arrow, which really is just a thin line end to end), so spinning around it
     * swings the visible blade through a wide circle every rotation - indistinguishable, to
     * a player watching it fly, from the whole throw corkscrewing/orbiting around the aim
     * direction, which is exactly what got reported a second time. Spinning around the
     * vertical axis instead has no such offset blade-sweep: the item just cycles between
     * face-on and edge-on as it turns, the same "day/night" cycle the player's own analogy
     * describes, while {@link #launch}'s own position update stays a perfectly straight line
     * the whole time either way.
     */
    private static final double SPIN_RADIANS_PER_TICK = Math.PI / 3.0;

    private final Plugin plugin;
    private final SpruceAxeService axe;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public SpruceAxeListener(Plugin plugin, SpruceAxeService axe) {
        this.plugin = plugin;
        this.axe = axe;
    }

    /** Call once from {@code FoodTooltipsPlugin#onEnable} - registers the raw-packet right-click trigger via {@code util.RightClickTrigger} if {@code ProtocolLib} is installed, a no-op otherwise (the throw stays Swap-Hands-only, same as before this was added). */
    public void start() {
        RightClickTrigger.registerIfAvailable(this.plugin,
                p -> this.axe.isSpruceAxe(p.getInventory().getItemInMainHand()), this::attemptThrow);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void chop(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (this.axe.isFellingActive(p) || !this.axe.isSpruceAxe(p.getInventory().getItemInMainHand())) {
            return;
        }
        if (!SpruceAxeService.isFellable(e.getBlock().getType())) {
            return;
        }
        this.axe.chop(this.plugin, p, e.getBlock());
    }

    /**
     * Swap Hands (F), not right-click: {@code PlayerInteractEvent}'s {@code
     * RIGHT_CLICK_AIR} is a long-standing, Spigot-acknowledged "intended, no workaround"
     * client-side limitation - the client simply doesn't reliably send the interact
     * packet for a right-click with nothing (no block, no entity) within reach - which
     * would make a ranged throw ability effectively only usable at melee range, the
     * opposite of the point. Same fix {@code skills.SwordThrowListener} already uses for
     * its own throw, for the exact same reason (see that class's own {@code
     * throwSword}). {@code priority = HIGH, ignoreCancelled = true} mirrors that class
     * too - and every other "must always fire" item-ability listener in this plugin
     * ({@code BuilderWandListener}, {@code DestroyerHandListener}, {@code
     * BiomeWandListener}, {@code BrewingMenuListener}) - so a protection plugin's own
     * cancellation (WorldGuard, a soft-depend of this plugin, most commonly) can't
     * silently swallow this ability either. Cancels the event (so a normal hand-swap
     * never sneaks through) whenever the Spruce Axe is held, regardless of cooldown -
     * only lets it fall through to vanilla's own hand-swap when a different item is held.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void throwAxe(PlayerSwapHandItemsEvent e) {
        if (this.attemptThrow(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** The actual throw, extracted so {@link BedrockSpruceAxeThrowListener} can trigger it too - a Bedrock/Geyser client (a PS4 controller especially, with no "swap hands" input to send at all) can't rely on {@link #throwAxe}'s own F-key gesture, same reasoning {@code skills.SwordThrowListener#attemptThrow} already documents for its own Bedrock fallback. Returns whether {@code p} was even holding the Spruce Axe (regardless of cooldown outcome), so either caller knows whether to cancel its own triggering event. */
    public boolean attemptThrow(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.axe.isSpruceAxe(held)) {
            return false;
        }
        Language l = Language.of(p);
        long now = System.currentTimeMillis();
        long ready = this.cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < ready) {
            p.sendActionBar(Component.text("Throw cooldown: "
                    + String.format(Locale.US, "%.1fs", (ready - now) / 1000.0), (TextColor) NamedTextColor.RED));
            return true;
        }
        this.cooldowns.put(p.getUniqueId(), now + THROW_COOLDOWN_MILLIS);
        this.launch(p, held);
        return true;
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.cooldowns.remove(e.getPlayer().getUniqueId());
    }

    /**
     * The thrown axe's visual - an invisible, held-still {@link ArmorStand} wearing {@code
     * heldAxe} as its helmet, not a dropped {@link org.bukkit.entity.Item} (this class's own
     * second version) or an {@link org.bukkit.entity.ItemDisplay} (its first). A dropped item
     * carries its own vanilla bob/spin animation, baked into its client-side rendering
     * independent of this ray-march's own per-tick reposition - looking like it's wobbling in
     * place instead of flying cleanly to its target, exactly what "ele só aparece no ar de um
     * jeito estranho" describes. An {@code ItemDisplay} has no such animation, but Display
     * entities (added in 1.19.4) are still poorly supported through Geyser for a Bedrock
     * player - invisible outright on some versions (GeyserMC/Geyser#3810, #5452), and even
     * when visible, a per-tick {@code Entity#teleport} like this ray-march needs often just
     * doesn't visually move for them at all (GeyserMC/Geyser#6723). An {@code ArmorStand}'s
     * equipped item has neither problem: no bob/spin animation of its own, and ArmorStands are
     * one of the oldest, most universally-supported entity types in the game, Bedrock
     * included - {@code setMarker(false)} deliberately, not {@code true}, since Marker mode
     * had its own now-fixed-upstream Geyser bug hiding equipped items entirely
     * (GeyserMC/Geyser#3089) - not worth the risk on an older/pinned Geyser build when a
     * regular (non-marker) invisible stand works everywhere.
     */
    private void launch(Player p, ItemStack heldAxe) {
        Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6));
        Vector direction = p.getEyeLocation().getDirection().normalize();
        start.setDirection(direction);
        ItemStack visual = heldAxe.clone();
        visual.setAmount(1);
        ArmorStand display = p.getWorld().spawn(start.clone().subtract(0, HEAD_HEIGHT_OFFSET, 0), ArmorStand.class, d -> {
            d.setInvisible(true);
            d.setGravity(false);
            d.setBasePlate(false);
            d.setArms(false);
            d.setSmall(true);
            d.setMarker(false);
            d.setCollidable(false);
            d.setSilent(true);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setCanMove(false);
            d.setCustomNameVisible(false);
            d.getEquipment().setHelmet(visual);
            d.setHeadPose(new EulerAngle(0, 0, 0));
        });
        new BukkitRunnable() {
            int ticks;
            Location at = start.clone();

            @Override
            public void run() {
                if (!p.isOnline() || !display.isValid() || this.ticks++ >= THROW_MAX_TICKS) {
                    this.finish();
                    return;
                }
                RayTraceResult block = p.getWorld().rayTraceBlocks(this.at, direction, 1.0, FluidCollisionMode.NEVER, true);
                if (block != null && block.getHitBlock() != null && !isLeaves(block.getHitBlock().getType())) {
                    Block hit = block.getHitBlock();
                    this.finish();
                    if (SpruceAxeService.isFellable(hit.getType())) {
                        SpruceAxeListener.this.axe.throwFell(p, hit);
                    }
                    return;
                }
                this.at.add(direction);
                display.teleport(this.at.clone().subtract(0, HEAD_HEIGHT_OFFSET, 0));
                display.setHeadPose(new EulerAngle(0, this.ticks * SPIN_RADIANS_PER_TICK, 0));
                p.getWorld().spawnParticle(Particle.CRIT, this.at, 4, 0.08, 0.08, 0.08, 0.02);
            }

            private void finish() {
                if (display.isValid()) {
                    display.remove();
                }
                this.cancel();
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    /** Whether {@code type} is any of the game's leaf blocks - the thrown axe passes straight through these (per the player's own explicit "atravessar folhas" spec) instead of stopping on the first one it clashes with, since a tree's own canopy would otherwise block a throw aimed at the trunk behind it. Every vanilla leaf {@link Material} name ends in {@code _LEAVES}. */
    private static boolean isLeaves(Material type) {
        return type.name().endsWith("_LEAVES");
    }
}
