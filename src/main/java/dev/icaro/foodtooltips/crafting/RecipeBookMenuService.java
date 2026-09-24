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

    /** {@code externalBack}, when set, is who opened the detail screen directly (see {@link #openDetail(Player, NamespacedKey, Runnable)}) rather than this book's own list - its Back button runs that instead of returning to {@code page}. */
    private record View(int page, NamespacedKey detail, Runnable externalBack) {
    }

    /** Whether {@code viewer} has met a gated recipe's own unlock condition, plus the human-readable requirement text (see {@link #requirementCheck}) - {@code met} true still gets a lore line ("requirement already met"), matching the player's own spec that a gated recipe stays visible either way, just with a requirement notice attached. */
    public record Requirement(boolean met, String labelPt, String labelEn) {
    }

    /** Late-bound, same "no direct dependency on the gating feature" shape {@code skills.ArmorDefenseService}'s own {@code protectionBonus}/{@code lethalityPenalty} callbacks use - lets {@code collections.CollectionsService} gate a recipe without this class ever depending on the {@code collections} package. Returns {@code null} for a recipe nothing gates (the default, and the only behavior before this is ever wired). */
    @FunctionalInterface
    public interface RequirementCheck {
        Requirement check(Player viewer, NamespacedKey recipeKey);
    }

    private final Plugin plugin;
    private final Consumer<Player> back;
    private final Map<UUID, View> views = new HashMap<>();
    private RequirementCheck requirementCheck = (viewer, key) -> null;

    public RecipeBookMenuService(Plugin plugin, Consumer<Player> back) {
        this.plugin = plugin;
        this.back = back;
    }

    /** Wired after construction, same reason as every other late-bound setter in this codebase - see {@link RequirementCheck}'s own doc. */
    public void requirementCheck(RequirementCheck requirementCheck) {
        this.requirementCheck = requirementCheck;
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
            v.setItem(LIST_SLOTS[i], this.resultIcon(p, recipes.get(start + i), l));
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
        this.views.put(p.getUniqueId(), new View(page, null, null));
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
        Inventory v = this.renderDetail(crafting, l, l.choose("Voltar ao livro", "Back to the book"));
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.views.put(p.getUniqueId(), new View(fromPage, key, null));
    }

    /**
     * Opens the same read-only recipe detail screen directly for {@code key}, skipping this
     * book's own list/page entirely - lets another menu (e.g. {@code
     * collections.CollectionsMenuService}'s own item preview screen) show a milestone's real
     * recipe shape without becoming part of this book's list flow. {@code onBack} runs instead
     * of returning to a list page when the player clicks Back (see {@link View#externalBack}) -
     * typically re-opening whatever screen the caller opened this from. Silently defers to
     * {@code onBack} instead of opening anything if {@code key} doesn't resolve to a real,
     * currently-registered recipe.
     */
    public void openDetail(Player p, NamespacedKey key, Runnable onBack) {
        Recipe recipe = Bukkit.getRecipe(key);
        if (!(recipe instanceof CraftingRecipe crafting)) {
            onBack.run();
            return;
        }
        Language l = Language.of(p);
        Inventory v = this.renderDetail(crafting, l, l.choose("Voltar", "Back"));
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.views.put(p.getUniqueId(), new View(0, key, onBack));
    }

    /** The shared 3x3-grid-plus-arrow-plus-result layout both {@link #openDetail(Player, int, NamespacedKey)} and {@link #openDetail(Player, NamespacedKey, Runnable)} render, differing only in the Back button's own label. */
    private Inventory renderDetail(CraftingRecipe crafting, Language l, String backLabel) {
        Inventory v = this.blank(l.choose("Receita", "Recipe"));
        ItemStack[] grid = this.gridFor(crafting);
        for (int i = 0; i < DETAIL_MATRIX_SLOTS.length; i++) {
            v.setItem(DETAIL_MATRIX_SLOTS[i], grid[i]);
        }
        v.setItem(DETAIL_ARROW_SLOT, this.item(Material.ARROW, l.choose("Resultado", "Result"), List.of()));
        v.setItem(DETAIL_RESULT_SLOT, crafting.getResult().clone());
        v.setItem(BACK_SLOT, this.customHead(HeadTexture.BACK, backLabel, List.of()));
        return v;
    }

    /** Dispatches a raw top-inventory slot click - {@link RecipeBookListener} cancels everything before calling this, so there's never anything to actually move. */
    public void click(Player p, int rawSlot) {
        View view = this.views.get(p.getUniqueId());
        if (view == null) {
            return;
        }
        if (view.detail() != null) {
            if (rawSlot == BACK_SLOT) {
                if (view.externalBack() != null) {
                    this.views.remove(p.getUniqueId());
                    view.externalBack().run();
                } else {
                    this.open(p, view.page());
                }
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

    /**
     * Recipe keys built the "old" way, straight from a hardcoded {@code "foodtooltips"}
     * string (see e.g. {@code collections.CollectionsCatalog}'s own recipe-key constants and
     * every {@code item.FarmingCollectionsItemsService} recipe registration) rather than from
     * this plugin's own {@link Plugin} instance (whose {@link NamespacedKey}-sanitized name is
     * {@code "icarusrpg"}, from the {@code plugin.yml} display name - see {@link #namespace}).
     * Both are this plugin's own recipes just the same, so {@link #ownRecipes} accepts either
     * - without this, every Collections-gated Farming recipe (Cactus/Chocolate/Mushroom
     * Armor, every Core, Farmhand/Haymaker/Farmer Boots, both Mushroom Soups...) silently
     * never showed up here at all, leaving only the couple of recipes (Lapis Lazuli Armor,
     * its Experience Bottles) that happen to use the other, plugin-instance-derived key.
     */
    private static final String LEGACY_RECIPE_NAMESPACE = "foodtooltips";

    /** Every registered recipe whose key belongs to this plugin (either of its two own namespaces - see {@link #LEGACY_RECIPE_NAMESPACE}), sorted by result Material name for a stable order across opens. */
    private List<CraftingRecipe> ownRecipes() {
        String namespace = this.namespace();
        List<CraftingRecipe> found = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r instanceof CraftingRecipe crafting) {
                String ns = crafting.getKey().getNamespace();
                if (ns.equals(namespace) || ns.equals(LEGACY_RECIPE_NAMESPACE)) {
                    found.add(crafting);
                }
            }
        }
        found.sort(Comparator.comparing(r -> r.getResult().getType().name()));
        return found;
    }

    /** This plugin's own {@link NamespacedKey} namespace, resolved from a throwaway key rather than guessed from {@code plugin.getName()} - see the class doc. */
    private String namespace() {
        return new NamespacedKey(this.plugin, "recipe_book_probe").getNamespace();
    }

    /** The list icon for one recipe: its result, with a short ingredient summary (and, for a gated recipe, its requirement's own status - see {@link RequirementCheck}) appended to whatever lore it already has. */
    private ItemStack resultIcon(Player p, CraftingRecipe recipe, Language l) {
        ItemStack icon = recipe.getResult().clone();
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        Requirement requirement = this.requirementCheck.check(p, recipe.getKey());
        if (requirement != null) {
            lore.add(this.text(l.choose("Requisito: ", "Requirement: ")
                    + l.choose(requirement.labelPt(), requirement.labelEn()), requirement.met() ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(this.text(requirement.met()
                    ? l.choose("Requisito cumprido!", "Requirement met!")
                    : l.choose("Requisito não cumprido.", "Requirement not met."), requirement.met() ? NamedTextColor.GREEN : NamedTextColor.RED));
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
