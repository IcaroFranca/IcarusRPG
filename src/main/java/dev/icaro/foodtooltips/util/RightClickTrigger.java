package dev.icaro.foodtooltips.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Shared "make a Swap-Hands-only ability also fire on a real right-click" registration, first
 * built for {@code item.PitcherWandService} and extracted here once the same request came in
 * for every other Swap-Hands ability in the plugin. Listens for the raw {@code
 * PacketType.Play.Client.USE_ITEM}/{@code USE_ITEM_ON} packets directly via ProtocolLib - one
 * layer below Bukkit's own {@code PlayerInteractEvent} translation, which an earlier attempt
 * (tagging the item with a {@code DataComponentTypes.CONSUMABLE} data component purely to force
 * the client to always send the interact packet) ran into: on the player's own server, that
 * event simply never fired for the resulting click, confirmed with temporary debug logging -
 * whatever packet the client was sending wasn't reaching Bukkit's usual interact dispatch there.
 * Reading the raw packet instead doesn't depend on that translation layer recognizing the click
 * at all.
 *
 * <p>ProtocolLib is a soft-depend (same runtime presence check {@code
 * creaking.CreakingSightService} already uses for PacketEvents) - {@link #registerIfAvailable}
 * is a no-op returning {@code false} when it isn't installed, leaving the ability exactly as
 * Swap-Hands-only as it already was; every call site keeps its own Swap Hands listener
 * registered regardless, so there's no regression either way, and no risk of a double-cast when
 * both fire (different physical inputs, never triggered by the same keypress).
 *
 * <p>{@code onPacketReceiving} itself runs on ProtocolLib's Netty thread, not the main server
 * thread - the exact bug that first showed up on the Pitcher Wand as Mana/cooldown draining
 * normally (plain in-memory map writes, thread-agnostic) while the actual ability visual
 * silently never ran (spawning an {@code ArmorStand} off the main thread either throws or
 * no-ops, swallowed inside ProtocolLib's own listener dispatch instead of reaching the normal
 * console). {@link #registerIfAvailable} only ever does the cheap {@code shouldHandle} check on
 * that thread and hops onto the main thread via {@link
 * org.bukkit.scheduler.BukkitScheduler#runTask} before calling {@code onRightClick} - every
 * caller's own real ability logic runs safely on the main thread no matter what.
 */
public final class RightClickTrigger {
    private RightClickTrigger() {
    }

    /**
     * Registers the packet listener if (and only if) ProtocolLib is installed and enabled right
     * now - returns whether it did, so a caller that wants to adjust its own item lore (e.g.
     * "RIGHT CLICK or SWAP HANDS" vs. just "SWAP HANDS") knows which to show. {@code
     * shouldHandle} is the cheap, Netty-thread-safe pre-check deciding whether to cancel the
     * packet at all (item type, sneaking, any other instant state a caller wants to gate on);
     * {@code onRightClick} is the real ability logic, always run on the main thread afterward -
     * a caller whose own {@code attemptX(Player)} method already re-validates everything (same
     * shape every ability in this plugin already uses for its Swap Hands listener) can just
     * pass that method reference directly.
     */
    public static boolean registerIfAvailable(Plugin plugin, Predicate<Player> shouldHandle, Consumer<Player> onRightClick) {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            return false;
        }
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Client.USE_ITEM, PacketType.Play.Client.USE_ITEM_ON) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player p = event.getPlayer();
                if (!shouldHandle.test(p)) {
                    return;
                }
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> onRightClick.accept(p));
            }
        });
        return true;
    }
}
