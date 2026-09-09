package dev.icaro.foodtooltips.skills;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Keeps {@link SkillsStarService}'s star pinned to its hotbar slot and wires any
 * click while holding it - left or right, air or block, plus clicking it in the
 * inventory screen if it's open - to {@link SkillsMenuService#openMain}. Every
 * handler here either re-grants the star (join/respawn/death) or cancels an
 * interaction that would move/drop/destroy it - nothing here ever lets it actually
 * leave slot {@value SkillsStarService#SLOT}.
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

    /**
     * Left-click (air or block) rides {@link PlayerAnimationEvent} - the plain
     * arm-swing animation - instead of {@code PlayerInteractEvent}'s
     * {@code LEFT_CLICK_AIR}. Bukkit's air-click interact event is throttled/best-effort
     * in a way block clicks aren't, so a swing with nothing in reach doesn't reliably
     * reach {@link #interact}. The animation event has no such caveat: it fires for
     * every left-click, air or not (same fix {@code BuilderWandListener#swing} uses).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (this.star.isStar(p.getInventory().getItemInMainHand())) {
            this.menus.openMain(p);
        }
    }

    /**
     * Right-click (air or block) opens the menu directly; left-click-on-block only
     * needs cancelling here - the open itself already ran off {@link #swing} - except
     * for a Bedrock player, whose left-click swing doesn't reliably reach {@link #swing}
     * through Geyser's translation (same caveat {@link BedrockSwordThrowListener} works
     * around), so it's opened straight from here instead for them.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !this.star.isStar(e.getItem())) {
            return;
        }
        if (e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_AIR) {
            e.setCancelled(true);
            if (BedrockPlayers.isBedrock(e.getPlayer())) {
                this.menus.openMain(e.getPlayer());
            }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        e.setCancelled(true);
        this.menus.openMain(e.getPlayer());
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

    /**
     * Blocks the F-key hand swap whenever the star is one of the two items involved -
     * living in the hotbar now means it can be the held item, and an un-sneaking swap
     * isn't caught by {@code SkillsListener#shortcut} (that one only cancels while
     * sneaking, to open the Skills menu - a different feature entirely).
     */
    @EventHandler(ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent e) {
        if (this.star.isStar(e.getMainHandItem()) || this.star.isStar(e.getOffHandItem())) {
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
