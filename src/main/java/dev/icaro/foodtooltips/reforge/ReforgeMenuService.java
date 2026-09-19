package dev.icaro.foodtooltips.reforge;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.menu.MenuBackground;
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
import org.bukkit.inventory.meta.SkullMeta;

/** The Blacksmith's reforge screen. Reforge choices are added separately. */
public final class ReforgeMenuService {
    public static final int ITEM_SLOT = 13;
    public static final int REFORGE_SLOT = 22;
    public static final int CLOSE_SLOT = 40;

    private final Set<UUID> viewing = new HashSet<>();

    public void open(Player player) {
        Language language = Language.of(player);
        Inventory inventory = Bukkit.createInventory(null, 54, "Reforge");
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(ITEM_SLOT, null);
        inventory.setItem(REFORGE_SLOT, this.item(Material.ANVIL,
                language.choose("Reforjar item", "Reforge Item"),
                List.of(
                        this.text(language.choose("Coloque o item no espaço acima.", "Place the item in the slot above."), NamedTextColor.GRAY),
                        this.text(language.choose("Clique para mudar a reforja.", "Click to change its reforge."), NamedTextColor.YELLOW))));
        inventory.setItem(CLOSE_SLOT, this.customHead(HeadTexture.CLOSE,
                language.choose("Fechar", "Close"), List.of()));

        this.viewing.add(player.getUniqueId());
        player.openInventory(inventory);
        MenuBackground.apply(player, ITEM_SLOT);
    }

    public boolean viewing(Player player) {
        return this.viewing.contains(player.getUniqueId());
    }

    public void close(Player player) {
        this.viewing.remove(player.getUniqueId());
    }

    public void returnItem(Player player, Inventory inventory) {
        ItemStack deposited = inventory.getItem(ITEM_SLOT);
        if (deposited == null || deposited.isEmpty()) {
            return;
        }
        inventory.setItem(ITEM_SLOT, null);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(deposited.clone());
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** Temporary response until the reforge catalogue and costs are supplied. */
    public void reforge(Player player) {
        ItemStack deposited = player.getOpenInventory().getTopInventory().getItem(ITEM_SLOT);
        Language language = Language.of(player);
        if (deposited == null || deposited.isEmpty()) {
            player.sendActionBar(this.text(
                    language.choose("Coloque um item no espaço acima.", "Place an item in the slot above."),
                    NamedTextColor.RED));
            return;
        }
        player.sendActionBar(this.text(
                language.choose("As reforjas serão adicionadas em seguida.", "Reforges will be added next."),
                NamedTextColor.YELLOW));
    }

    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Fall back to a plain player head if the texture is invalid.
        }
        meta.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color);
    }
}
