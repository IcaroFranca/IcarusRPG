package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * A Nether Star permanently pinned to hotbar slot {@value #SLOT} (the last hotbar
 * slot, "9" the way the vanilla UI numbers it) that opens the Skills menu on any
 * click - left or right, air or block - reachable without ever opening the
 * inventory screen, unlike the
 * {@code /skills} command (awkward to type on console/mobile) or the sneak+swap-hands
 * shortcut (no obvious controller/touch equivalent). Living in the hotbar rather than
 * the main inventory is what makes that true: it's always one scroll/number-key/tap
 * away, exactly like a compass or a map.
 *
 * <p>Identified by a tag in its own {@link ItemMeta}'s {@link PersistentDataContainer}
 * (same pattern as {@code BuilderWandService#isWand}), not by slot alone, so it's never
 * confused with a plain Nether Star a player might otherwise obtain. {@link
 * SkillsStarListener} is what actually keeps it pinned (cancels any click/drag that
 * would move, drop or destroy it) and re-grants it on join/respawn if ever missing.
 */
public final class SkillsStarService {
    public static final int SLOT = 8;

    private final NamespacedKey starKey;

    public SkillsStarService(Plugin plugin) {
        this.starKey = new NamespacedKey(plugin, "skills_star");
    }

    public boolean isStar(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.starKey, PersistentDataType.BYTE);
    }

    private ItemStack create(Player p) {
        Language l = Language.of(p);
        ItemStack star = ItemStack.of(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        meta.displayName(Component.text("★ " + l.choose("Menu", "Menu"), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(l.choose("Clique para abrir o Menu.", "Click to open the Menu."), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(l.choose("Não pode ser removido ou dado a outro jogador.", "Cannot be removed or given to another player."), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(this.starKey, PersistentDataType.BYTE, (byte) 1);
        star.setItemMeta(meta);
        return star;
    }

    /**
     * Makes sure slot {@value #SLOT} holds the tagged star, moving whatever was there
     * (if anything, and it isn't already the star) elsewhere in the inventory - or
     * dropping it at the player's feet if there's no room - rather than deleting it.
     * Safe to call repeatedly (join, respawn, self-healing after a click): a no-op once
     * the star is already in place.
     *
     * <p>Also wipes any stray tagged star sitting in another slot first - a leftover
     * from {@link #SLOT} having moved (a player who joined while the star still lived
     * in the main inventory keeps that old copy forever otherwise, since every other
     * safeguard treats a tagged star as untouchable regardless of which slot it's in).
     */
    public void ensure(Player p) {
        PlayerInventory inventory = p.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i != SLOT && this.isStar(contents[i])) {
                inventory.setItem(i, null);
            }
        }
        ItemStack current = inventory.getItem(SLOT);
        if (this.isStar(current)) {
            return;
        }
        inventory.setItem(SLOT, this.create(p));
        if (current != null && !current.getType().isAir()) {
            for (ItemStack leftover : inventory.addItem(current).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
        }
    }
}
