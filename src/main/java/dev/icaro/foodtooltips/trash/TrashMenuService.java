package dev.icaro.foodtooltips.trash;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * A dedicated screen (opened from the Skills menu's trash-can button) with a single
 * slot that vanishes anything dropped on it one tick later - covers every click
 * outcome (an item dropped onto the icon, the icon itself picked up, a swap...)
 * uniformly by resetting the slot to a fresh icon and clearing the cursor if it ended
 * up holding the icon, rather than tracking exactly what the click did to get there.
 * The icon itself is PDC-tagged so it can never actually leave the menu with a player.
 */
public final class TrashMenuService {
    public static final int TRASH_SLOT = 22;
    public static final int BACK_SLOT = 45;

    private final Plugin plugin;
    private final Consumer<Player> back;
    private final NamespacedKey trashIconKey;
    private final Set<UUID> viewing = new HashSet<>();

    public TrashMenuService(Plugin plugin, Consumer<Player> back) {
        this.plugin = plugin;
        this.back = back;
        this.trashIconKey = new NamespacedKey(plugin, "trash_icon");
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Lixeira", "Trash Can"));
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) {
            v.setItem(i, filler);
        }
        v.setItem(TRASH_SLOT, this.trashIcon(l));
        v.setItem(BACK_SLOT, this.item(Material.BARRIER, l.choose("Voltar às skills", "Back to skills"), List.of()));
        p.openInventory(v);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
    }

    public void back(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.back.accept(p);
    }

    /**
     * Vanishes whatever ends up in the trash slot one tick after a click there - see
     * this class's own doc for why every outcome is handled the same uniform way.
     */
    public void scheduleTrashEmpty(Player p) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.viewing(p)) {
                return;
            }
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getSize() != 54) {
                return;
            }
            if (this.isTrashIcon(p.getItemOnCursor())) {
                p.setItemOnCursor(null);
            }
            top.setItem(TRASH_SLOT, this.trashIcon(Language.of(p)));
        });
    }

    private boolean isTrashIcon(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.trashIconKey, PersistentDataType.BYTE);
    }

    private ItemStack trashIcon(Language l) {
        ItemStack i = this.customHead(HeadTexture.TRASH_CAN, l.choose("Lixeira", "Trash Can"),
                List.of(this.text(l.choose("Solte um item aqui para descartá-lo.", "Drop an item here to discard it."), NamedTextColor.GRAY)));
        ItemMeta m = i.getItemMeta();
        m.getPersistentDataContainer().set(this.trashIconKey, PersistentDataType.BYTE, (byte) 1);
        i.setItemMeta(m);
        return i;
    }

    /** A player head wearing a custom skin (base64 "Value" texture), falling back to a plain head if it's bad. */
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
        m.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private Component text(String s, NamedTextColor c) {
        return Component.text(s, c);
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
}
