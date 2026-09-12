package dev.icaro.foodtooltips.food;

import dev.icaro.foodtooltips.food.FoodTooltipService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemStackUtil;
import dev.icaro.foodtooltips.item.ItemTierService;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
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
    private final ItemTierService tiers;
    private final Set<UUID> scheduled = new HashSet<UUID>();

    public FoodTooltipListener(Plugin p, FoodTooltipService s, ItemTierService tiers) {
        this.plugin = p;
        this.service = s;
        this.tiers = tiers;
    }

    /**
     * Tags TIER (and, where applicable, food/mining) lore onto every item the instant
     * it spawns in the world - a block break, a mob kill, a dispenser, anything -
     * rather than only once it's already sitting in a player's inventory. Without this,
     * a freshly-dropped item (no lore yet) picked up right after an already-tagged
     * stack of the same item sits in the player's inventory looks like a DIFFERENT item
     * to vanilla's own stacking check (different lore = not stackable) until the next
     * tick/interaction catches up and re-tags/coalesces it - for a big burst of drops
     * (Vein Miner breaking dozens of ore blocks in one go, say) that shows up as a pile
     * of separate un-merged stacks instead of one. {@link ItemTierService#applyTier}
     * ignores its {@code Language} argument entirely (the "TIER X" label is the same in
     * both languages - see its own doc) and {@link FoodTooltipService#update} never
     * reads the {@code Player} it's handed, so passing a fixed language and no player
     * here is safe; a language mismatch on the food-attributes header text, if it ever
     * mattered, self-heals the moment the item is next touched by any of this class's
     * other hooks.
     */
    @EventHandler(ignoreCancelled = true)
    public void spawn(ItemSpawnEvent e) {
        Item entity = e.getEntity();
        ItemStack stack = entity.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        boolean changed = this.service.update(stack, Language.EN, null);
        if (this.tiers.applyTier(stack, Language.EN) != null) {
            changed = true;
        }
        if (changed) {
            entity.setItemStack(stack);
        }
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
        // isExcludedContainer skips this class entirely for furnace-family blocks and
        // the real vanilla Crafting Table - see its own doc.
        if (top.getLocation() != null && !isExcludedContainer(top.getType()) && (changed |= this.update(top, l, p, isSimpleStorage(top.getType())))) {
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

    /**
     * Furnace-family blocks (Furnace/Blast Furnace/Smoker) and the real vanilla
     * Crafting Table - this class no longer touches their contents' lore AT ALL (not
     * just skipping coalescing, like {@link #isSimpleStorage} does for other
     * process blocks) per the user's own request: rewriting the tier/food tooltip on
     * whatever's sitting in a furnace's input/fuel/output slots or a crafting grid was
     * breaking IcarusFurnaces (a separate plugin managing its own furnace/crafting
     * behavior, presumably reading that same lore/meta for its own recipe or item
     * identity checks). This plugin's own Crafting Table screen ({@code
     * CraftingMenuService}) is unaffected either way - it's a synthetic {@code
     * Bukkit.createInventory} GUI with no real block behind it, already excluded by
     * the {@code top.getLocation() != null} check above.
     */
    private static boolean isExcludedContainer(InventoryType type) {
        return switch (type) {
            case FURNACE, BLAST_FURNACE, SMOKER, WORKBENCH -> true;
            default -> false;
        };
    }

    /**
     * {@code coalesce} gates only the same-item-stack-merging pass (see {@link
     * #isSimpleStorage}) - the tooltip rewrite above it always runs regardless of slot
     * layout. Also tags TIER lore ({@link ItemTierService#applyTier}/{@link
     * ItemTierService#repairTierSpacing}) on every item this touches, not just the
     * player's own inventory - {@code ItemTierService#applyItemTiers}'s own periodic
     * tick (see {@code FoodTooltipsPlugin}) only ever reaches a PLAYER's inventory, so
     * without this, a chest filled some other way than passing through a player first
     * (a loot table, a hopper, an admin command, another plugin) would show untagged
     * items forever the moment it's opened, instead of getting the same tier tooltip
     * a player's own gear already has.
     */
    private boolean update(Inventory inv, Language l, Player p, boolean coalesce) {
        boolean changed = false;
        ItemStack[] contents = inv.getContents();
        for (ItemStack i : contents) {
            if (i == null || i.isEmpty()) continue;
            changed |= this.service.update(i, l, p);
            ItemStack tiered = this.tiers.applyTier(i, l);
            if (tiered == null) {
                tiered = this.tiers.repairTierSpacing(i);
            }
            if (tiered != null) {
                changed = true;
            }
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

