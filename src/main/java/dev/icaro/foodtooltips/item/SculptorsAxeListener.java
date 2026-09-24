package dev.icaro.foodtooltips.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Fires the Sculptor's Axe's own effect ({@link SculptorsAxeService#transform}) on right-click - same shape as {@code sponge.MegaSpongeListener}. */
public final class SculptorsAxeListener implements Listener {
    private final SculptorsAxeService axe;

    public SculptorsAxeListener(SculptorsAxeService axe) {
        this.axe = axe;
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !this.axe.isSculptorsAxe(e.getItem())) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = e.getClickedBlock();
        if (clicked == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!this.axe.transform(clicked)) {
            p.sendActionBar(Component.text("Nothing to sculpt here.", NamedTextColor.RED));
        }
    }
}
