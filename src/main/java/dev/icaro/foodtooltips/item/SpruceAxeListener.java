package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
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

/** Wires the Spruce Axe's two effects: normal felling ({@link SpruceAxeService#chop}) on any log/stem break while holding it, and the throw ability ({@link #launch}, same {@code ArmorStand}-ray-march visual as {@code skills.SwordThrowListener} - see that method's own doc for why not a dropped {@code Item} or an {@code ItemDisplay}) on Swap Hands (F) - see {@link #throwAxe}'s own doc for why not right-click. {@link BedrockSpruceAxeThrowListener} triggers the same throw ({@link #attemptThrow}) via a double-crouch instead, for a Bedrock/Geyser player who can't reliably send - or, on a console controller, send at all - the F-key gesture. */
public final class SpruceAxeListener implements Listener {
    private static final long THROW_COOLDOWN_MILLIS = 1000L;
    /** Ray-march moves exactly 1 block/tick (see {@link #launch}), so this doubles as the thrown axe's max travel distance in blocks. */
    private static final int THROW_MAX_TICKS = 50;
    /**
     * {@code World#spawn}/{@code Entity#teleport} position an {@link ArmorStand} by its own
     * feet, not its head - a small stand's helmet-slot item actually renders roughly this many
     * blocks above that point in its default pose, so every spawn/teleport in {@link #launch}
     * subtracts it back off, keeping the visible axe lined up with the ray-march's own travel
     * point (starting at the player's own eye/hand height) instead of floating above it.
     */
    private static final double HEAD_HEIGHT_OFFSET = 1.2;
    /** One full tumble every 6 ticks (60°/tick) - a fast, clearly visible end-over-end spin for the thrown axe, applied via {@link ArmorStand#setHeadPose} since its equipped item has no spin animation of its own (unlike a dropped {@code Item}). */
    private static final double SPIN_RADIANS_PER_TICK = Math.PI / 3.0;

    private final Plugin plugin;
    private final SpruceAxeService axe;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public SpruceAxeListener(Plugin plugin, SpruceAxeService axe) {
        this.plugin = plugin;
        this.axe = axe;
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
            p.sendActionBar(Component.text(l.choose("Arremesso em recarga: ", "Throw cooldown: ")
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
            d.setSilent(true);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setCanMove(false);
            d.setCustomNameVisible(false);
            d.getEquipment().setHelmet(visual);
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
                if (block != null && block.getHitBlock() != null) {
                    Block hit = block.getHitBlock();
                    this.finish();
                    if (SpruceAxeService.isFellable(hit.getType())) {
                        SpruceAxeListener.this.axe.throwFell(p, hit);
                    }
                    return;
                }
                this.at.add(direction);
                display.teleport(this.at.clone().subtract(0, HEAD_HEIGHT_OFFSET, 0));
                display.setHeadPose(new EulerAngle(this.ticks * SPIN_RADIANS_PER_TICK, 0, 0));
            }

            private void finish() {
                if (display.isValid()) {
                    display.remove();
                }
                this.cancel();
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }
}
