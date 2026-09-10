package dev.icaro.foodtooltips.food;

import dev.icaro.foodtooltips.food.FoodTooltipService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemStackUtil;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class FoodTooltipListener
implements Listener {
    private final Plugin plugin;
    private final FoodTooltipService service;
    private final Set<UUID> scheduled = new HashSet<UUID>();

    public FoodTooltipListener(Plugin p, FoodTooltipService s) {
        this.plugin = p;
        this.service = s;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.later(e.getPlayer());
    }

    @EventHandler
    public void locale(PlayerLocaleChangeEvent e) {
        this.later(e.getPlayer());
    }

    @EventHandler
    public void open(InventoryOpenEvent e) {
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player) {
            Player p = (Player)humanEntity;
            this.later(p);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void click(InventoryClickEvent e) {
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player) {
            Player p = (Player)humanEntity;
            this.later(p);
        }
    }

    @EventHandler
    public void held(PlayerItemHeldEvent e) {
        this.later(e.getPlayer());
    }

    private void later(Player p) {
        if (this.scheduled.add(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                this.scheduled.remove(p.getUniqueId());
                this.refresh(p);
            });
        }
    }

    public void refresh(Player p) {
        Language l = Language.of(p);
        boolean changed = this.update((Inventory)p.getInventory(), l, p, true);
        Inventory top = p.getOpenInventory().getTopInventory();
        // A plugin GUI (this plugin's own Skills menu and every sub-screen, but also any
        // *other* plugin's custom inventory - e.g. a storage/chest GUI built via
        // Bukkit.createInventory(customHolder, ...) for its own click-handling bookkeeping)
        // is a headless inventory with no physical block behind it. getHolder() != null
        // used to be the check here, but a plugin GUI can very much have a non-null
        // holder of its own (its own InventoryHolder implementation, not null) while still
        // being exactly as virtual as one of ours - getLocation() is the real
        // discriminator: it's non-null only for an inventory actually attached to a block
        // (a chest, barrel, ender chest...), never for a synthetic Bukkit.createInventory
        // GUI regardless of what holder it was given - a virtual GUI's decorative filler
        // tiles (every empty slot typically shares the SAME ItemStack reference) would
        // otherwise get coalesced into a single stack instead of staying filled.
        // isSimpleStorage further narrows the *coalesce* half specifically - see its doc.
        if (top.getLocation() != null && (changed |= this.update(top, l, p, isSimpleStorage(top.getType())))) {
            p.updateInventory();
        }
    }

    /**
     * Whether {@code type} is a plain storage container, where every slot is
     * interchangeable and merging same-item stacks (see {@link ItemStackUtil#coalesce})
     * is always safe - unlike a crafting table, furnace, brewing stand, anvil,
     * enchanting table... where specific slots hold a deliberate, position-meaningful
     * arrangement (a recipe in progress, fuel, an input) that coalescing would wrongly
     * collapse, silently destroying whatever the player was mid-arranging the moment
     * they clicked anywhere else in their own inventory.
     */
    private static boolean isSimpleStorage(InventoryType type) {
        return switch (type) {
            case CHEST, BARREL, SHULKER_BOX, ENDER_CHEST, DISPENSER, DROPPER, HOPPER -> true;
            default -> false;
        };
    }

    /** {@code coalesce} gates only the same-item-stack-merging pass (see {@link #isSimpleStorage}) - the tooltip rewrite above it always runs regardless of slot layout. */
    private boolean update(Inventory inv, Language l, Player p, boolean coalesce) {
        boolean changed = false;
        ItemStack[] contents = inv.getContents();
        for (ItemStack i : contents) {
            if (i == null || i.isEmpty()) continue;
            changed |= this.service.update(i, l, p);
        }
        // Heals same-item stacks left split by this very rewrite pass (or
        // ItemTierService's, running on its own schedule) landing on the two stacks in
        // a different order - see ItemStackUtil's class doc. Gated on changed (a
        // rewrite actually just touched something in this pass) - without that, this
        // ran on *every* click regardless, silently re-merging stacks the player just
        // split on purpose (a plain, everyday inventory action) the instant they
        // clicked anywhere else. Only where every slot is interchangeable storage (see
        // isSimpleStorage) - never on a crafting/process block, where slot position is
        // meaningful and this would corrupt it instead.
        if (coalesce && changed && ItemStackUtil.coalesce(contents)) {
            changed = true;
        }
        if (changed) {
            inv.setContents(contents);
        }
        return changed;
    }
}

