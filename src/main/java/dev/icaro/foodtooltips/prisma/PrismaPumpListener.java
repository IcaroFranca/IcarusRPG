package dev.icaro.foodtooltips.prisma;

import dev.icaro.foodtooltips.i18n.Language;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/** Fires the Prismapump's own effect ({@link PrismaPumpService#fillAdjacentWater}) the moment one is placed. */
public final class PrismaPumpListener implements Listener {
    private final PrismaPumpService pump;

    public PrismaPumpListener(PrismaPumpService pump) {
        this.pump = pump;
    }

    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (!this.pump.isPump(e.getItemInHand())) {
            return;
        }
        int filled = this.pump.fillAdjacentWater(e.getBlockPlaced());
        if (filled <= 0) {
            return;
        }
        Player p = e.getPlayer();
        Language l = Language.of(p);
        p.sendActionBar(Component.text("+" + filled + " " + l.choose("água", "water"), NamedTextColor.AQUA));
    }
}
