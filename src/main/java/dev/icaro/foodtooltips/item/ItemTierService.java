package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Item rarity ({@link ItemTier}) for the plugin's weapons, tools and armor - drops,
 * ores and other plain items don't get one. Every taggable {@link Material} resolves
 * to a tier by tool/armor material family (see {@link #equipmentTier}), unless a
 * server owner overrides it for a specific Material via {@code item-tiers} in
 * config.yml (works for any item, equipment or not - that's still an explicit,
 * per-item admin choice, not the automatic default) or the item was pinned in code
 * via {@link #forceTier} (same idea, for one-off items like the Builder's Wand).
 *
 * <p>Tooltip rewriting follows the same one-shot pattern as {@code
 * ArmorDefenseService#applyDefenseTooltip}: idempotent via a PDC marker on the
 * item's own {@link ItemMeta}, so each item is only rewritten once. A taggable item
 * gets a bold "TIER {X} {KIND}" line appended at the very end of its lore (e.g.
 * "TIER C PICKAXE"), or just the bare "TIER {X}" for an override/forced item with no
 * equipment "kind" of its own (e.g. a config-overridden Totem of Undying). An
 * already-tagged item that turns out not to be taggable (tagged before this
 * equipment-only restriction existed) gets its tier lore and name color stripped
 * back off the next time it's swept - see {@link #stripTier}.
 */
public final class ItemTierService {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final NamespacedKey tierKey;
    private final NamespacedKey forcedTierKey;
    private final NamespacedKey spacingRepairedKey;
    private final Map<Material, ItemTier> overrides = new EnumMap<>(Material.class);

    public ItemTierService(Plugin plugin) {
        this.tierKey = new NamespacedKey(plugin, "item_tier_applied");
        this.forcedTierKey = new NamespacedKey(plugin, "item_tier_forced");
        this.spacingRepairedKey = new NamespacedKey(plugin, "item_tier_spacing_repaired");
        var section = plugin.getConfig().getConfigurationSection("item-tiers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    Material m = Material.valueOf(key.toUpperCase(java.util.Locale.ROOT));
                    ItemTier t = ItemTier.valueOf(section.getString(key, "D").toUpperCase(java.util.Locale.ROOT));
                    this.overrides.put(m, t);
                } catch (IllegalArgumentException ignored) {
                    // Unknown Material or tier name in config - skip rather than crash startup.
                }
            }
        }
    }

    // ----- Tier lookup ----------------------------------------------------

    /**
     * Pins a specific item (not the whole Material - see {@code item-tiers} in
     * config.yml for that) to a tier, regardless of what {@link #tierOf} would
     * otherwise say. For one-off special items built entirely in code (like the
     * Builder's Wand, a plain Stick that would otherwise fall into Tier E's junk
     * bucket) - call this on the item's own {@link ItemMeta} while building it,
     * before it ever reaches {@link #applyItemTiers}.
     */
    public void forceTier(ItemMeta meta, ItemTier tier) {
        meta.getPersistentDataContainer().set(this.forcedTierKey, PersistentDataType.STRING, tier.name());
    }

    private ItemTier tierOf(ItemMeta meta, Material m) {
        String forced = meta.getPersistentDataContainer().get(this.forcedTierKey, PersistentDataType.STRING);
        if (forced != null) {
            try {
                return ItemTier.valueOf(forced);
            } catch (IllegalArgumentException ignored) {
                // Corrupted/foreign PDC value - fall through to the normal Material-based lookup.
            }
        }
        return this.tierOf(m);
    }

    public ItemTier tierOf(Material m) {
        ItemTier override = this.overrides.get(m);
        if (override != null) {
            return override;
        }
        String kind = kindOf(m);
        // Only reached for a taggable non-equipment item (an override already returned
        // above) or a caller outside the taggable() gate entirely - Common is as
        // reasonable a default as any for something with no curated tier of its own.
        return kind != null ? equipmentTier(m, kind) : ItemTier.D;
    }

    /**
     * Whether {@code m} gets a tier tag at all: a weapon/tool/armor piece ({@link
     * #kindOf} says so), or an item a server owner or the plugin's own code explicitly
     * opted in for (a config {@code item-tiers} override, or {@link #forceTier} pinned
     * on this exact item) - drops, ores and other plain items get neither by default.
     */
    private boolean taggable(Material m, ItemMeta meta) {
        return kindOf(m) != null
                || this.overrides.containsKey(m)
                || meta.getPersistentDataContainer().has(this.forcedTierKey, PersistentDataType.STRING);
    }

    private static ItemTier equipmentTier(Material m, String kind) {
        // Standalone weapons - no "{FAMILY}_{KIND}" material name to read a family from.
        switch (m) {
            case TRIDENT, MACE -> { return ItemTier.A; }
            case CROSSBOW, SHIELD -> { return ItemTier.B; }
            case BOW, FISHING_ROD -> { return ItemTier.C; }
            default -> { /* fall through to family-prefix lookup below */ }
        }
        String name = m.name();
        String family = name.endsWith("_" + kind) ? name.substring(0, name.length() - kind.length() - 1) : name;
        // Compressed on purpose: everything iron-and-below (wood, gold, stone, copper,
        // iron, chainmail/leather/turtle) sits at D, diamond at C, netherite at B -
        // leaving A and S free for the plugin's own future gear (see SwordDamageService
        // for the matching attack-damage rework), rather than spreading vanilla
        // materials across the whole scale like the old NETHERITE=S/DIAMOND=A mapping did.
        return switch (family) {
            case "NETHERITE" -> ItemTier.B;
            case "DIAMOND" -> ItemTier.C;
            default -> ItemTier.D;
        };
    }

    /** Vanilla item-type word to append after the tier for equipment (e.g. "PICKAXE"), or null for plain items. */
    private static String kindOf(Material m) {
        String n = m.name();
        if (n.endsWith("_SWORD")) return "SWORD";
        if (n.endsWith("_PICKAXE")) return "PICKAXE";
        if (n.endsWith("_AXE")) return "AXE";
        if (n.endsWith("_SHOVEL")) return "SHOVEL";
        if (n.endsWith("_HOE")) return "HOE";
        if (n.endsWith("_HELMET")) return "HELMET";
        if (n.endsWith("_CHESTPLATE")) return "CHESTPLATE";
        if (n.endsWith("_LEGGINGS")) return "LEGGINGS";
        if (n.endsWith("_BOOTS")) return "BOOTS";
        return switch (m) {
            case BOW -> "BOW";
            case CROSSBOW -> "CROSSBOW";
            case TRIDENT -> "TRIDENT";
            case SHIELD -> "SHIELD";
            case MACE -> "MACE";
            case FISHING_ROD -> "FISHING ROD";
            default -> null;
        };
    }

    // ----- Tooltip rewrite --------------------------------------------------

    /** Applies the tier tooltip to every item in the player's inventory (storage, armor and offhand). */
    public void applyItemTiers(Player p) {
        Language l = Language.of(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack updated = this.applyTier(storage[i], l);
            if (updated == null) {
                updated = this.repairTierSpacing(storage[i]);
            }
            if (updated != null) {
                storage[i] = updated;
                changed = true;
            }
        }
        // A freshly-received item (bought, mined, looted, given...) only gets its tier
        // tooltip applied here, one tick after it lands in the inventory - for that one
        // tick its lore/name doesn't match an already-tagged stack of the same item, so
        // the game can't merge them and they end up as two separate slots even once both
        // are tagged identically. Re-coalescing every tick, right after tagging, heals
        // that split (and any other stray fragmentation) instead of leaving it stuck.
        if (ItemStackUtil.coalesce(storage)) {
            changed = true;
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack updated = this.applyTier(armor[i], l);
            if (updated == null) {
                updated = this.repairTierSpacing(armor[i]);
            }
            if (updated != null) {
                armor[i] = updated;
            }
        }
        inv.setArmorContents(armor);
        ItemStack offhand = this.applyTier(inv.getItemInOffHand(), l);
        if (offhand == null) {
            offhand = this.repairTierSpacing(inv.getItemInOffHand());
        }
        if (offhand != null) {
            inv.setItemInOffHand(offhand);
        }
    }

    /**
     * Returns the mutated item if it needed rewriting, or null if it's not taggable or
     * was already done. Public (not just called from {@link #applyItemTiers}) so a
     * freshly-built one-off item - a legendary weapon pinned via {@link #forceTier}, say
     * - can get its tier badge and name color immediately at creation time instead of
     * waiting for the next inventory sweep; idempotent via {@link #tierKey}, so calling
     * it early never causes {@link #applyItemTiers} to redo the work later.
     */
    public ItemStack applyTier(ItemStack item, Language l) {
        if (item == null || item.isEmpty() || !item.getType().isItem()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        boolean alreadyTagged = meta.getPersistentDataContainer().has(this.tierKey, PersistentDataType.BYTE);
        if (!this.taggable(item.getType(), meta)) {
            // Not a weapon/tool/armor piece and no explicit override/forceTier - if it
            // was tagged before this restriction existed, strip that back off; otherwise
            // there's nothing to do.
            return alreadyTagged ? this.stripTier(item, meta) : null;
        }
        if (alreadyTagged) {
            return null;
        }
        ItemTier tier = this.tierOf(meta, item.getType());
        String kind = kindOf(item.getType());
        String text = kind == null ? tier.label() : tier.label() + " " + kind;
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        // Whether there's real content before the tier line decides the separating blank
        // line - deliberately NOT just "is lore non-empty right now" (see
        // willGainMoreLore's own doc for why that racy check is exactly what used to
        // split otherwise-identical stacks apart for good).
        if (!lore.isEmpty() || this.willGainMoreLore(item)) {
            lore.add(Component.empty());
        }
        lore.add(Component.text(text, tier.color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        // Tooltip box border is a fixed vanilla texture (can't be recolored per-item without a
        // custom resource pack via the tooltip_style component) - so instead the item's own
        // name is recolored/bolded to match its tier, the closest at-a-glance signal without one.
        Component base = meta.hasDisplayName() ? meta.displayName() : Component.translatable(item.getType().translationKey());
        meta.displayName(base.color(tier.color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(this.tierKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Un-tags an item that {@link #applyTier} already tagged before it stopped being
     * taggable (either the equipment-only restriction landed after this exact item was
     * tagged, or a config {@code item-tiers} override/{@link #forceTier} pin was
     * removed since) - drops its "TIER ..." lore line (plus the blank separator right
     * before it, since that line always sits last in the lore, see {@link #applyTier})
     * and clears {@link #tierKey} so it's never revisited. The recolored/bolded display
     * name is reset to vanilla's own (null) too - nothing else in the plugin names a
     * plain drop/ore, so this can't clobber another system's name, only a player's own
     * anvil rename on the rare item this applies to and was also renamed.
     */
    private ItemStack stripTier(ItemStack item, ItemMeta meta) {
        if (meta.hasLore()) {
            List<Component> lore = new ArrayList<>(meta.lore());
            for (int i = lore.size() - 1; i >= 0; i--) {
                if (PLAIN.serialize(lore.get(i)).startsWith("TIER ")) {
                    lore.remove(i);
                    if (i > 0 && i - 1 == lore.size() - 1 && this.isBlank(lore.get(i - 1))) {
                        lore.remove(i - 1);
                    }
                    break;
                }
            }
            meta.lore(lore.isEmpty() ? null : lore);
        }
        meta.displayName(null);
        meta.getPersistentDataContainer().remove(this.tierKey);
        meta.getPersistentDataContainer().remove(this.spacingRepairedKey);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isBlank(Component c) {
        return PLAIN.serialize(c).isEmpty();
    }

    /**
     * Whether some *other* independently-scheduled system is going to add its own lore
     * to {@code item} sooner or later - checked by Material/data instead of the item's
     * current lore, since that other system might not have run yet (food/mining
     * tooltips run off their own click/join/held triggers, entirely separate from this
     * one-shot pass's own periodic one) or might run again later on its own schedule.
     * Without this, whether the tier line got a separating blank line before it
     * depended purely on which of two independently-triggered systems happened to
     * reach the item first - and since this pass never runs twice, whichever way that
     * race went stuck to the item forever, silently splitting two otherwise-identical
     * stacks (say, two Raw Mutton picked up moments apart) apart for good.
     */
    private boolean willGainMoreLore(ItemStack item) {
        Material type = item.getType();
        return type.name().endsWith("_PICKAXE")
                || item.getData(DataComponentTypes.FOOD) != null
                || SwordDamageService.totalDamage(type) != null
                || ToolDamageService.totalDamage(type) != null;
    }

    /**
     * One-time repair for an item tiered before {@link #willGainMoreLore} existed,
     * where the blank line before its "TIER ..." line ended up wrong purely because of
     * the race described there. Never re-touches the tier lookup, color or name
     * rewrite - only adds or removes that one blank line, based on whether there's
     * real (non-blank) content already sitting before the tier line right now.
     * Idempotent via its own PDC marker, independent of {@link #tierKey}.
     */
    public ItemStack repairTierSpacing(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(this.tierKey, PersistentDataType.BYTE)
                || meta.getPersistentDataContainer().has(this.spacingRepairedKey, PersistentDataType.BYTE)) {
            return null;
        }
        // Always marks spacingRepairedKey and writes the meta back below (even when
        // there's nothing to fix) and always returns the item non-null - so the caller
        // always persists it, guaranteeing this scan genuinely only ever happens once
        // per item, the same one-shot guarantee applyTier's own tierKey gives it.
        meta.getPersistentDataContainer().set(this.spacingRepairedKey, PersistentDataType.BYTE, (byte) 1);
        if (meta.hasLore()) {
            List<Component> lore = new ArrayList<>(meta.lore());
            int tierIndex = -1;
            for (int i = 0; i < lore.size(); i++) {
                if (PLAIN.serialize(lore.get(i)).startsWith("TIER ")) {
                    tierIndex = i;
                    break;
                }
            }
            if (tierIndex >= 0) {
                boolean hasRealContentBefore = false;
                for (int i = 0; i < tierIndex; i++) {
                    if (!PLAIN.serialize(lore.get(i)).isEmpty()) {
                        hasRealContentBefore = true;
                        break;
                    }
                }
                boolean hasBlankImmediatelyBefore = tierIndex > 0 && PLAIN.serialize(lore.get(tierIndex - 1)).isEmpty();
                if (hasRealContentBefore && !hasBlankImmediatelyBefore) {
                    lore.add(tierIndex, Component.empty());
                    meta.lore(lore);
                } else if (!hasRealContentBefore && hasBlankImmediatelyBefore) {
                    lore.remove(tierIndex - 1);
                    meta.lore(lore);
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }
}
