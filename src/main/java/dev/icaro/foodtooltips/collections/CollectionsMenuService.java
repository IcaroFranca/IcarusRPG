package dev.icaro.foodtooltips.collections;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.crafting.RecipeBookMenuService;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.util.LoreWrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The Collections screen - categories (one per {@link CollectionsCategory}) -> a category's
 * own entries -> one entry's milestone ladder, three screens deep exactly like {@code
 * bestiary.BestiaryMenuService} (same {@code centeredSlots}/pagination/Back-button
 * conventions, same {@code View}/{@code ViewType} state map, copied here rather than shared
 * since the two catalogs' data shapes differ enough that a common base would need more
 * indirection than it saves). Opened from the Skills menu's own Collections button ({@code
 * SkillsMenuService}, slot 20).
 *
 * <p>{@link #openEntry} shows every milestone with no real item behind it (an XP/enchant-
 * discount/feature-unlock milestone) as a stained glass pane colored by its own status - red
 * for locked, yellow for the one milestone currently in progress (the first not yet reached;
 * every milestone past it is still red even though the same collected count is quietly
 * working toward it too), green for already reached - per the player's own original "painél
 * de vidro vermelho para bloqueados, amarelo para os que estão em progresso e verde para os
 * liberados" spec. A milestone with a real recipe behind it ({@link #previewItems}, the same
 * generic {@link Bukkit#getRecipe} lookup {@code CollectionsItemsMenuService} uses for its own
 * {@code /rpgitems} tiles) shows that real item instead, in every status - locked and
 * in-progress included - per the player's own later "mostrar o item sempre, em qualquer
 * status" follow-up, which retired the pane convention for exactly these milestones; the
 * item's own stat lore stays intact, with the same status lines the pane used to carry
 * appended below it ({@link #withMilestoneStatusLore}). Either way, clicking a milestone tile
 * that has a real item still opens a dedicated, bigger preview screen ({@link
 * #openItemPreview} - useful for a 4-piece armor set's milestone, which only has room for one
 * of its four pieces as the ladder tile's own icon). Clicking one of {@link #openItemPreview}'s
 * own item tiles goes one level deeper still,
 * opening that exact recipe's real shape via {@link RecipeBookMenuService#openDetail(Player,
 * NamespacedKey, Runnable)} - the same read-only 3x3-grid screen the Recipe Book itself uses,
 * reused rather than duplicated here, with its own Back button wired to reopen this same
 * preview screen instead of the Recipe Book's own list.
 */
public final class CollectionsMenuService {
    private final CollectionsProgressService progress;
    private final GlobalLevelService global;
    private final Consumer<Player> back;
    private final Map<UUID, View> viewers = new HashMap<>();
    private RecipeBookMenuService recipeBook;

    public CollectionsMenuService(CollectionsProgressService progress, GlobalLevelService global, Consumer<Player> back) {
        this.progress = progress;
        this.global = global;
        this.back = back;
    }

    /** Late-bound, same "no direct constructor dependency" shape every cross-menu wiring in this plugin uses ({@code SkillsMenuService#reforge}, etc.) - lets a real item preview tile in {@link #openItemPreview} open that recipe's actual shape via {@link RecipeBookMenuService#openDetail(Player, NamespacedKey, Runnable)}. */
    public void recipeBook(RecipeBookMenuService recipeBook) {
        this.recipeBook = recipeBook;
    }

    public void openCategories(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, 54, "Collection Categories");
        this.fill(inv);
        CollectionsCategory[] categories = CollectionsCategory.values();
        List<Integer> slots = this.centeredSlots(categories.length);
        Map<Integer, CollectionsCategory> buttons = new HashMap<>();
        for (int i = 0; i < categories.length; i++) {
            CollectionsCategory c = categories[i];
            int slot = slots.get(i);
            List<Component> lore = List.of(
                    this.text(CollectionsCatalog.entries(c).size() + " " + "catalogued items", NamedTextColor.GRAY),
                    this.text("Click to open!", NamedTextColor.YELLOW));
            inv.setItem(slot, this.item(c.icon(), c.display(l == Language.PT), lore));
            buttons.put(slot, c);
        }
        inv.setItem(49, this.customHead(HeadTexture.BACK, "Back to Skills", List.of()));
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewers.put(p.getUniqueId(), View.categories(buttons));
    }

    public void openCategory(Player p, CollectionsCategory cat, int wanted) {
        List<CollectionsEntry> all = CollectionsCatalog.entries(cat);
        int pages = Math.max(1, (int) Math.ceil(all.size() / 45.0));
        int page = Math.max(0, Math.min(pages - 1, wanted));
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, 54, cat.display(l == Language.PT) + " • " + (page + 1) + "/" + pages);
        this.fill(inv);
        int from = page * 45;
        int to = Math.min(from + 45, all.size());
        List<Integer> slots = this.centeredSlots(to - from);
        Map<Integer, CollectionsEntry> buttons = new HashMap<>();
        for (int i = from; i < to; i++) {
            int slot = slots.get(i - from);
            CollectionsEntry entry = all.get(i);
            inv.setItem(slot, this.entryItem(p, entry, l));
            buttons.put(slot, entry);
        }
        inv.setItem(49, this.customHead(HeadTexture.BACK, "Back to categories", List.of()));
        if (page > 0) {
            inv.setItem(47, this.customHead(HeadTexture.ARROW_LEFT, "Previous Page", List.of()));
        }
        if (page + 1 < pages) {
            inv.setItem(51, this.customHead(HeadTexture.ARROW_RIGHT, "Next Page", List.of()));
        }
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewers.put(p.getUniqueId(), View.category(cat, page, buttons));
    }

    private static final int[] MILESTONE_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 40};

    public void openEntry(Player p, CollectionsEntry entry, CollectionsCategory back, int page) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, 54, entry.displayName(l == Language.PT) + " • Milestones");
        this.fill(inv);
        int collected = this.progress.collected(p, entry);
        int done = this.progress.achieved(p, entry);
        int max = this.progress.maxMilestones(entry);
        List<Component> summaryLore = new ArrayList<>();
        summaryLore.add(this.text("Collected: " + collected, NamedTextColor.GREEN));
        summaryLore.add(this.text("Milestones completed: " + done + "/" + max, NamedTextColor.GOLD));
        inv.setItem(4, this.item(entry.drop(), entry.displayName(l == Language.PT), summaryLore));
        Map<Integer, CollectionsMilestone> milestoneButtons = new HashMap<>();
        for (int i = 0; i < MILESTONE_SLOTS.length && i < max; i++) {
            CollectionsMilestone milestone = entry.milestones().get(i);
            boolean unlocked = i < done;
            boolean inProgress = i == done;
            int remaining = Math.max(0, milestone.threshold() - collected);
            List<Component> lore = new ArrayList<>();
            if (unlocked) {
                lore.add(this.text("Reached at " + milestone.threshold(), NamedTextColor.GRAY));
            } else {
                lore.add(this.text("Need " + remaining + " " + "more to complete", NamedTextColor.GRAY));
            }
            NamedTextColor rewardColor = unlocked ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            for (String part : LoreWrap.wrapText(milestone.reward(l == Language.PT), LoreWrap.DEFAULT_WIDTH)) {
                lore.add(this.text(part, rewardColor));
            }
            lore.add(this.text("+" + this.global.milestoneXp() + " " + "Global Level XP", NamedTextColor.AQUA));
            lore.add(this.text(unlocked ? "COMPLETED" : "LOCKED", unlocked ? NamedTextColor.GREEN : NamedTextColor.RED));
            List<ItemStack> preview = this.previewItems(milestone);
            if (preview.isEmpty()) {
                // Enchant-discount milestones (custom or vanilla) get a representative
                // Enchanted Book instead of the generic status pane, per the player's own
                // explicit "coloque um livro encantado para representar" - named after
                // whichever enchant it discounts, same status lore the pane used to carry.
                // Deliberately not added to milestoneButtons below - there's no recipe/item
                // preview screen behind it, same as the pane it replaces.
                boolean enchantDiscount = milestone.kind() == RewardKind.ENCHANT_DISCOUNT || milestone.kind() == RewardKind.VANILLA_ENCHANT_DISCOUNT;
                ItemStack icon;
                if (enchantDiscount) {
                    icon = this.enchantBookIcon(milestone, lore);
                } else {
                    Material pane = unlocked ? Material.LIME_STAINED_GLASS_PANE
                            : inProgress ? Material.YELLOW_STAINED_GLASS_PANE
                            : Material.RED_STAINED_GLASS_PANE;
                    icon = this.item(pane, "Milestone " + (i + 1), lore);
                }
                inv.setItem(MILESTONE_SLOTS[i], icon);
                continue;
            }
            // A real recipe reward shows the actual item (its own name/stat lore kept intact,
            // this milestone's own status lore appended below it) instead of a status-colored
            // pane, regardless of locked/in-progress/unlocked - per the player's own explicit
            // "mostrar o item sempre, em qualquer status" spec, which retires the pane's own
            // red/yellow/green convention for exactly these milestones (everything else, XP/
            // enchant-discount/feature-unlock milestones with no physical item, still uses it).
            inv.setItem(MILESTONE_SLOTS[i], this.withMilestoneStatusLore(preview.get(0), lore));
            milestoneButtons.put(MILESTONE_SLOTS[i], milestone);
        }
        inv.setItem(49, this.customHead(HeadTexture.BACK, "Back", List.of()));
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewers.put(p.getUniqueId(), View.detail(back, page, entry, milestoneButtons));
    }

    public boolean viewing(Player p) {
        return this.viewers.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewers.remove(p.getUniqueId());
    }

    public void click(Player p, int slot) {
        View v = this.viewers.get(p.getUniqueId());
        if (v == null) {
            return;
        }
        if (v.type() == ViewType.CATEGORIES) {
            CollectionsCategory c = v.categoryButtons().get(slot);
            if (c != null) {
                this.openCategory(p, c, 0);
            } else if (slot == 49) {
                this.viewers.remove(p.getUniqueId());
                this.back.accept(p);
            }
            return;
        }
        if (v.type() == ViewType.DETAIL) {
            CollectionsMilestone milestone = v.milestoneButtons().get(slot);
            if (milestone != null) {
                this.openItemPreview(p, milestone, v.entry(), v.category(), v.page());
            } else if (slot == 49) {
                this.openCategory(p, v.category(), v.page());
            }
            return;
        }
        if (v.type() == ViewType.ITEM_PREVIEW) {
            NamespacedKey key = v.recipeButtons().get(slot);
            if (key != null) {
                this.recipeBook.openDetail(p, key, () -> this.openItemPreview(p, v.milestone(), v.entry(), v.category(), v.page()));
            } else if (slot == 49) {
                this.openEntry(p, v.entry(), v.category(), v.page());
            }
            return;
        }
        CollectionsEntry selected = v.entryButtons().get(slot);
        if (selected != null) {
            this.openEntry(p, selected, v.category(), v.page());
        } else if (slot == 47) {
            this.openCategory(p, v.category(), v.page() - 1);
        } else if (slot == 51) {
            this.openCategory(p, v.category(), v.page() + 1);
        } else if (slot == 49) {
            this.openCategories(p);
        }
    }

    /** Every one of {@code milestone}'s own {@link CollectionsMilestone#recipes} that resolves to a real registered {@link Recipe} right now, as fresh clones (never the live registered instance) - a 4-piece armor set's own milestone returns all 4, in the same order the catalog lists them. Empty for anything with no real recipe to preview (an XP/enchant-discount milestone, a PotionMix, or a feature unlock with no physical item at all). */
    private List<ItemStack> previewItems(CollectionsMilestone milestone) {
        List<ItemStack> items = new ArrayList<>();
        for (NamespacedKey key : milestone.recipes()) {
            Recipe recipe = Bukkit.getRecipe(key);
            if (recipe != null) {
                items.add(recipe.getResult().clone());
            }
        }
        return items;
    }

    /** An {@link Material#ENCHANTED_BOOK} icon named after whichever enchant {@code milestone} discounts (resolved from {@link CollectionsMilestone#discountEnchant}/{@link CollectionsMilestone#vanillaDiscountEnchant} depending on {@link CollectionsMilestone#kind}), carrying the same status lore the generic pane it replaces would have - see this class's own doc at the {@link #openEntry} call site. */
    private ItemStack enchantBookIcon(CollectionsMilestone milestone, List<Component> lore) {
        String name = milestone.kind() == RewardKind.VANILLA_ENCHANT_DISCOUNT
                ? this.vanillaEnchantName(milestone.vanillaDiscountEnchant())
                : milestone.discountEnchant().displayName(false);
        return this.item(Material.ENCHANTED_BOOK, name, lore);
    }

    /** {@code key}'s own real vanilla display name (Paper resolves it server-side, same locale-independent trick {@code enchant.VanillaEnchantEntry#catalogName} already uses), or a generic fallback if the registry somehow doesn't know it. */
    private String vanillaEnchantName(NamespacedKey key) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(key);
        return enchantment == null ? "Enchantment" : PlainTextComponentSerializer.plainText().serialize(enchantment.displayName(1));
    }

    /** The same filter as {@link #previewItems}, same order, but the recipe keys themselves - used to open a real recipe's shape ({@link RecipeBookMenuService#openDetail(Player, NamespacedKey, Runnable)}) from {@link #openItemPreview}'s own tiles. */
    private List<NamespacedKey> previewKeys(CollectionsMilestone milestone) {
        List<NamespacedKey> keys = new ArrayList<>();
        for (NamespacedKey key : milestone.recipes()) {
            if (Bukkit.getRecipe(key) != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * Opens a dedicated read-only screen showing every real item {@code milestone} unlocks
     * (all 4 pieces at once for an armor set, not just one) - each already carrying its own
     * real stat lore straight from {@code item.FarmingCollectionsItemsService}'s own item
     * builders, per the player's own "uma tela com o item ou itens... com seus status" spec.
     * Clicking a milestone tile in {@link #openEntry} that has no real preview (an XP/enchant-
     * discount milestone) simply does nothing, same as before this existed.
     */
    public void openItemPreview(Player p, CollectionsMilestone milestone, CollectionsEntry entry, CollectionsCategory back, int page) {
        Language l = Language.of(p);
        List<ItemStack> items = this.previewItems(milestone);
        List<NamespacedKey> keys = this.previewKeys(milestone);
        Inventory inv = Bukkit.createInventory(null, 54, "Item Preview");
        this.fill(inv);
        List<Integer> slots = this.centeredSlots(items.size());
        Map<Integer, NamespacedKey> recipeButtons = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            int slot = slots.get(i);
            inv.setItem(slot, this.withRecipeHint(items.get(i), l));
            recipeButtons.put(slot, keys.get(i));
        }
        inv.setItem(49, this.customHead(HeadTexture.BACK, "Back", List.of()));
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewers.put(p.getUniqueId(), View.itemPreview(back, page, entry, milestone, recipeButtons));
    }

    /** Appends {@code statusLore} (the "Reached at/Need N more", reward text, Global XP, LOCKED/COMPLETED lines {@link #openEntry} already built for this milestone) below {@code item}'s own existing lore, separated by a blank line - keeps the crafted item's own real stat lore fully intact while still showing the ladder's own per-milestone status underneath it. Mutates and returns the same instance, which is already a fresh clone from {@link #previewItems}. */
    private ItemStack withMilestoneStatusLore(ItemStack item, List<Component> statusLore) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.addAll(statusLore);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Appends a "click to see the recipe" hint to {@code item}'s own lore - every tile in {@link #openItemPreview} now opens the real recipe shape on click (see {@link #recipeBook}). Mutates and returns the same instance, which is already a fresh clone from {@link #previewItems}. */
    private ItemStack withRecipeHint(ItemStack item, Language l) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.add(this.text("Click to see the recipe.", NamedTextColor.YELLOW));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack entryItem(Player p, CollectionsEntry e, Language l) {
        List<Component> lore = new ArrayList<>();
        lore.add(this.text("Collected: " + this.progress.collected(p, e), NamedTextColor.GREEN));
        lore.add(this.text("Milestones: " + this.progress.achieved(p, e) + "/" + this.progress.maxMilestones(e), NamedTextColor.GOLD));
        lore.add(Component.empty());
        lore.add(this.text("Click to view milestones!", NamedTextColor.YELLOW));
        return this.item(e.drop(), e.displayName(l == Language.PT), lore);
    }

    private List<Integer> centeredSlots(int count) {
        List<Integer> out = new ArrayList<>();
        if (count <= 0) {
            return out;
        }
        int rows = Math.min(5, (int) Math.ceil(count / 7.0));
        int firstRow = (5 - rows) / 2;
        int base = count / rows;
        int extra = count % rows;
        for (int row = 0; row < rows; row++) {
            int amount = base + (row < extra ? 1 : 0);
            int firstColumn = (9 - amount) / 2;
            for (int col = 0; col < amount; col++) {
                out.add((firstRow + row) * 9 + firstColumn + col);
            }
        }
        return out;
    }

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

    private record View(ViewType type, CollectionsCategory category, int page, CollectionsEntry entry, CollectionsMilestone milestone,
                         Map<Integer, CollectionsCategory> categoryButtons, Map<Integer, CollectionsEntry> entryButtons,
                         Map<Integer, CollectionsMilestone> milestoneButtons, Map<Integer, NamespacedKey> recipeButtons) {
        static View categories(Map<Integer, CollectionsCategory> b) {
            return new View(ViewType.CATEGORIES, null, 0, null, null, b, Map.of(), Map.of(), Map.of());
        }

        static View category(CollectionsCategory c, int p, Map<Integer, CollectionsEntry> b) {
            return new View(ViewType.CATEGORY, c, p, null, null, Map.of(), b, Map.of(), Map.of());
        }

        static View detail(CollectionsCategory c, int p, CollectionsEntry e, Map<Integer, CollectionsMilestone> milestoneButtons) {
            return new View(ViewType.DETAIL, c, p, e, null, Map.of(), Map.of(), milestoneButtons, Map.of());
        }

        static View itemPreview(CollectionsCategory c, int p, CollectionsEntry e, CollectionsMilestone m, Map<Integer, NamespacedKey> recipeButtons) {
            return new View(ViewType.ITEM_PREVIEW, c, p, e, m, Map.of(), Map.of(), Map.of(), recipeButtons);
        }
    }

    private enum ViewType {
        CATEGORIES, CATEGORY, DETAIL, ITEM_PREVIEW
    }
}
