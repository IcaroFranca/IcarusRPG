package dev.icaro.foodtooltips.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Multiplies every damageable item's Max Durability by {@code items.durability-multiplier}
 * (default 5) - every tool, weapon, armor piece, bow/crossbow, elytra, shield, fishing
 * rod, trident etc., since {@link org.bukkit.Material#getMaxDurability()} already
 * covers exactly that set with no per-Material list to maintain (returns 0 for
 * anything that isn't damageable, which {@link #multiply} skips).
 *
 * <p>Same one-shot pattern as {@code ItemTierService#applyItemTiers}: idempotent via a
 * PDC marker on the item's own {@link ItemMeta}, applied on join and re-applied every
 * HUD tick in {@code FoodTooltipsPlugin} to reach anything crafted/looted/bought/given
 * afterwards, without needing a dedicated pickup/inventory-click hook. An item's
 * current damage is scaled up by the same multiplier as its new max, so an
 * already-half-worn tool stays half-worn (proportionally) instead of the multiplier
 * handing it free uses out of nowhere.
 *
 * <p>Gold tools/weapons/armor/horse armor (any {@code GOLDEN_*} material) are the one
 * exception: instead of the usual multiplier, every one of them gets a flat {@link
 * #GOLD_MAX_DURABILITY} - vanilla's own gold tools in particular sit at a notoriously
 * low 32, so even ×5 (160) leaves them too fragile to matter next to gold's already
 * class-leading Mining Speed/attack speed here; a flat, generous number fixes that
 * without needing a per-gold-item-type table.
 *
 * <p>Every {@code Material.SHIELD} also gets flagged Unbreakable outright (per explicit
 * request) - piggybacking on this same one-shot sweep rather than a dedicated service/key
 * of its own, since a shield is already guaranteed to pass through here (it's damageable,
 * see this class's own doc) and {@link ItemMeta#setUnbreakable} is naturally idempotent
 * (setting it to already-true again is a no-op), so it doesn't need its own PDC marker.
 *
 * <p>Also keeps a live "Durability: X / Y" lore line on every damageable item, since F3+H
 * (vanilla's own Advanced Tooltips overlay, the only other place durability numbers show)
 * is entirely client-side - a server plugin has no API surface to filter or reformat it, so
 * the only way to show a player their exact durability without requiring F3+H is this
 * plugin's own always-visible line. Unlike the one-shot multiplier/Unbreakable flag above,
 * this line is rebuilt on every {@link #multiply} call (every HUD tick, not gated by {@link
 * #appliedKey}) since current damage keeps changing as the item is used - same
 * strip-old-line-then-reinsert-before-TIER pattern as {@code FoodTooltipService#update}.
 */
public final class DurabilityService {
    private static final PlainTextComponentSerializer P = PlainTextComponentSerializer.plainText();
    /** Flat Max Durability every {@code GOLDEN_*} item gets instead of the usual multiplier - see this class's own doc. */
    private static final int GOLD_MAX_DURABILITY = 1000;
    private final NamespacedKey appliedKey;
    private final int multiplier;

    public DurabilityService(Plugin plugin) {
        this.appliedKey = new NamespacedKey(plugin, "durability_multiplied");
        this.multiplier = Math.max(1, plugin.getConfig().getInt("items.durability-multiplier", 5));
    }

    /** Applies the multiplier to every item in the player's inventory (storage, armor and offhand). */
    public void applyDurability(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            if (this.multiply(storage[i])) {
                changed = true;
            }
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack[] armor = inv.getArmorContents();
        changed = false;
        for (int i = 0; i < armor.length; i++) {
            if (this.multiply(armor[i])) {
                changed = true;
            }
        }
        if (changed) {
            inv.setArmorContents(armor);
        }
        ItemStack offhand = inv.getItemInOffHand();
        if (this.multiply(offhand)) {
            inv.setItemInOffHand(offhand);
        }
    }

    /** Mutates {@code item} in place and returns true if it needed multiplying and/or flagging Unbreakable (see the class doc), or false if neither applied. */
    private boolean multiply(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        int vanillaMax = item.getType().getMaxDurability();
        if (vanillaMax <= 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return false;
        }
        boolean changed = false;
        // Checked independent of appliedKey's own one-shot gate below (and every tick, not
        // just once) so a shield already in someone's inventory from before this shipped -
        // appliedKey already set from its own durability multiplier - still gets flagged,
        // no re-crafting/re-looting needed.
        if (item.getType() == Material.SHIELD && !meta.isUnbreakable()) {
            meta.setUnbreakable(true);
            changed = true;
        }
        if (!meta.getPersistentDataContainer().has(this.appliedKey, PersistentDataType.BYTE)) {
            int currentDamage = damageable.hasDamage() ? damageable.getDamage() : 0;
            int targetMax = item.getType().name().startsWith("GOLDEN_") ? GOLD_MAX_DURABILITY : vanillaMax * this.multiplier;
            damageable.setMaxDamage(targetMax);
            damageable.setDamage((int) Math.round(currentDamage * (targetMax / (double) vanillaMax)));
            meta.getPersistentDataContainer().set(this.appliedKey, PersistentDataType.BYTE, (byte) 1);
            changed = true;
        }
        if (this.syncDurabilityLore(meta, damageable)) {
            changed = true;
        }
        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }

    /** Rebuilds {@code meta}'s "Durability: X / Y" lore line from {@code damageable}'s current state; returns true if the lore actually changed. */
    private boolean syncDurabilityLore(ItemMeta meta, Damageable damageable) {
        List<Component> original = Objects.requireNonNullElse(meta.lore(), List.of());
        ArrayList<Component> lore = new ArrayList<Component>(original);
        this.removeDurabilityLine(lore);
        int max = damageable.getMaxDamage();
        int damage = damageable.hasDamage() ? damageable.getDamage() : 0;
        int remaining = Math.max(0, max - damage);
        List<Component> block = List.of(this.line("Durability: " + remaining + " / " + max, NamedTextColor.GRAY));
        this.insertBeforeTier(lore, block);
        if (lore.equals(original)) {
            return false;
        }
        meta.lore(lore);
        return true;
    }

    /**
     * Same ordering fix as {@code FoodTooltipService#insertBeforeTier}: always resolves this
     * line to immediately before the item's "TIER ..." line (appending at the end if there
     * isn't one), so two otherwise-identical stacks never end up with this block in a
     * different relative position and permanently fail to stack.
     */
    private void insertBeforeTier(List<Component> lore, List<Component> block) {
        int at = this.findTierIndex(lore);
        if (at < 0) {
            at = lore.size();
        }
        if (at == 0 || !this.isBlank(lore.get(at - 1))) {
            lore.add(at++, Component.empty());
        }
        lore.addAll(at, block);
        at += block.size();
        if (at < lore.size() && !this.isBlank(lore.get(at))) {
            lore.add(at, Component.empty());
        }
    }

    private int findTierIndex(List<Component> lore) {
        for (int i = 0; i < lore.size(); i++) {
            if (P.serialize(lore.get(i)).startsWith("TIER ")) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBlank(Component c) {
        return P.serialize(c).isEmpty();
    }

    private void removeDurabilityLine(List<Component> lore) {
        int i = 0;
        while (i < lore.size()) {
            if (!P.serialize(lore.get(i)).startsWith("Durability: ")) {
                i++;
                continue;
            }
            int from = i > 0 && this.isBlank(lore.get(i - 1)) ? i - 1 : i;
            lore.subList(from, i + 1).clear();
            i = Math.max(0, from - 1);
        }
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, (TextColor) c).decoration(TextDecoration.ITALIC, false);
    }
}
