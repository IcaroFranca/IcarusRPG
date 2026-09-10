package dev.icaro.foodtooltips.enchant;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Right-clicking a real Enchanting Table block opens {@link EnchantMenuService}'s own
 * screen instead of vanilla's random-offer UI (the interact event is cancelled either
 * way, so vanilla's never gets a chance to open). Click routing per screen: the item
 * slot on the main screen accepts normal placement/pickup (there's something real to
 * enchant, and its change is picked up live - see {@link #click}), catalog/level icons
 * and navigation buttons are plain buttons, and the player's own inventory stays fully
 * usable throughout.
 *
 * <p>The Guide screen's search button opens a real sign-editing UI (the same trick
 * IcarusChests uses for its own chest search: temporarily turn a nearby block into a
 * sign, let the player type into vanilla's own sign editor, read what they typed via
 * {@link #onSignChange}, then restore the block) rather than any custom text-input
 * widget, since Bukkit doesn't have one.
 */
public final class EnchantMenuListener implements Listener {
    /** How long a fake search sign waits for input before giving up and restoring the block on its own (matches IcarusChests' own timeout) - 60 seconds. */
    private static final long SEARCH_SIGN_TIMEOUT_TICKS = 1200L;

    private final EnchantMenuService menu;
    private final Plugin plugin;
    private final Map<UUID, PendingSearch> pendingSearches = new HashMap<>();

    public EnchantMenuListener(EnchantMenuService menu, Plugin plugin) {
        this.menu = menu;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND
                || e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setCancelled(true);
        this.menu.open(e.getPlayer(), e.getClickedBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.menu.viewing(p)) {
            return;
        }
        EnchantMenuService.View view = this.menu.view(p);
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (!topInventory) {
            // Player's own inventory - always free to reorganize. Shift-click is only
            // meaningful on the main screen (shift-clicking a held item moves it into
            // the item slot, the only slot #fill leaves genuinely empty there - every
            // other slot on every screen is always either an icon or a filler pane, so
            // Bukkit's own default shift-click-into-top behavior can't land anywhere
            // else); block it everywhere else the way any other slot there would be.
            if (e.isShiftClick() && view.type() != EnchantMenuService.Type.MAIN) {
                e.setCancelled(true);
            } else if (e.isShiftClick()) {
                this.menu.scheduleCatalogRefresh(p);
            }
            return;
        }
        if (view.type() == EnchantMenuService.Type.MAIN && raw == EnchantMenuService.ITEM_SLOT) {
            // Not cancelled - real placement/pickup, this is the item being enchanted.
            // The catalog depends on what's here, so refresh it once the click's own
            // default pickup/place/swap actually resolves (next tick - this handler
            // runs before that happens).
            this.menu.scheduleCatalogRefresh(p);
            return;
        }
        e.setCancelled(true);
        if (view.type() == EnchantMenuService.Type.GUIDE && raw == EnchantMenuService.GUIDE_SEARCH_SLOT) {
            if (e.getClick().isShiftClick()) {
                this.menu.setGuideSearch(p, null);
                this.menu.openGuide(p, view.page());
            } else {
                this.openSearchSign(p);
            }
            return;
        }
        if (this.menu.handleNav(p, raw)) {
            return;
        }
        switch (view.type()) {
            case MAIN -> {
                EnchantEntry enchant = this.menu.catalogEnchantAt(p, EnchantMenuService.Type.MAIN, view.page(), raw);
                if (enchant == null) {
                    return;
                }
                ItemStack item = e.getInventory().getItem(EnchantMenuService.ITEM_SLOT);
                if (item == null || item.isEmpty()) {
                    p.sendMessage(Component.text(
                            Language.of(p).choose("Coloque um item para encantar primeiro.", "Place an item to enchant first."),
                            NamedTextColor.RED));
                    return;
                }
                e.getInventory().setItem(EnchantMenuService.ITEM_SLOT, null);
                this.menu.chooseEnchant(p, item, enchant);
            }
            case LEVEL -> {
                if (view.enchant() == null) {
                    return;
                }
                int level = this.menu.levelAt(raw);
                if (level > 0) {
                    this.menu.applyLevel(p, view.enchant(), level);
                }
            }
            case GUIDE -> {
                // Every clickable slot here (nav, search) is already handled above -
                // the catalog books and decorative title are read-only.
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.menu.viewing(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !this.menu.viewing(p) || this.menu.isTransitioning(p)) {
            // isTransitioning means WE closed this screen ourselves (switching to
            // another of our own screens, or opening the search sign) - not a real
            // close, so none of the return-item/clear-state logic below should run.
            return;
        }
        EnchantMenuService.View view = this.menu.view(p);
        ItemStack toReturn = view.type() == EnchantMenuService.Type.MAIN
                ? e.getInventory().getItem(EnchantMenuService.ITEM_SLOT)
                : this.menu.takePendingItem(p);
        this.menu.close(p);
        if (toReturn == null || toReturn.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> overflow = p.getInventory().addItem(toReturn);
        for (ItemStack over : overflow.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), over);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.pendingSearches.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Turns the block 2 above {@code p} into a sign, opens vanilla's own sign editor
     * on it, and remembers enough to undo that (see {@link #onSignChange}) - the menu
     * screen was already closed by the caller before this runs.
     */
    private void openSearchSign(Player p) {
        UUID id = p.getUniqueId();
        this.menu.markTransitioning(p, true);
        p.closeInventory();
        this.menu.markTransitioning(p, false);
        Block block = p.getLocation().getBlock().getRelative(BlockFace.UP, 2);
        BlockData originalData = block.getBlockData();
        block.setType(Material.OAK_SIGN, false);
        Sign sign = (Sign) block.getState();
        PendingSearch pending = new PendingSearch(block, originalData);
        this.pendingSearches.put(id, pending);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (this.pendingSearches.remove(id, pending)) {
                block.setBlockData(originalData, false);
            }
        }, SEARCH_SIGN_TIMEOUT_TICKS);
        p.openSign(sign, Side.FRONT);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        Player p = e.getPlayer();
        PendingSearch pending = this.pendingSearches.remove(p.getUniqueId());
        if (pending == null) {
            return;
        }
        e.setCancelled(true);
        pending.block().setBlockData(pending.originalData(), false);
        String query = PlainTextComponentSerializer.plainText().serialize(e.line(0));
        this.menu.setGuideSearch(p, query);
        // Next tick: the sign UI is still closing this same tick, and Bukkit won't
        // let another inventory open while that's in progress.
        Bukkit.getScheduler().runTask(this.plugin, () -> this.menu.openGuide(p, 0));
    }

    private record PendingSearch(Block block, BlockData originalData) {
    }
}
