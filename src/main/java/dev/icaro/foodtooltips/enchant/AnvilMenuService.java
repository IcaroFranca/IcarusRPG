package dev.icaro.foodtooltips.enchant;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.util.LoreWrap;
import java.util.ArrayList;
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
import org.bukkit.inventory.meta.Damageable;
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
 * <p>Combine or repair (Reforge was going to be a third mode here, but it's being tied
 * to an NPC instead - no mode-switching structure left to carry): {@link
 * #MAIN_ITEM_SLOT} takes the item to keep, {@link #SECONDARY_ITEM_SLOT} takes either
 * another item of the exact same {@link Material}, a real enchanted book, or one of
 * {@link #REPAIR_MATERIALS}' own ores/ingots - every enchantment (custom or vanilla)
 * the secondary item/book offers is checked against the main item the same way the
 * Enchanting Table itself would ({@link EnchantService#vanillaBlockReason}/{@link
 * EnchantService#customBlockReason} - same type-compatibility and conflict rules, no
 * duplicated logic), merged in (matching level bumps it by one, capped at the entry's
 * own max; a higher level on either side wins); a repair material instead restores a
 * flat percentage of {@link #MAIN_ITEM_SLOT}'s own current Max Durability (see {@link
 * #computeRepair}'s own doc on why that's independent of the item's own material).
 * Either way the result lands as a live preview at {@link #PREVIEW_SLOT}. Clicking the
 * preview - when it's a real item, not an explanation - consumes both inputs and hands
 * the result to the player, exactly like clicking a real anvil's own output slot.
 *
 * <p>Closing the screen always hands back whatever sits in {@link #MAIN_ITEM_SLOT}/
 * {@link #SECONDARY_ITEM_SLOT} first ({@link #returnInputItems}) - never consumed except
 * by a genuine confirmed combination.
 */
public final class AnvilMenuService {
    public static final int MAIN_ITEM_SLOT = 29;
    public static final int SECONDARY_ITEM_SLOT = 33;
    public static final int PREVIEW_SLOT = 13;
    public static final int LABEL_SLOT = 22;
    public static final int CLOSE_SLOT = 49;
    private static final int[] VISIBLE_WORK_SLOTS = {
            MAIN_ITEM_SLOT, SECONDARY_ITEM_SLOT, PREVIEW_SLOT, LABEL_SLOT, CLOSE_SLOT
    };
    /**
     * Fraction of {@link #MAIN_ITEM_SLOT}'s own Max Durability a single {@link
     * #SECONDARY_ITEM_SLOT} item of this material restores - checked before the
     * enchantment-combine path in {@link #computeCombine}, so any of these placed as the
     * secondary item always repairs instead, regardless of what the main item actually is
     * (a Netherite Ingot repairs a Wooden Hoe exactly as much as a Netherite Pickaxe - see
     * {@link #computeRepair}'s own doc). Netherite Ingot's {@code 1.0} fully repairs
     * whatever damage is left, same spirit as the other tiers scaled up to "all of it".
     */
    private static final Map<Material, Double> REPAIR_MATERIALS = Map.of(
            Material.COAL, 0.05,
            Material.COPPER_INGOT, 0.15,
            Material.IRON_INGOT, 0.25,
            Material.GOLD_INGOT, 0.30,
            Material.DIAMOND, 0.50,
            Material.NETHERITE_INGOT, 1.0);
    /** Netherite Ingot's own extra perk on top of the full repair every {@link #REPAIR_MATERIALS} entry gets at {@code 1.0} - see {@link #computeRepair}'s own doc on why this stacks uncapped. */
    private static final int NETHERITE_MAX_DURABILITY_BOOST_PERCENT = 10;

    private final Plugin plugin;
    private final EnchantService enchants;
    private final Set<UUID> viewing = new HashSet<>();

    public AnvilMenuService(Plugin plugin, EnchantService enchants) {
        this.plugin = plugin;
        this.enchants = enchants;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, "Anvil");
        this.fill(v);
        v.setItem(MAIN_ITEM_SLOT, null);
        v.setItem(SECONDARY_ITEM_SLOT, null);
        v.setItem(LABEL_SLOT, this.labelIcon(p));
        v.setItem(CLOSE_SLOT, this.closeIcon(p));
        this.refreshPreview(p, v);
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p, VISIBLE_WORK_SLOTS);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    /** Called by {@code AnvilMenuListener} on {@code InventoryCloseEvent} - hands back any input items before forgetting this player entirely. */
    public void handleClose(Player p, Inventory v) {
        this.returnInputItems(p, v);
        this.viewing.remove(p.getUniqueId());
    }

    /** Called (next tick, after the click that changed a slot actually lands) whenever an input slot changes - same deferred-refresh pattern {@code GrindstoneMenuService#scheduleCatalogRefresh} uses. A no-op if the player closed the screen before this ran. */
    public void scheduleRefresh(Player p) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.viewing.contains(p.getUniqueId())) {
                return;
            }
            Inventory v = p.getOpenInventory().getTopInventory();
            if (v.getSize() != 54) {
                return;
            }
            this.refreshPreview(p, v);
            dev.icaro.foodtooltips.menu.MenuBackground.apply(p, VISIBLE_WORK_SLOTS);
        });
    }

    /** Clicking the preview slot: a no-op unless it's currently showing a real, valid result (never an explanation or the "waiting for items" hint). Consumes both inputs and hands the result to the player. */
    public void confirm(Player p) {
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
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p, VISIBLE_WORK_SLOTS);
    }

    /** Hands back whatever's sitting in the two input slots (overflow drops on the ground), then clears them - the only way an in-progress combination can be abandoned, since there's no second mode to switch away to anymore. */
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

    private void refreshPreview(Player p, Inventory v) {
        boolean pt = Language.of(p) == Language.PT;
        ItemStack main = v.getItem(MAIN_ITEM_SLOT);
        ItemStack secondary = v.getItem(SECONDARY_ITEM_SLOT);
        v.setItem(PREVIEW_SLOT, this.previewIcon(this.computeCombine(main, secondary, pt), pt));
    }

    // ---- Combine logic --------------------------------------------------------------

    private record CombineOutcome(ItemStack preview, String reason) {
    }

    /**
     * Works out what combining would produce right now, or why it can't - see this
     * class's own doc for the merge rules. Both {@code main}/{@code secondary} are read
     * only, never mutated - the returned {@link CombineOutcome#preview} (if any) is a
     * fresh clone.
     */
    private CombineOutcome computeCombine(ItemStack main, ItemStack secondary, boolean pt) {
        if (main == null || main.isEmpty() || secondary == null || secondary.isEmpty()) {
            return new CombineOutcome(null, null);
        }
        Double repairFraction = REPAIR_MATERIALS.get(secondary.getType());
        if (repairFraction != null) {
            return this.computeRepair(main, repairFraction);
        }
        if (main.getType() == Material.BOOK || main.getType() == Material.ENCHANTED_BOOK) {
            return new CombineOutcome(null, "The main item needs to be a piece of equipment, not a book.");
        }
        EnchantmentStorageMeta bookMeta = secondary.getType() == Material.ENCHANTED_BOOK && secondary.getItemMeta() instanceof EnchantmentStorageMeta esm ? esm : null;
        if (bookMeta == null && main.getType() != secondary.getType()) {
            return new CombineOutcome(null, "The items need to be the same type, or the second one needs to be an enchanted book.");
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
                reason = "That item has no enchantments to combine.";
            } else if (failureReasons.size() == 1) {
                reason = failureReasons.get(0);
            } else if (!failureReasons.isEmpty()) {
                reason = "None of that item's enchantments can be combined with the main one.";
            } else {
                reason = "This wouldn't change the main item.";
            }
            return new CombineOutcome(null, reason);
        }

        ItemStack preview = main.clone();
        for (Map.Entry<EnchantEntry, Integer> entry : toApply.entrySet()) {
            this.enchants.setLevel(preview, entry.getKey(), entry.getValue(), pt);
        }
        return new CombineOutcome(preview, null);
    }

    /**
     * Restores {@code fraction} of {@code main}'s own current Max Durability (see {@link
     * #REPAIR_MATERIALS}), reading {@link Damageable#getMaxDamage} rather than {@link
     * Material#getMaxDurability} so this repairs the SAME number this plugin's own {@code
     * item.DurabilityService} multiplier already shows the player (e.g. a tool whose real
     * max is 5x vanilla gets 5x the repair too) - never the raw vanilla number underneath
     * it. Deliberately keyed only by {@code main}'s own current Max Durability, never its
     * {@link Material} - the player's own explicit spec ("independe do material que o item
     * é feito"): a Coal repairs a Netherite Pickaxe by the exact same 5% of ITS OWN max
     * that it would a Wooden Hoe, not some material-scaled amount.
     *
     * <p>Netherite Ingot ({@code fraction == 1.0}) does both: a full repair AND a
     * permanent +{@value #NETHERITE_MAX_DURABILITY_BOOST_PERCENT}% bump to the item's own
     * Max Durability (see {@link #boostedMaxDurability}) - stacking multiplicatively with
     * no cap on repeated use, per the player's own explicit choice (Netherite's own real
     * scarcity/cost already self-limits how often this happens, so no artificial per-item
     * guard was added). Unlike every other repair material, this one still does something
     * useful even at full durability (the boost alone), so it's the one case allowed
     * through despite {@code currentDamage} being 0. Neither effect is narrated in the
     * preview item's own lore - per the player's own "não quero isso na lore dos itens" -
     * the item's real stats (current/max durability) already show the result once applied.
     */
    private CombineOutcome computeRepair(ItemStack main, double fraction) {
        ItemMeta currentMeta = main.getItemMeta();
        if (!(currentMeta instanceof Damageable damageable) || main.getType().getMaxDurability() <= 0) {
            return new CombineOutcome(null, "That item doesn't have durability to repair.");
        }
        int currentDamage = damageable.getDamage();
        boolean isNetherite = fraction >= 1.0;
        if (currentDamage <= 0 && !isNetherite) {
            return new CombineOutcome(null, "That item is already at full durability.");
        }
        int currentMax = damageable.getMaxDamage();
        int restored = repairAmount(currentDamage, currentMax, fraction);
        ItemStack preview = main.clone();
        ItemMeta previewMeta = preview.getItemMeta();
        Damageable previewDamageable = (Damageable) previewMeta;
        previewDamageable.setDamage(currentDamage - restored);
        if (isNetherite) {
            previewDamageable.setMaxDamage(boostedMaxDurability(currentMax));
        }
        preview.setItemMeta(previewMeta);
        return new CombineOutcome(preview, null);
    }

    /** {@code currentMax} increased by {@value #NETHERITE_MAX_DURABILITY_BOOST_PERCENT}% - pure math, see {@code AnvilMenuServiceTest}. */
    static int boostedMaxDurability(int currentMax) {
        return (int) Math.round(currentMax * (1.0 + NETHERITE_MAX_DURABILITY_BOOST_PERCENT / 100.0));
    }

    /** Pure repair-amount math (no Bukkit statics touched) - see {@code AnvilMenuServiceTest}. Never more than {@code currentDamage} itself, so a repair can't overshoot into negative damage. */
    static int repairAmount(int currentDamage, int maxDurability, double fraction) {
        if (currentDamage <= 0) {
            return 0;
        }
        return fraction >= 1.0 ? currentDamage : Math.min(currentDamage, (int) Math.round(maxDurability * fraction));
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
            lore.add(this.text("Click to confirm.", NamedTextColor.GREEN));
            meta.lore(lore);
            shown.setItemMeta(meta);
            return shown;
        }
        if (outcome.reason() != null) {
            List<Component> lore = new ArrayList<>();
            for (String part : LoreWrap.wrapText(outcome.reason(), LoreWrap.DEFAULT_WIDTH)) {
                lore.add(this.text(part, NamedTextColor.RED));
            }
            return this.item(Material.BARRIER, "Invalid combination", lore);
        }
        List<Component> lore = new ArrayList<>();
        for (String part : LoreWrap.wrapText("Place an item and another compatible equipment or an enchanted book.", LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GRAY));
        }
        return this.item(Material.GRAY_STAINED_GLASS_PANE, "Waiting for items", lore);
    }

    private ItemStack labelIcon(Player p) {
        boolean pt = Language.of(p) == Language.PT;
        List<Component> lore = new ArrayList<>();
        for (String part : LoreWrap.wrapText("Combines the enchantments of two compatible items, or an item and an enchanted book. Place Coal, a Copper/Iron/Gold Ingot, a Diamond or a Netherite Ingot instead to repair durability - a Netherite Ingot also permanently increases Max Durability by " + NETHERITE_MAX_DURABILITY_BOOST_PERCENT + "%.", LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GRAY));
        }
        return this.item(Material.ANVIL, "Combine Items", lore);
    }

    private ItemStack closeIcon(Player p) {
        boolean pt = Language.of(p) == Language.PT;
        return this.customHead(HeadTexture.CLOSE, "Close", List.of());
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
