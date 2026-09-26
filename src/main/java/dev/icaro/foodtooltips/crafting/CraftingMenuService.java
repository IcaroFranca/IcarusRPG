package dev.icaro.foodtooltips.crafting;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.AccessoryItems;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * A crafting table embedded directly in the Skills menu - no physical block needed.
 * The 3x3 grid ({@link #MATRIX_SLOTS}) and {@link #OUTPUT_SLOT} recognize every real
 * vanilla recipe (via {@link Bukkit#craftItem}, the same resolution a real crafting
 * table uses - including any recipe added by another plugin), everything else in the
 * 54-slot menu is a plain decorative gray pane.
 *
 * <p><b>Known v1 simplifications</b> (small, deliberate scope cuts - not bugs):
 * taking the output always goes straight into the player's inventory (dropped at
 * their feet if it's full) rather than riding the cursor the way vanilla's own result
 * slot does; there's no shift-click-to-fill-the-grid or multi-slot dragging into it
 * yet, only single-item placement (matching the exact workflow the crafting-grid
 * merge bug reports already showed players using); and a recipe whose ingredients
 * have a "leftover" item (a milk bucket recipe returning empty buckets, for example)
 * simply consumes the ingredient outright instead of returning that leftover, unlike
 * a small minority of real vanilla recipes.
 */
public final class CraftingMenuService {
    /** Row-major (matches {@link Bukkit#craftItem}'s [0 1 2 / 3 4 5 / 6 7 8] order) slots of the 3x3 grid, centered in the 54-slot menu. */
    public static final int[] MATRIX_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    public static final int OUTPUT_SLOT = 24;
    public static final int BACK_SLOT = 49;
    private static final int[] VISIBLE_WORK_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31, OUTPUT_SLOT};

    private final Consumer<Player> back;
    private final GlobalLevelService global;
    private final ItemTierService tiers;
    private final Set<UUID> viewing = new HashSet<>();

    public CraftingMenuService(Consumer<Player> back, GlobalLevelService global, ItemTierService tiers) {
        this.back = back;
        this.global = global;
        this.tiers = tiers;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Mesa de Trabalho", "Crafting Table"));
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) {
            v.setItem(i, filler);
        }
        for (int slot : MATRIX_SLOTS) {
            v.setItem(slot, null);
        }
        v.setItem(OUTPUT_SLOT, null);
        v.setItem(BACK_SLOT, this.customHead(HeadTexture.BACK, l.choose("Voltar às skills", "Back to skills"), List.of()));
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p, VISIBLE_WORK_SLOTS);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
    }

    /** Order matters: fires the back navigation first (which opens a new top inventory and, in doing so, synchronously triggers {@link CraftingMenuListener#close} for this one while {@link #viewing} still says yes) so {@link #returnGridItems} actually runs before this player stops counting as viewing. */
    public void back(Player p) {
        this.back.accept(p);
        this.viewing.remove(p.getUniqueId());
    }

    /**
     * Gives back whatever's left sitting in the 3x3 grid when the menu closes for any
     * reason (pressing Escape, the Back button, a plugin reload, the player quitting) -
     * this is a virtual inventory conjured with {@link Bukkit#createInventory}, not a
     * real placed block, so unlike a real crafting table nothing else would ever return
     * these items to the player. Overflow drops at their feet, same as {@link
     * #takeOutput}. Called from {@link CraftingMenuListener#close} with the
     * about-to-close inventory, whose contents are still readable at that point.
     */
    public void returnGridItems(Player p, Inventory v) {
        for (int slot : MATRIX_SLOTS) {
            ItemStack item = v.getItem(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(item.clone());
            for (ItemStack leftover : overflow.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
            v.setItem(slot, null);
        }
    }

    /** Re-derives the output slot from the grid's current contents - called (next tick, after the click that changed it actually lands) whenever a matrix slot changes. */
    public void recompute(Player p) {
        Inventory v = p.getOpenInventory().getTopInventory();
        if (!this.viewing(p) || v.getSize() != 54) {
            return;
        }
        ItemStack[] matrix = new ItemStack[MATRIX_SLOTS.length];
        for (int i = 0; i < MATRIX_SLOTS.length; i++) {
            ItemStack item = v.getItem(MATRIX_SLOTS[i]);
            matrix[i] = item == null ? ItemStack.of(Material.AIR) : item;
        }
        ItemStack result = Bukkit.craftItem(matrix, p.getWorld(), p);
        v.setItem(OUTPUT_SLOT, result.isEmpty() ? null : result);
    }

    /**
     * Takes whatever's in the output slot: gives it to {@code p} and consumes one of
     * each non-empty ingredient from the grid, vanilla-style, then recomputes in case
     * there's enough left for another craft (so repeatedly clicking draws the stack
     * down one craft at a time, same as a real table). No-op if the output is empty.
     */
    public void takeOutput(Player p) {
        Inventory v = p.getOpenInventory().getTopInventory();
        if (!this.viewing(p) || v.getSize() != 54) {
            return;
        }
        ItemStack result = v.getItem(OUTPUT_SLOT);
        if (result == null || result.isEmpty()) {
            return;
        }
        this.grantAccessoryCraftXp(p, result);
        Map<Integer, ItemStack> overflow = p.getInventory().addItem(result.clone());
        for (ItemStack leftover : overflow.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
        }
        for (int slot : MATRIX_SLOTS) {
            ItemStack item = v.getItem(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            item.setAmount(item.getAmount() - 1);
            v.setItem(slot, item.getAmount() <= 0 ? null : item);
        }
        this.recompute(p);
    }

    /**
     * Global Level XP for crafting an accessory ({@link AccessoryItems#type} says so - never
     * plain gear, even at the same tier: the player's own spec was specifically about
     * accessories), scaled to how good the one just crafted is: 1 XP at Tier D (the
     * Talisman rung), 3 at Tier C (Ring), 5 at Tier B (Artifact) - multiplied by however many
     * came out of this craft at once, though every accessory recipe today only ever yields 1.
     */
    private void grantAccessoryCraftXp(Player p, ItemStack result) {
        if (AccessoryItems.type(result) == null) {
            return;
        }
        long xpPerItem = switch (this.tiers.tierOf(result)) {
            case D -> 1L;
            case C -> 3L;
            case B -> 5L;
            default -> 0L;
        };
        if (xpPerItem > 0L) {
            this.global.addGlobalXp(p, xpPerItem * result.getAmount(), GlobalXpSource.ACCESSORY_CRAFT);
        }
    }

    /**
     * Shift-click on the output slot: repeats {@link #takeOutput} (one real craft each
     * time, ingredients included) until the grid can't produce the recipe anymore -
     * same "craft the whole stack at once" behavior a real vanilla crafting table's
     * result slot gives a shift-click, which this screen's own plain click didn't
     * reproduce before (it only ever did one craft, shift or not). Terminates on its
     * own: each pass consumes at least one ingredient, so the grid's own finite stacks
     * guarantee the output eventually goes empty.
     */
    public void craftAll(Player p) {
        Inventory v = p.getOpenInventory().getTopInventory();
        if (!this.viewing(p) || v.getSize() != 54) {
            return;
        }
        ItemStack result = v.getItem(OUTPUT_SLOT);
        while (result != null && !result.isEmpty()) {
            this.takeOutput(p);
            result = v.getItem(OUTPUT_SLOT);
        }
    }

    /** A player head wearing a custom skin (base64 "Value" texture), falling back to a plain head if it's bad. */
    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        m.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta((ItemMeta) m);
        return i;
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
}
