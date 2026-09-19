package dev.icaro.foodtooltips.enchant;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.util.LoreWrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/**
 * The reworked Anvil screen - opened by right-clicking a real Anvil block (any of the
 * 3 damage-stage materials) instead of vanilla's own rename/repair/combine UI, which
 * never opens at all (see {@code AnvilMenuListener}). A plain custom {@link Inventory},
 * not a real {@code AnvilInventory} - there's deliberately no text-input field anywhere
 * in this screen, so renaming an item here is impossible by construction, not by a
 * runtime check.
 *
 * <p>Two modes, switched via the tabs at {@link #COMBINE_TAB_SLOT}/{@link
 * #REFORGE_TAB_SLOT} (per-player, see {@link #modes}):
 * <ul>
 *   <li><b>Combine</b> ({@link Mode#COMBINE}, the default): {@link #MAIN_ITEM_SLOT} takes
 *   the item to keep, {@link #SECONDARY_ITEM_SLOT} takes either another item of the exact
 *   same {@link Material} or a real enchanted book - every enchantment (custom or vanilla)
 *   the secondary item/book offers is checked against the main item the same way the
 *   Enchanting Table itself would ({@link EnchantService#vanillaBlockReason}/{@link
 *   EnchantService#customBlockReason} - same type-compatibility and conflict rules, no
 *   duplicated logic), merged in (matching level bumps it by one, capped at the entry's
 *   own max; a higher level on either side wins) into a live preview at {@link
 *   #PREVIEW_SLOT}. Clicking the preview - when it's a real item, not an explanation -
 *   consumes both inputs and hands the result to the player, exactly like clicking a real
 *   anvil's own output slot.
 *   <li><b>Reforge</b> ({@link Mode#REFORGE}): structure only for now, no functionality -
 *   see the class doc note on {@link #renderReforge}. Deliberately not this class's
 *   concern yet: real vanilla repair/durability restoration is unavailable while this
 *   screen replaces the anvil, until Reforge actually ships.
 * </ul>
 * Switching modes or closing the screen always hands back whatever sits in {@link
 * #MAIN_ITEM_SLOT}/{@link #SECONDARY_ITEM_SLOT} first ({@link #returnInputItems}) -
 * never consumed except by a genuine confirmed Combine.
 */
public final class AnvilMenuService {
    public static final int MAIN_ITEM_SLOT = 29;
    public static final int SECONDARY_ITEM_SLOT = 33;
    public static final int PREVIEW_SLOT = 13;
    public static final int COMBINE_TAB_SLOT = 22;
    public static final int REFORGE_TAB_SLOT = 24;
    public static final int CLOSE_SLOT = 49;

    public enum Mode { COMBINE, REFORGE }

    private final Plugin plugin;
    private final EnchantService enchants;
    private final Set<UUID> viewing = new HashSet<>();
    private final Map<UUID, Mode> modes = new HashMap<>();

