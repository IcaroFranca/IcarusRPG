package dev.icaro.foodtooltips.collections;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.util.ArrayList;
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

    private final Consumer<Player> back;
    private final Map<UUID, Map<Integer, CollectionsMilestone>> viewing = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();

    public CollectionsItemsMenuService(Consumer<Player> back) {
        this.back = back;
    }

    public void open(Player p, int wanted) {
        List<CollectionsMilestone> all = givableMilestones();
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) PER_PAGE));
        int page = Math.max(0, Math.min(pages - 1, wanted));
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, 54, l.choose("Itens de Coleções", "Collections Items") + " • " + (page + 1) + "/" + pages);
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
        inv.setItem(49, this.customHead(HeadTexture.BACK, l.choose("Voltar", "Back"), List.of()));
        if (page > 0) {
            inv.setItem(47, this.customHead(HeadTexture.ARROW_LEFT, l.choose("Página anterior", "Previous Page"), List.of()));
        }
        if (page + 1 < pages) {
            inv.setItem(51, this.customHead(HeadTexture.ARROW_RIGHT, l.choose("Próxima página", "Next Page"), List.of()));
        }
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewing.put(p.getUniqueId(), buttons);
        this.pages.put(p.getUniqueId(), page);
    }

    public boolean viewing(Player p) {
        return this.viewing.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.pages.remove(p.getUniqueId());
    }

    public void click(Player p, int slot) {
        Map<Integer, CollectionsMilestone> shown = this.viewing.get(p.getUniqueId());
        if (shown == null) {
            return;
        }
        int page = this.pages.getOrDefault(p.getUniqueId(), 0);
        if (slot == 49) {
            this.close(p);
            this.back.accept(p);
            return;
        }
        if (slot == 47) {
            this.open(p, page - 1);
            return;
        }
        if (slot == 51) {
            this.open(p, page + 1);
            return;
        }
        CollectionsMilestone milestone = shown.get(slot);
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
        p.sendMessage(Component.text(l.choose("Recebido: ", "Received: ") + milestone.reward(l == Language.PT), NamedTextColor.GREEN));
    }

    /** Every {@link RewardKind#RECIPE_UNLOCK} milestone across the whole catalog whose own recipe(s) resolve to a real {@link Recipe} right now - recomputed on every open rather than cached, since which recipes exist can change across a {@code /reload}. */
    private static List<CollectionsMilestone> givableMilestones() {
        List<CollectionsMilestone> out = new ArrayList<>();
        for (CollectionsEntry entry : CollectionsCatalog.entries()) {
            for (CollectionsMilestone milestone : entry.milestones()) {
                if (milestone.kind() != RewardKind.RECIPE_UNLOCK || milestone.recipes().isEmpty()) {
                    continue;
                }
                boolean resolvable = milestone.recipes().stream().anyMatch(key -> Bukkit.getRecipe(key) != null);
                if (resolvable) {
                    out.add(milestone);
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
            lore.add(this.text(l.choose("Dá o set completo (" + milestone.recipes().size() + " peças).", "Gives the full set (" + milestone.recipes().size() + " pieces)."), NamedTextColor.YELLOW));
        }
        lore.add(this.text(l.choose("Clique para receber.", "Click to receive."), NamedTextColor.YELLOW));
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
