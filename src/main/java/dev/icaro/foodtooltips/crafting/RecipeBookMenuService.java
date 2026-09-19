package dev.icaro.foodtooltips.crafting;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/**
 * A read-only catalogue of every custom recipe this plugin itself has registered (Lapis
 * Lazuli Armor and its Experience Bottles today - see {@code LapisArmorService}/{@code
 * LapisExperienceService}), reachable from the Skills menu's Book icon. Deliberately never
 * hand-maintained: {@link #ownRecipes} asks Bukkit's own recipe registry for every recipe
 * whose key belongs to this plugin ({@link #namespace}, resolved once from a throwaway key
 * rather than hardcoded, since {@link NamespacedKey}'s own sanitizing of the plugin's name
 * is otherwise easy to get subtly wrong) - so a brand new {@code Bukkit.addRecipe(...)} call
 * anywhere in the plugin shows up here automatically the next time this menu opens, without
 * ever touching this class. Vanilla and other plugins' recipes are never listed.
 *
 * <p>Clicking a result opens {@link #openDetail}, a read-only preview of that one recipe's
 * shape - the exact same 3x3-grid-plus-arrow-plus-result layout {@link CraftingMenuService}
 * already uses for live crafting, just filled with the recipe's own ingredients and immune
 * to every click (nothing here is ever taken, moved or crafted). A {@link ShapelessRecipe}
 * has no fixed shape to show, so its ingredients just fill the grid in listed order instead.
 */
public final class RecipeBookMenuService {
    /** 28 per page (4 rows x 7, framed by the border columns/rows every other menu here uses). */
    private static final int[] LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    /** Same positions {@code CraftingMenuService#MATRIX_SLOTS} uses, so a shaped recipe's shape reads identically in both screens. */
    private static final int[] DETAIL_MATRIX_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    private static final int DETAIL_ARROW_SLOT = 24;
    private static final int DETAIL_RESULT_SLOT = 25;
    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;

    private record View(int page, NamespacedKey detail) {
    }

    private final Plugin plugin;
    private final Consumer<Player> back;
    private final Map<UUID, View> views = new HashMap<>();

    public RecipeBookMenuService(Plugin plugin, Consumer<Player> back) {
        this.plugin = plugin;
        this.back = back;
    }

    public boolean viewing(Player p) {
        return this.views.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        this.views.remove(p.getUniqueId());
    }

    public void back(Player p) {
        this.back.accept(p);
        this.views.remove(p.getUniqueId());
    }

    public void open(Player p, int page) {
        List<CraftingRecipe> recipes = this.ownRecipes();
        int maxPage = recipes.isEmpty() ? 0 : (recipes.size() - 1) / LIST_SLOTS.length;
        page = Math.max(0, Math.min(maxPage, page));
        Language l = Language.of(p);
        Inventory v = this.blank(l.choose("Livro de Receitas", "Recipe Book"));
        int start = page * LIST_SLOTS.length;
        for (int i = 0; i < LIST_SLOTS.length && start + i < recipes.size(); i++) {
            v.setItem(LIST_SLOTS[i], this.resultIcon(recipes.get(start + i), l));
        }
        v.setItem(BACK_SLOT, this.customHead(HeadTexture.BACK, l.choose("Voltar às skills", "Back to skills"), List.of()));
        if (page > 0) {
            v.setItem(PREV_SLOT, this.customHead(HeadTexture.ARROW_LEFT, l.choose("Página anterior", "Previous page"), List.of()));
        }
        if ((page + 1) * LIST_SLOTS.length < recipes.size()) {
            v.setItem(NEXT_SLOT, this.customHead(HeadTexture.ARROW_RIGHT, l.choose("Próxima página", "Next page"), List.of()));
        }
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.views.put(p.getUniqueId(), new View(page, null));
    }

    private void openDetail(Player p, int fromPage, NamespacedKey key) {
        Recipe recipe = Bukkit.getRecipe(key);
        if (!(recipe instanceof CraftingRecipe crafting)) {
            // The recipe was removed/changed since the list was built (a /reload, say) -
            // safest fallback is just back to a fresh list rather than a broken detail screen.
            this.open(p, fromPage);
            return;
        }
        Language l = Language.of(p);
        Inventory v = this.blank(l.choose("Receita", "Recipe"));
        ItemStack[] grid = this.gridFor(crafting);
        for (int i = 0; i < DETAIL_MATRIX_SLOTS.length; i++) {
            v.setItem(DETAIL_MATRIX_SLOTS[i], grid[i]);
        }
        v.setItem(DETAIL_ARROW_SLOT, this.item(Material.ARROW, l.choose("Resultado", "Result"), List.of()));
        v.setItem(DETAIL_RESULT_SLOT, crafting.getResult().clone());
        v.setItem(BACK_SLOT, this.customHead(HeadTexture.BACK, l.choose("Voltar ao livro", "Back to the book"), List.of()));
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.views.put(p.getUniqueId(), new View(fromPage, key));
    }