    public AnvilMenuService(Plugin plugin, EnchantService enchants) {
        this.plugin = plugin;
        this.enchants = enchants;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Bigorna", "Anvil"));
        this.fill(v);
        v.setItem(MAIN_ITEM_SLOT, null);
        v.setItem(SECONDARY_ITEM_SLOT, null);
        this.modes.put(p.getUniqueId(), Mode.COMBINE);
        this.renderTabsAndClose(v, p);
        this.refreshPreview(p, v);
        p.openInventory(v);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public Mode mode(Player p) {
        return this.modes.getOrDefault(p.getUniqueId(), Mode.COMBINE);
    }

    /** Called by {@code AnvilMenuListener} on {@code InventoryCloseEvent} - hands back any input items before forgetting this player entirely. */
    public void handleClose(Player p, Inventory v) {
        this.returnInputItems(p, v);
        this.viewing.remove(p.getUniqueId());
        this.modes.remove(p.getUniqueId());
    }

    /** Switches to {@code target} (a no-op if already there), returning whatever's in the input slots first - see this class's own doc. Reuses the already-open {@link Inventory} in place, same "update, don't reopen" pattern {@code EnchantMenuService#turnMainPage} uses for pagination. */
    public void switchMode(Player p, Mode target) {
        if (this.mode(p) == target) {
            return;
        }
        Inventory v = p.getOpenInventory().getTopInventory();
        this.returnInputItems(p, v);
        this.modes.put(p.getUniqueId(), target);
        this.renderTabsAndClose(v, p);
        if (target == Mode.COMBINE) {
            v.setItem(MAIN_ITEM_SLOT, null);
            v.setItem(SECONDARY_ITEM_SLOT, null);
            this.refreshPreview(p, v);
        } else {
            this.renderReforge(v, p);
        }
    }

    /** Called (next tick, after the click that changed a slot actually lands) whenever an input slot changes in Combine mode - same deferred-refresh pattern {@code GrindstoneMenuService#scheduleCatalogRefresh} uses. A no-op if the player switched away or closed the screen before this ran. */
    public void scheduleRefresh(Player p) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.viewing.contains(p.getUniqueId()) || this.mode(p) != Mode.COMBINE) {
                return;
            }
            Inventory v = p.getOpenInventory().getTopInventory();
            if (v.getSize() != 54) {
                return;
            }
            this.refreshPreview(p, v);
        });
    }

    /** Clicking the preview slot in Combine mode: a no-op unless it's currently showing a real, valid result (never an explanation or the "waiting for items" hint). Consumes both inputs and hands the result to the player. */
    public void confirm(Player p) {
        if (this.mode(p) != Mode.COMBINE) {
            return;
        }
        Inventory v = p.getOpenInventory().getTopInventory();
        boolean pt = Language.of(p) == Language.PT;
        ItemStack main = v.getItem(MAIN_ITEM_SLOT);
        ItemStack secondary = v.getItem(SECONDARY_ITEM_SLOT);
        CombineOutcome outcome = this.computeCombine(main, secondary, pt);
        if (outcome.preview() == null) {
            return;
        }
        v.setItem(MAIN_ITEM_SLOT, null);
        v.setItem(SECONDARY_ITEM_SLOT, null);
        this.giveOrDrop(p, outcome.preview());
        this.refreshPreview(p, v);
    }

    /** Hands back whatever's sitting in the two input slots (overflow drops on the ground), then clears them - shared by {@link #handleClose} and {@link #switchMode}, the only two ways an in-progress Combine can be abandoned. */
    private void returnInputItems(Player p, Inventory v) {
        ItemStack main = v.getItem(MAIN_ITEM_SLOT);
        ItemStack secondary = v.getItem(SECONDARY_ITEM_SLOT);
        v.setItem(MAIN_ITEM_SLOT, null);
        v.setItem(SECONDARY_ITEM_SLOT, null);
        this.giveOrDrop(p, main);
        this.giveOrDrop(p, secondary);
    }

    private void giveOrDrop(Player p, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        for (ItemStack overflow : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
    }

    private void renderTabsAndClose(Inventory v, Player p) {
        v.setItem(COMBINE_TAB_SLOT, this.combineTabIcon(p));
        v.setItem(REFORGE_TAB_SLOT, this.reforgeTabIcon(p));
        v.setItem(CLOSE_SLOT, this.closeIcon(p));
    }

    private void refreshPreview(Player p, Inventory v) {
        boolean pt = Language.of(p) == Language.PT;
        ItemStack main = v.getItem(MAIN_ITEM_SLOT);
        ItemStack secondary = v.getItem(SECONDARY_ITEM_SLOT);
        v.setItem(PREVIEW_SLOT, this.previewIcon(this.computeCombine(main, secondary, pt), pt));
    }

    /**
     * Structure only, per explicit request - no reforge logic yet. The two item slots
     * are plain filler here (not the real, placeable ones Combine mode uses) so nothing
     * can be dropped into a mode that doesn't process it yet.
     */
    private void renderReforge(Inventory v, Player p) {
        boolean pt = Language.of(p) == Language.PT;
        v.setItem(MAIN_ITEM_SLOT, this.filler());
        v.setItem(SECONDARY_ITEM_SLOT, this.filler());
        v.setItem(PREVIEW_SLOT, this.item(Material.BARRIER, pt ? "Em breve" : "Coming soon",
                List.of(this.text(pt ? "O modo Reforjar ainda não existe." : "Reforge mode doesn't exist yet.", NamedTextColor.GRAY))));
    }

    // ---- Combine mode logic --------------------------------------------------------

    private record CombineOutcome(ItemStack preview, String reason) {
    }

    /**
     * Works out what Combine would produce right now, or why it can't - see this
     * class's own doc for the merge rules. Both {@code main}/{@code secondary} are read
     * only, never mutated - the returned {@link CombineOutcome#preview} (if any) is a
     * fresh clone.
     */
    private CombineOutcome computeCombine(ItemStack main, ItemStack secondary, boolean pt) {
        if (main == null || main.isEmpty() || secondary == null || secondary.isEmpty()) {
            return new CombineOutcome(null, null);
        }
        if (main.getType() == Material.BOOK || main.getType() == Material.ENCHANTED_BOOK) {
            return new CombineOutcome(null, pt
                    ? "O item principal precisa ser um equipamento, não um livro."
                    : "The main item needs to be a piece of equipment, not a book.");
        }
        EnchantmentStorageMeta bookMeta = secondary.getType() == Material.ENCHANTED_BOOK && secondary.getItemMeta() instanceof EnchantmentStorageMeta esm ? esm : null;
        if (bookMeta == null && main.getType() != secondary.getType()) {
            return new CombineOutcome(null, pt
                    ? "Coloque outro item do mesmo tipo ou um livro encantado no segundo slot."
                    : "Place another item of the same type or an enchanted book in the second slot.");
        }

        Map<EnchantEntry, Integer> current = this.enchants.levelsOf(main);
        Map<EnchantEntry, Integer> toApply = new LinkedHashMap<>();
        List<String> failureReasons = new ArrayList<>();
        int considered = 0;

        if (bookMeta != null) {
            for (Map.Entry<Enchantment, Integer> stored : bookMeta.getStoredEnchants().entrySet()) {
                Enchantment enchantment = stored.getKey();
                if (!this.enchants.isOfferable(enchantment)) {
                    continue;
                }
                considered++;
                String reason = this.enchants.vanillaBlockReason(main, enchantment, pt);
                if (reason != null) {
                    failureReasons.add(reason);
                    continue;
                }
                this.mergeIfChanged(current, toApply, new VanillaEnchantEntry(enchantment), stored.getValue());
            }
        } else {
            for (Map.Entry<EnchantEntry, Integer> entry : this.enchants.levelsOf(secondary).entrySet()) {
                EnchantEntry key = entry.getKey();
                considered++;
                String reason = key instanceof VanillaEnchantEntry v
                        ? this.enchants.vanillaBlockReason(main, v.enchantment(), pt)
                        : this.enchants.customBlockReason(main, ((CustomEnchantEntry) key).enchant(), pt);
                if (reason != null) {
                    failureReasons.add(reason);
                    continue;
                }
                this.mergeIfChanged(current, toApply, key, entry.getValue());
            }
        }

        if (toApply.isEmpty()) {
            String reason;
            if (considered == 0) {
                reason = pt ? "Esse item não tem nenhum encantamento para combinar." : "That item has no enchantments to combine.";
            } else if (failureReasons.size() == 1) {
                reason = failureReasons.get(0);
            } else if (!failureReasons.isEmpty()) {
                reason = pt
                        ? "Nenhum dos encantamentos desse item pode ser combinado com o principal."
                        : "None of that item's enchantments can be combined with the main one.";
            } else {
                reason = pt ? "Isso não mudaria o item principal." : "This wouldn't change the main item.";
            }
            return new CombineOutcome(null, reason);
        }

        ItemStack preview = main.clone();
        for (Map.Entry<EnchantEntry, Integer> entry : toApply.entrySet()) {
            this.enchants.setLevel(preview, entry.getKey(), entry.getValue(), pt);
        }
        return new CombineOutcome(preview, null);
    }

    /** Same "matching level bumps by one, otherwise the higher one wins" rule a real vanilla anvil uses, capped at the entry's own max level - only recorded in {@code toApply} if it actually differs from what {@code main} already has (so a redundant lower-level entry from the secondary item never shows up as a change). */
    private void mergeIfChanged(Map<EnchantEntry, Integer> current, Map<EnchantEntry, Integer> toApply, EnchantEntry entry, int incomingLevel) {
        int currentLevel = current.getOrDefault(entry, 0);
        int maxLevel = entry.maxLevel();
        int newLevel = currentLevel <= 0 ? Math.min(incomingLevel, maxLevel)
                : currentLevel == incomingLevel ? Math.min(currentLevel + 1, maxLevel)
                : Math.min(Math.max(currentLevel, incomingLevel), maxLevel);
        if (newLevel != currentLevel) {
            toApply.put(entry, newLevel);
        }
    }

    // ---- Icons ----------------------------------------------------------------------

    private ItemStack previewIcon(CombineOutcome outcome, boolean pt) {
        if (outcome.preview() != null) {
            ItemStack shown = outcome.preview().clone();
            ItemMeta meta = shown.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
            lore.add(Component.empty());
            lore.add(this.text(pt ? "Clique para confirmar a combinação." : "Click to confirm the combination.", NamedTextColor.GREEN));
            meta.lore(lore);
            shown.setItemMeta(meta);
            return shown;
        }
        if (outcome.reason() != null) {
            List<Component> lore = new ArrayList<>();
            for (String part : LoreWrap.wrapText(outcome.reason(), LoreWrap.DEFAULT_WIDTH)) {
                lore.add(this.text(part, NamedTextColor.RED));
            }
            return this.item(Material.BARRIER, pt ? "Combinação inválida" : "Invalid combination", lore);
        }
        return this.item(Material.GRAY_STAINED_GLASS_PANE, pt ? "Aguardando itens" : "Waiting for items", List.of(
                this.text(pt ? "Coloque o item principal no slot 29" : "Place the main item in slot 29", NamedTextColor.GRAY),
                this.text(pt ? "e outro equipamento igual ou um" : "and another matching equipment or an", NamedTextColor.GRAY),
                this.text(pt ? "livro encantado no slot 33." : "enchanted book in slot 33.", NamedTextColor.GRAY)));
    }

    private ItemStack combineTabIcon(Player p) {
        boolean pt = Language.of(p) == Language.PT;
        boolean active = this.mode(p) == Mode.COMBINE;
        List<Component> lore = new ArrayList<>();
        for (String part : LoreWrap.wrapText(pt
                ? "Combina os encantamentos do item principal com os de outro equipamento igual ou de um livro encantado."
                : "Combines the main item's enchantments with another matching equipment's or an enchanted book's.", LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GRAY));
        }
        if (active) {
            lore.add(Component.empty());
            lore.add(this.text(pt ? "Modo ativo" : "Active mode", NamedTextColor.GREEN));
        }
        ItemStack icon = this.item(Material.ANVIL, pt ? "Combinar Itens" : "Combine Items", lore);
        if (active) {
            this.glint(icon);
        }
        return icon;
    }

    private ItemStack reforgeTabIcon(Player p) {
        boolean pt = Language.of(p) == Language.PT;
        boolean active = this.mode(p) == Mode.REFORGE;
        List<Component> lore = new ArrayList<>();
        lore.add(this.text(pt ? "Em breve." : "Coming soon.", NamedTextColor.GRAY));
        if (active) {
            lore.add(Component.empty());
            lore.add(this.text(pt ? "Modo ativo" : "Active mode", NamedTextColor.GREEN));
        }
        ItemStack icon = this.item(Material.DAMAGED_ANVIL, pt ? "Reforjar" : "Reforge", lore);
        if (active) {
            this.glint(icon);
        }
        return icon;
    }

    private ItemStack closeIcon(Player p) {
        boolean pt = Language.of(p) == Language.PT;
        return this.customHead(HeadTexture.CLOSE, pt ? "Fechar" : "Close", List.of());
    }

    private void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    private ItemStack filler() {
        return this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private void fill(Inventory v) {
        ItemStack filler = this.filler();
        for (int i = 0; i < v.getSize(); i++) {
            v.setItem(i, filler);
        }
    }

    private Component text(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.text(name, NamedTextColor.GOLD));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    /** A player head wearing a custom skin (base64 "Value" texture), falling back to a plain head if it's bad - same pattern every other menu in this plugin uses for its own custom-head icons. */
    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack i = this.item(Material.PLAYER_HEAD, name, lore);
        SkullMeta meta = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        i.setItemMeta(meta);
        return i;
    }
}
