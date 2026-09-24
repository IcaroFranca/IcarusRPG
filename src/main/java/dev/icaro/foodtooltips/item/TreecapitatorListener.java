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
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Wires the Treecapitator's two effects: normal felling ({@link TreecapitatorService#chop}) on any log/stem break while holding it, and the throw ability ({@link #launch}) on Swap Hands (F) - same shape as {@code SpruceAxeListener}, see that class's own doc for why F, not right-click. {@link BedrockTreecapitatorThrowListener} triggers the same throw ({@link #attemptThrow}) via a double-crouch instead, for a Bedrock/Geyser player who can't reliably send - or, on a console controller, send at all - the F-key gesture. */
public final class TreecapitatorListener implements Listener {
    private static final long THROW_COOLDOWN_MILLIS = 1000L;
    /** Ray-march moves exactly 1 block/tick (see {@link #launch}), so this doubles as the thrown axe's max travel distance in blocks. */
    private static final int THROW_MAX_TICKS = 50;

    private final Plugin plugin;
    private final TreecapitatorService axe;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public TreecapitatorListener(Plugin plugin, TreecapitatorService axe) {
        this.plugin = plugin;
        this.axe = axe;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void chop(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (this.axe.isFellingActive(p) || !this.axe.isTreecapitator(p.getInventory().getItemInMainHand())) {
            return;
        }
        if (!TreecapitatorService.isFellable(e.getBlock().getType())) {
            return;
        }
        this.axe.chop(this.plugin, p, e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void throwAxe(PlayerSwapHandItemsEvent e) {
        if (this.attemptThrow(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** The actual throw, extracted so {@link BedrockTreecapitatorThrowListener} can trigger it too - see {@code SpruceAxeListener#attemptThrow}'s own doc for why. Returns whether {@code p} was even holding the Treecapitator (regardless of cooldown outcome), so either caller knows whether to cancel its own triggering event. */
    public boolean attemptThrow(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.axe.isTreecapitator(held)) {
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

    private void launch(Player p, ItemStack heldAxe) {
        Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6));
        Vector direction = p.getEyeLocation().getDirection().normalize();
        ItemDisplay display = p.getWorld().spawn(start, ItemDisplay.class, d -> {
            ItemStack visual = heldAxe.clone();
            visual.setAmount(1);
            d.setItemStack(visual);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(0.5f);
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
                    if (TreecapitatorService.isFellable(hit.getType())) {
                        TreecapitatorListener.this.axe.throwFell(p, hit);
                    }
                    return;
                }
                this.at.add(direction);
                display.teleport(this.at);
                float angle = (float) (this.ticks * Math.PI / 3.0);
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX(angle), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
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
