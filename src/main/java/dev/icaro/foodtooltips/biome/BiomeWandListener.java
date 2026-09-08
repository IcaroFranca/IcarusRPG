package dev.icaro.foodtooltips.biome;

import dev.icaro.foodtooltips.i18n.Language;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Same swing/interact/menu wiring as {@code BuilderWandListener} - left-click (swing) opens the biome menu or undoes (shift), right-click on a block paints. */
public final class BiomeWandListener implements Listener {
    private final BiomeWandService wand;

    public BiomeWandListener(BiomeWandService wand) {
        this.wand = wand;
    }

    /** Left-click rides {@link PlayerAnimationEvent}, not {@code PlayerInteractEvent}'s air-click - see {@code BuilderWandListener#swing}'s doc for why. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.wand.isWand(held)) {
            return;
        }
        Language l = Language.of(p);
        if (p.isSneaking()) {
            int undone = this.wand.undo(p);
            if (undone <= 0) {
                p.sendActionBar(Component.text(l.choose("Nada pra desfazer.", "Nothing to undo."), NamedTextColor.RED));
            } else {
                p.sendActionBar(Component.text("-" + undone + " " + l.choose("células de bioma (desfeito)", "biome cells (undone)"), NamedTextColor.GOLD));
            }
        } else {
            this.wand.openMenu(p, held);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void use(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !this.wand.isWand(e.getItem())) {
            return;
        }
        Player p = e.getPlayer();
        Language l = Language.of(p);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            // The actual undo/menu action already ran off swing() above - this only
            // stops the left-click from also mining the block underneath the cursor.
            e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        e.setCancelled(true);
        Block clicked = e.getClickedBlock();
        if (clicked == null) {
            return;
        }
        int painted = this.wand.paint(p, clicked, e.getItem());
        if (painted <= 0) {
            p.sendActionBar(Component.text(l.choose("Já é esse bioma por aqui.", "Already this biome around here."), NamedTextColor.RED));
        } else {
            p.sendActionBar(Component.text("+" + painted + " " + l.choose("células de bioma", "biome cells"), NamedTextColor.GREEN));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void menuClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.wand.viewingMenu(p)) {
            e.setCancelled(true);
            this.wand.handleMenuClick(p, e.getRawSlot(), e.getClick());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void menuDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.wand.viewingMenu(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void menuClose(InventoryCloseEvent e) {
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player p) {
            this.wand.closeMenu(p);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.wand.forget(e.getPlayer().getUniqueId());
    }
}
