package dev.icaro.foodtooltips.skills;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Keeps {@link SkillsStarService}'s star pinned to its slot and wires clicking it to
 * {@link SkillsMenuService#openMain}. Every handler here either re-grants the star
 * (join/respawn/death) or cancels an interaction that would move/drop/destroy it -
 * nothing here ever lets it actually leave slot {@value SkillsStarService#SLOT}.
 */
public final class SkillsStarListener implements Listener {
    private final Plugin plugin;
    private final SkillsStarService star;
    private final SkillsMenuService menus;

    public SkillsStarListener(Plugin plugin, SkillsStarService star, SkillsMenuService menus) {
        this.plugin = plugin;
        this.star = star;
        this.menus = menus;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.star.ensure(e.getPlayer());
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent e) {
        this.star.ensure(e.getPlayer());
    }

    /** Belt-and-suspenders: {@code keepInventory false} would otherwise drop it on the ground for anyone to grab. */
    @EventHandler
    public void death(PlayerDeathEvent e) {
        e.getDrops().removeIf(this.star::isStar);
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (!this.star.isStar(current) && !this.star.isStar(cursor)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() instanceof PlayerInventory && e.getSlot() == SkillsStarService.SLOT && this.star.isStar(current)) {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                p.closeInventory();
                this.menus.openMain(p);
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (this.star.isStar(e.getOldCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent e) {
        if (this.star.isStar(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }
}
