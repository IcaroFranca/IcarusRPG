package dev.icaro.foodtooltips.crafting;

import dev.icaro.foodtooltips.i18n.Language;
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
    public static final int OUTPUT_SLOT = 25;
    public static final int BACK_SLOT = 45;
    private static final int ARROW_SLOT = 24;

    private final Consumer<Player> back;
    private final Set<UUID> viewing = new HashSet<>();

    public CraftingMenuService(Consumer<Player> back) {
        this.back = back;
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
        v.setItem(ARROW_SLOT, this.item(Material.ARROW, l.choose("Resultado", "Result"), List.of()));
        v.setItem(OUTPUT_SLOT, null);
        v.setItem(BACK_SLOT, this.item(Material.BARRIER, l.choose("Voltar às skills", "Back to skills"), List.of()));
        p.openInventory(v);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
    }

    public void back(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.back.accept(p);
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
