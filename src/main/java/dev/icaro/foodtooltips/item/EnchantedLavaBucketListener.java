package dev.icaro.foodtooltips.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The Enchanted Lava Bucket (Coal Collection M5, built by {@link MiningCollectionsItemsService}):
 * places lava like a normal Lava Bucket, but stays full - the player's own "um balde de lava que
 * não consome a lava". Every vanilla way a Lava Bucket gets emptied by something other than
 * placing it (furnace fuel, a dispenser, a cauldron) is blocked instead, since each would turn
 * this one-of-a-kind item into a plain empty Bucket.
 */
public final class EnchantedLavaBucketListener implements Listener {

    /** HIGHEST so whatever item another listener decided on is overridden last - the bucket always comes back full. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void empty(PlayerBucketEmptyEvent e) {
        ItemStack held = e.getHand() == null ? null : e.getPlayer().getInventory().getItem(e.getHand());
        if (MiningCollectionsItemsService.isEnchantedLavaBucket(held)) {
            e.setItemStack(held.clone());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void cauldron(CauldronLevelChangeEvent e) {
        if (e.getReason() == CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY && e.getEntity() instanceof Player p
                && (MiningCollectionsItemsService.isEnchantedLavaBucket(p.getInventory().getItemInMainHand())
                        || MiningCollectionsItemsService.isEnchantedLavaBucket(p.getInventory().getItemInOffHand()))) {
            e.setCancelled(true);
            p.sendActionBar(Component.text("The Enchanted Lava Bucket can't fill cauldrons.", NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void fuel(FurnaceBurnEvent e) {
        if (MiningCollectionsItemsService.isEnchantedLavaBucket(e.getFuel())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void dispense(BlockDispenseEvent e) {
        if (MiningCollectionsItemsService.isEnchantedLavaBucket(e.getItem())) {
            e.setCancelled(true);
        }
    }
}
