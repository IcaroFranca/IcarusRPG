package dev.icaro.foodtooltips.item.legendary;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The {@code /rpgitems} admin-only menu: one tile per {@link LegendaryWeapon}, click to
 * receive a copy in your own inventory (per-player decision - no target-player picker
 * for now). Not craftable, dropped, or sold anywhere yet - this menu is the only way
 * any of these items enter the game.
 */
public final class LegendaryItemsMenuService {
    private static final Map<Integer, LegendaryWeapon> SLOTS = Map.of(
            10, LegendaryWeapon.KASAKA_VENOM_FANG,
            12, LegendaryWeapon.KNIGHT_KILLER,
            14, LegendaryWeapon.BARUKA_DAGGER,
            16, LegendaryWeapon.DEMON_KING_DAGGERS,
            29, LegendaryWeapon.DEMON_KING_LONGSWORD,
            31, LegendaryWeapon.KAMISH_WRATH,
            33, LegendaryWeapon.UNDEAD_SWORD);

    private final LegendaryWeaponService weapons;
    private final Set<UUID> viewing = new HashSet<>();

    public LegendaryItemsMenuService(LegendaryWeaponService weapons) {
        this.weapons = weapons;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Itens Lendários", "Legendary Items"));
        ItemStack filler = this.filler();
        for (int i = 0; i < 54; i++) {
            v.setItem(i, filler);
        }
        for (Map.Entry<Integer, LegendaryWeapon> e : SLOTS.entrySet()) {
            v.setItem(e.getKey(), this.preview(e.getValue(), l));
        }
        p.openInventory(v);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
    }

    public void handleClick(Player p, int slot) {
        LegendaryWeapon w = SLOTS.get(slot);
        if (w == null) {
            return;
        }
        Language l = Language.of(p);
        ItemStack item = this.weapons.create(w, l);
        for (ItemStack overflow : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
        p.sendMessage(Component.text(l.choose("Recebido: ", "Received: ") + w.name(l == Language.PT), NamedTextColor.GREEN));
    }

    /** The menu tile: {@code w}'s real item plus one extra "click to receive" line. */
    private ItemStack preview(LegendaryWeapon w, Language l) {
        ItemStack item = this.weapons.create(w, l);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.add(Component.text(l.choose("Clique para receber.", "Click to receive."), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack f = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = f.getItemMeta();
        m.displayName(Component.text(" "));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        f.setItemMeta(m);
        return f;
    }
}
