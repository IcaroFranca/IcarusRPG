package dev.icaro.foodtooltips.collections;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
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
 * The Collections screen - categories (one per {@link CollectionsCategory}) -> a category's
 * own entries -> one entry's milestone ladder, three screens deep exactly like {@code
 * bestiary.BestiaryMenuService} (same {@code centeredSlots}/pagination/Back-button
 * conventions, same {@code View}/{@code ViewType} state map, copied here rather than shared
 * since the two catalogs' data shapes differ enough that a common base would need more
 * indirection than it saves). Opened from the Skills menu's own Collections button ({@code
 * SkillsMenuService}, slot 19).
 *
 * <p>{@link #openEntry} shows every milestone as a stained glass pane colored by its own
 * status - red for locked, yellow for the one milestone currently in progress (the first
 * not yet reached; every milestone past it is still red even though the same collected
 * count is quietly working toward it too), green for already reached - per the player's
 * own "painél de vidro vermelho para bloqueados, amarelo para os que estão em progresso e
 * verde para os liberados" spec. A milestone with a real recipe behind it ({@link
 * #previewItems}, the same generic {@link Bukkit#getRecipe} lookup {@code
 * CollectionsItemsMenuService} uses for its own {@code /rpgitems} tiles) still opens a
 * dedicated preview screen ({@link #openItemPreview}) on click - the pane is only what
 * represents the milestone in the ladder itself, not a replacement for seeing the real item.
 */
public final class CollectionsMenuService {
    private final CollectionsProgressService progress;
    private final GlobalLevelService global;
    private final Consumer<Player> back;
    private final Map<UUID, View> viewers = new HashMap<>();

    public CollectionsMenuService(CollectionsProgressService progress, GlobalLevelService global, Consumer<Player> back) {
        this.progress = progress;
        this.global = global;
        this.back = back;
    }

    public void openCategories(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, 54, l.choose("Categorias de Coleções", "Collection Categories"));
        this.fill(inv);
        CollectionsCategory[] categories = CollectionsCategory.values();
        List<Integer> slots = this.centeredSlots(categories.length);
        Map<Integer, CollectionsCategory> buttons = new HashMap<>();
        for (int i = 0; i < categories.length; i++) {
            CollectionsCategory c = categories[i];
            int slot = slots.get(i);
            List<Component> lore = List.of(
                    this.text(CollectionsCatalog.entries(c).size() + " " + l.choose("itens catalogados", "catalogued items"), NamedTextColor.GRAY),
                    this.text(l.choose("Clique para abrir!", "Click to open!"), NamedTextColor.YELLOW));
            inv.setItem(slot, this.item(c.icon(), c.display(l == Language.PT), lore));
            buttons.put(slot, c);
        }
        inv.setItem(49, this.customHead(HeadTexture.BACK, l.choose("Voltar às Skills", "Back to Skills"), List.of()));
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
        inv.setItem(49, this.customHead(HeadTexture.BACK, l.choose("Voltar às categorias", "Back to categories"), List.of()));
        if (page > 0) {
            inv.setItem(47, this.customHead(HeadTexture.ARROW_LEFT, l.choose("Página anterior", "Previous Page"), List.of()));
        }
        if (page + 1 < pages) {
            inv.setItem(51, this.customHead(HeadTexture.ARROW_RIGHT, l.choose("Próxima página", "Next Page"), List.of()));
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
        summaryLore.add(this.text(l.choose("Coletado: ", "Collected: ") + collected, NamedTextColor.GREEN));
        summaryLore.add(this.text(l.choose("Milestones concluídas: ", "Milestones completed: ") + done + "/" + max, NamedTextColor.GOLD));
        inv.setItem(4, this.item(entry.drop(), entry.displayName(l == Language.PT), summaryLore));
        Map<Integer, CollectionsMilestone> milestoneButtons = new HashMap<>();
        for (int i = 0; i < MILESTONE_SLOTS.length && i < max; i++) {
            CollectionsMilestone milestone = entry.milestones().get(i);
            boolean unlocked = i < done;
            boolean inProgress = i == done;
            int remaining = Math.max(0, milestone.threshold() - collected);
            List<Component> lore = new ArrayList<>();
            if (unlocked) {
                lore.add(this.text(l.choose("Concluído em ", "Reached at ") + milestone.threshold(), NamedTextColor.GRAY));
            } else {
                lore.add(this.text(l.choose("Faltam ", "Need ") + remaining + " " + l.choose("para completar", "more to complete"), NamedTextColor.GRAY));
            }
            lore.add(this.text(milestone.reward(l == Language.PT), unlocked ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            lore.add(this.text("+" + this.global.milestoneXp() + " " + l.choose("XP de Nível Global", "Global Level XP"), NamedTextColor.AQUA));
            lore.add(this.text(unlocked ? l.choose("CONCLUÍDA", "COMPLETED") : l.choose("BLOQUEADA", "LOCKED"), unlocked ? NamedTextColor.GREEN : NamedTextColor.RED));
            Material pane = unlocked ? Material.LIME_STAINED_GLASS_PANE
                    : inProgress ? Material.YELLOW_STAINED_GLASS_PANE
                    : Material.RED_STAINED_GLASS_PANE;
            inv.setItem(MILESTONE_SLOTS[i], this.item(pane, "Milestone " + (i + 1), lore));
            if (!this.previewItems(milestone).isEmpty()) {
                milestoneButtons.put(MILESTONE_SLOTS[i], milestone);
            }
        }
        inv.setItem(49, this.customHead(HeadTexture.BACK, l.choose("Voltar", "Back"), List.of()));
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
            if (slot == 49) {
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
        Inventory inv = Bukkit.createInventory(null, 54, l.choose("Prévia do Item", "Item Preview"));
        this.fill(inv);
        List<Integer> slots = this.centeredSlots(items.size());
        for (int i = 0; i < items.size(); i++) {
            inv.setItem(slots.get(i), items.get(i));
        }
        inv.setItem(49, this.customHead(HeadTexture.BACK, l.choose("Voltar", "Back"), List.of()));
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewers.put(p.getUniqueId(), View.itemPreview(back, page, entry));
    }

    private ItemStack entryItem(Player p, CollectionsEntry e, Language l) {
        List<Component> lore = new ArrayList<>();
        lore.add(this.text(l.choose("Coletado: ", "Collected: ") + this.progress.collected(p, e), NamedTextColor.GREEN));
        lore.add(this.text("Milestones: " + this.progress.achieved(p, e) + "/" + this.progress.maxMilestones(e), NamedTextColor.GOLD));
        lore.add(Component.empty());
        lore.add(this.text(l.choose("Clique para ver milestones!", "Click to view milestones!"), NamedTextColor.YELLOW));
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

    private record View(ViewType type, CollectionsCategory category, int page, CollectionsEntry entry,
                         Map<Integer, CollectionsCategory> categoryButtons, Map<Integer, CollectionsEntry> entryButtons,
                         Map<Integer, CollectionsMilestone> milestoneButtons) {
        static View categories(Map<Integer, CollectionsCategory> b) {
            return new View(ViewType.CATEGORIES, null, 0, null, b, Map.of(), Map.of());
        }

        static View category(CollectionsCategory c, int p, Map<Integer, CollectionsEntry> b) {
            return new View(ViewType.CATEGORY, c, p, null, Map.of(), b, Map.of());
        }

        static View detail(CollectionsCategory c, int p, CollectionsEntry e, Map<Integer, CollectionsMilestone> milestoneButtons) {
            return new View(ViewType.DETAIL, c, p, e, Map.of(), Map.of(), milestoneButtons);
        }

        static View itemPreview(CollectionsCategory c, int p, CollectionsEntry e) {
            return new View(ViewType.ITEM_PREVIEW, c, p, e, Map.of(), Map.of(), Map.of());
        }
    }

    private enum ViewType {
        CATEGORIES, CATEGORY, DETAIL, ITEM_PREVIEW
    }
}
