package dev.icaro.foodtooltips.collections;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The {@code /rpgitems} admin menu's own "Collections Items" page - every {@link
 * RewardKind#RECIPE_UNLOCK} milestone across the whole {@link CollectionsCatalog} whose own
 * recipe(s) actually resolve to a real registered {@link Recipe} (skipping enchant discounts,
 * plain XP milestones, and the handful of "unlocked in name only" ones with no real recipe to
 * gate - a PotionMix like the Adrenaline/Resistance Potions, or Sprout Armor's own "recipe
 * coming soon"), one tile per milestone (a 4-piece armor set's own milestone is ONE tile that
 * gives every piece at once, same "whole set, one click" shape {@code
 * LegendaryItemsMenuService#armorSetPreview} already uses for Miner's/Lapis Lazuli Armor).
 *
 * <p>{@link #open} opens a {@link CollectionsCategory} picker rather than a single flat list -
 * per the player's own "quero uma espécie de separação e filtragem para não passar tantas
 * páginas" (screenshot of this page's old single 28-per-page flat grid), same "category picker
 * before the paginated list" shape {@code crafting.RecipeBookMenuService#open}/{@code
 * #openCategory} already uses for its own, near-identical "every unlocked recipe" problem;
 * {@link #openCategory} is the actual (now per-category) paginated list this class used to show
 * at the top level. A category only appears on the picker if it actually has at least one
 * givable milestone right now, same "don't show an empty bucket" rule {@code
 * RecipeBookMenuService#open} applies to its own {@code OTHER} category.
 *
 * <p>Deliberately reads straight from {@link Bukkit#getRecipe} rather than keeping its own
 * registry of "every item this plugin can ever unlock" - the moment a new Collections entry
 * with a real recipe is added anywhere (Farming today, Mining/Foraging/Combat/Fishing later -
 * see {@code CollectionsCatalog}'s own doc), it shows up here automatically, with zero extra
 * wiring, the same reasoning {@code CollectionsMenuService#recipePreview} already uses for its
 * own milestone tile icons.
 */
public final class CollectionsItemsMenuService {
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int PER_PAGE = SLOTS.length;
    /** One per {@link CollectionsCategory} (declaration order), centered on the picker screen's own row - same row {@code crafting.RecipeBookMenuService#CATEGORY_SLOTS} uses, narrowed from 7 to 5 since this catalog has no "Other" bucket. */
    private static final int[] CATEGORY_SLOTS = {20, 21, 22, 23, 24};
    private static final int BACK_SLOT = 49;
    private static final int PREV_SLOT = 47;
    private static final int NEXT_SLOT = 51;

    /** Pairs a givable milestone with the {@link CollectionsEntry#category()} it came from, since {@link CollectionsMilestone} itself carries no category - see {@link #givableMilestones()}. */
    private record CategorizedMilestone(CollectionsCategory category, CollectionsMilestone milestone) {
    }

    /** {@code category == null} means the picker screen; {@code page} is only meaningful once a category is picked. */
    private record View(CollectionsCategory category, int page) {
    }

    private final Consumer<Player> back;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, Map<Integer, CollectionsMilestone>> viewing = new HashMap<>();

    public CollectionsItemsMenuService(Consumer<Player> back) {
        this.back = back;
    }

    /** The category picker - one button per {@link CollectionsCategory} with at least one givable milestone right now. */
    public void open(Player p) {
        Language l = Language.of(p);
        Map<CollectionsCategory, Integer> counts = new EnumMap<>(CollectionsCategory.class);
        for (CategorizedMilestone cm : givableMilestones()) {
            counts.merge(cm.category(), 1, Integer::sum);
        }
        Inventory inv = Bukkit.createInventory(null, 54, "Collections Items");
        this.fill(inv);
        CollectionsCategory[] categories = CollectionsCategory.values();
        for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
            CollectionsCategory category = categories[i];
            int count = counts.getOrDefault(category, 0);
            if (count == 0) {
                continue;
            }
            List<Component> lore = List.of(
                    this.text(count + " givable item" + (count == 1 ? "" : "s"), NamedTextColor.GRAY),
                    this.text("Click to view!", NamedTextColor.YELLOW));
            inv.setItem(CATEGORY_SLOTS[i], this.item(category.icon(), category.display(l == Language.PT), lore));
        }
        inv.setItem(BACK_SLOT, this.customHead(HeadTexture.BACK, "Back", List.of()));
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.views.put(p.getUniqueId(), new View(null, 0));
        this.viewing.remove(p.getUniqueId());
    }

    /** {@code category}'s own paginated list of givable milestones - what used to be this class's single top-level list before it was split by {@link CollectionsCategory}. */
    public void openCategory(Player p, CollectionsCategory category, int wanted) {
        List<CollectionsMilestone> all = givableMilestones().stream()
                .filter(cm -> cm.category() == category)
                .map(CategorizedMilestone::milestone)
                .toList();
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) PER_PAGE));
        int page = Math.max(0, Math.min(pages - 1, wanted));
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, 54,
                category.display(l == Language.PT) + " • " + (page + 1) + "/" + pages);
        this.fill(inv);
        int from = page * PER_PAGE;
        int to = Math.min(from + PER_PAGE, all.size());
        Map<Integer, CollectionsMilestone> buttons = new HashMap<>();
        for (int i = from; i < to; i++) {
            CollectionsMilestone milestone = all.get(i);
            int slot = SLOTS[i - from];
            inv.setItem(slot, this.preview(milestone, l));
            buttons.put(slot, milestone);
        }
        inv.setItem(BACK_SLOT, this.customHead(HeadTexture.BACK, "Back", List.of()));
        if (page > 0) {
            inv.setItem(PREV_SLOT, this.customHead(HeadTexture.ARROW_LEFT, "Previous Page", List.of()));
        }
        if (page + 1 < pages) {
            inv.setItem(NEXT_SLOT, this.customHead(HeadTexture.ARROW_RIGHT, "Next Page", List.of()));
        }
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.views.put(p.getUniqueId(), new View(category, page));
        this.viewing.put(p.getUniqueId(), buttons);
    }

    public boolean viewing(Player p) {
        return this.views.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        this.views.remove(p.getUniqueId());
        this.viewing.remove(p.getUniqueId());
    }

    public void click(Player p, int slot) {
        View view = this.views.get(p.getUniqueId());
        if (view == null) {
            return;
        }
        if (view.category() == null) {
            if (slot == BACK_SLOT) {
                this.close(p);
                this.back.accept(p);
                return;
            }
            CollectionsCategory[] categories = CollectionsCategory.values();
            for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
                if (CATEGORY_SLOTS[i] == slot) {
                    this.openCategory(p, categories[i], 0);
                    return;
                }
            }
            return;
        }
        if (slot == BACK_SLOT) {
            this.open(p);
            return;
        }
        if (slot == PREV_SLOT) {
            this.openCategory(p, view.category(), view.page() - 1);
            return;
        }
        if (slot == NEXT_SLOT) {
            this.openCategory(p, view.category(), view.page() + 1);
            return;
        }
        Map<Integer, CollectionsMilestone> shown = this.viewing.get(p.getUniqueId());
        CollectionsMilestone milestone = shown == null ? null : shown.get(slot);
        if (milestone == null) {
            return;
        }
        Language l = Language.of(p);
        for (NamespacedKey key : milestone.recipes()) {
            Recipe recipe = Bukkit.getRecipe(key);
            if (recipe == null) {
                continue;
            }
            for (ItemStack overflow : p.getInventory().addItem(recipe.getResult().clone()).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
        p.sendMessage(Component.text("Received: " + milestone.reward(l == Language.PT), NamedTextColor.GREEN));
    }

    /** Every {@link RewardKind#RECIPE_UNLOCK} milestone across the whole catalog whose own recipe(s) resolve to a real {@link Recipe} right now, each paired with its parent entry's own {@link CollectionsCategory} - recomputed on every open rather than cached, since which recipes exist can change across a {@code /reload}. */
    private static List<CategorizedMilestone> givableMilestones() {
        List<CategorizedMilestone> out = new ArrayList<>();
        for (CollectionsEntry entry : CollectionsCatalog.entries()) {
            for (CollectionsMilestone milestone : entry.milestones()) {
                if (milestone.kind() != RewardKind.RECIPE_UNLOCK || milestone.recipes().isEmpty()) {
                    continue;
                }
                boolean resolvable = milestone.recipes().stream().anyMatch(key -> Bukkit.getRecipe(key) != null);
                if (resolvable) {
                    out.add(new CategorizedMilestone(entry.category(), milestone));
                }
            }
        }
        return out;
    }

    private ItemStack preview(CollectionsMilestone milestone, Language l) {
        ItemStack result = null;
        for (NamespacedKey key : milestone.recipes()) {
            Recipe recipe = Bukkit.getRecipe(key);
            if (recipe != null) {
                result = recipe.getResult().clone();
                break;
            }
        }
        if (result == null) {
            return this.item(Material.BARRIER, "?", List.of());
        }
        ItemMeta meta = result.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        if (milestone.recipes().size() > 1) {
            lore.add(this.text(("Gives the full set (" + milestone.recipes().size() + " pieces)."), NamedTextColor.YELLOW));
        }
        lore.add(this.text("Click to receive.", NamedTextColor.YELLOW));
        meta.lore(lore);
        result.setItemMeta(meta);
        return result;
    }

    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            var profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        m.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private void fill(Inventory inv) {
        ItemStack f = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int s = 0; s < inv.getSize(); s++) {
            inv.setItem(s, f);
        }
    }

    private ItemStack item(Material mat, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
}