    /** Dispatches a raw top-inventory slot click - {@link RecipeBookListener} cancels everything before calling this, so there's never anything to actually move. */
    public void click(Player p, int rawSlot) {
        View view = this.views.get(p.getUniqueId());
        if (view == null) {
            return;
        }
        if (view.detail() != null) {
            if (rawSlot == BACK_SLOT) {
                this.open(p, view.page());
            }
            return;
        }
        if (rawSlot == BACK_SLOT) {
            this.back(p);
            return;
        }
        if (rawSlot == PREV_SLOT) {
            this.open(p, view.page() - 1);
            return;
        }
        if (rawSlot == NEXT_SLOT) {
            this.open(p, view.page() + 1);
            return;
        }
        for (int i = 0; i < LIST_SLOTS.length; i++) {
            if (LIST_SLOTS[i] != rawSlot) {
                continue;
            }
            List<CraftingRecipe> recipes = this.ownRecipes();
            int index = view.page() * LIST_SLOTS.length + i;
            if (index < recipes.size()) {
                this.openDetail(p, view.page(), recipes.get(index).getKey());
            }
            return;
        }
    }

    /** Every registered recipe whose key belongs to this plugin, sorted by result Material name for a stable order across opens. */
    private List<CraftingRecipe> ownRecipes() {
        String namespace = this.namespace();
        List<CraftingRecipe> found = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r instanceof CraftingRecipe crafting && crafting.getKey().getNamespace().equals(namespace)) {
                found.add(crafting);
            }
        }
        found.sort(Comparator.comparing(r -> r.getResult().getType().name()));
        return found;
    }

    /** This plugin's own {@link NamespacedKey} namespace, resolved from a throwaway key rather than guessed from {@code plugin.getName()} - see the class doc. */
    private String namespace() {
        return new NamespacedKey(this.plugin, "recipe_book_probe").getNamespace();
    }

    /** The list icon for one recipe: its result, with a short ingredient summary appended to whatever lore it already has. */
    private ItemStack resultIcon(CraftingRecipe recipe, Language l) {
        ItemStack icon = recipe.getResult().clone();
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(this.text(l.choose("Ingredientes:", "Ingredients:"), NamedTextColor.GRAY));
        for (RecipeChoice choice : this.choicesOf(recipe)) {
            ItemStack sample = choice.getItemStack();
            if (sample == null || sample.isEmpty()) {
                continue;
            }
            Component name = sample.hasItemMeta() && sample.getItemMeta().hasDisplayName()
                    ? sample.getItemMeta().displayName()
                    : Component.translatable(sample.getType().translationKey());
            lore.add(this.text("- ", NamedTextColor.DARK_GRAY).append(name.colorIfAbsent(NamedTextColor.DARK_GRAY)));
        }
        lore.add(Component.empty());
        lore.add(this.text(l.choose("Clique para ver a receita.", "Click to see the recipe."), NamedTextColor.YELLOW));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /** {@code recipe}'s ingredients, deduplicated by distinct choice - used for the list icon's short summary (see {@link #resultIcon}), not the detail grid (which needs every slot, blanks included - see {@link #gridFor}). */
    private List<RecipeChoice> choicesOf(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getChoiceMap().values().stream().filter(Objects::nonNull).distinct().toList();
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return shapeless.getChoiceList().stream().distinct().toList();
        }
        return List.of();
    }

    /** A 9-slot (row-major, matching {@link #DETAIL_MATRIX_SLOTS}) preview of {@code recipe}'s actual shape - a {@link org.bukkit.inventory.ShapedRecipe}'s own blank spaces stay empty; a {@link org.bukkit.inventory.ShapelessRecipe} has no shape at all, so its ingredients just fill the grid in listed order instead. */
    private ItemStack[] gridFor(CraftingRecipe recipe) {
        ItemStack[] grid = new ItemStack[9];
        if (recipe instanceof ShapedRecipe shaped) {
            String[] rows = shaped.getShape();
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (int row = 0; row < rows.length && row < 3; row++) {
                String line = rows[row];
                for (int col = 0; col < line.length() && col < 3; col++) {
                    RecipeChoice choice = choices.get(line.charAt(col));
                    ItemStack sample = choice == null ? null : choice.getItemStack();
                    if (sample != null && !sample.isEmpty()) {
                        grid[row * 3 + col] = sample.clone();
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> choices = shapeless.getChoiceList();
            for (int i = 0; i < choices.size() && i < 9; i++) {
                ItemStack sample = choices.get(i).getItemStack();
                if (sample != null && !sample.isEmpty()) {
                    grid[i] = sample.clone();
                }
            }
        }
        return grid;
    }

    private Inventory blank(String title) {
        Inventory v = Bukkit.createInventory(null, 54, title);
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) {
            v.setItem(i, filler);
        }
        return v;
    }

    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Fall back to a plain player head if the texture is invalid.
        }
        m.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
}
