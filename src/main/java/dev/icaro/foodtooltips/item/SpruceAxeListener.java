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
import org.bukkit.entity.Item;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Wires the Spruce Axe's two effects: normal felling ({@link SpruceAxeService#chop}) on any log/stem break while holding it, and the throw ability ({@link #launch}, same dropped-{@code Item}-ray-march visual as {@code skills.SwordThrowListener} - see that method's own doc for why not an {@code ItemDisplay}) on Swap Hands (F) - see {@link #throwAxe}'s own doc for why not right-click. {@link BedrockSpruceAxeThrowListener} triggers the same throw ({@link #attemptThrow}) via a double-crouch instead, for a Bedrock/Geyser player who can't reliably send - or, on a console controller, send at all - the F-key gesture. */
public final class SpruceAxeListener implements Listener {
    private static final long THROW_COOLDOWN_MILLIS = 1000L;
    /** Ray-march moves exactly 1 block/tick (see {@link #launch}), so this doubles as the thrown axe's max travel distance in blocks. */
    private static final int THROW_MAX_TICKS = 50;

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
     * The thrown axe's visual - a plain dropped-{@link Item} entity (its own vanilla bob/spin
     * animation is free, no manual rotation needed), not an {@link org.bukkit.entity.ItemDisplay}
     * like this class's own first version. Display entities (added in 1.19.4) are still poorly
     * supported through Geyser for a Bedrock player - invisible outright on some versions
     * (GeyserMC/Geyser#3810, #5452), and even when visible, a per-tick {@code Entity#teleport}
     * like this ray-march needs often just doesn't visually move for them at all
     * (GeyserMC/Geyser#6723, "sliding display entity...updates...do not visually apply on
     * Bedrock") - exactly this ability's own symptom once the throw itself started working via
     * the double-crouch fix. A dropped item is one of the oldest, most universally-supported
     * entity types in the game, Bedrock included, so this trades a bit of visual flair (no
     * fixed-billboard orientation, no custom spin rate) for actually being seen at all.
     */
    private void launch(Player p, ItemStack heldAxe) {
        Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6));
        Vector direction = p.getEyeLocation().getDirection().normalize();
        ItemStack visual = heldAxe.clone();
        visual.setAmount(1);
        Item display = p.getWorld().spawn(start, Item.class, d -> {
            d.setItemStack(visual);
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setUnlimitedLifetime(true);
            d.setCanPlayerPickup(false);
            d.setCanMobPickup(false);
            d.setVelocity(new Vector(0, 0, 0));
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
                display.teleport(this.at);
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
