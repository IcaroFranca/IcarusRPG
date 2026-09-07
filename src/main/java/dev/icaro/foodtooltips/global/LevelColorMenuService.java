package dev.icaro.foodtooltips.global;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

public final class LevelColorMenuService {
    private final Plugin plugin;
    private final GlobalLevelService global;
    private final LevelColorService colors;
    private final GlobalPresentationService presentation;
    private final Consumer<Player> back;

    public LevelColorMenuService(Plugin plugin, GlobalLevelService global, LevelColorService colors, GlobalPresentationService presentation, Consumer<Player> back) {
        this.plugin = plugin;
        this.global = global;
        this.colors = colors;
        this.presentation = presentation;
        this.back = back;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        ChestGui gui = new ChestGui(6, l.choose("Cores do Nível", "Level Colors"), this.plugin);
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(9, 6);
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 9; x++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        GlobalLevelSnapshot snapshot = this.global.snapshot(p);
        LevelColorTheme selected = this.colors.selected(p);
        ItemStack head = this.item(Material.PLAYER_HEAD, l.choose("Preview do Nível", "Level Preview"), List.of(this.presentation.badge(p).append(Component.text(p.getName(), NamedTextColor.WHITE)), this.text(l.choose("Nível Global: ", "Global Level: ") + snapshot.level(), NamedTextColor.GOLD), this.text(l.choose("Selecionado: ", "Selected: ") + selected.name(), NamedTextColor.YELLOW), !this.colors.unlocked(p, selected) ? this.text(l.choose("Temporariamente suspenso: nível insuficiente", "Temporarily suspended: insufficient level"), NamedTextColor.RED) : Component.empty()), false);
        SkullMeta skull = (SkullMeta) head.getItemMeta();
        skull.setOwningPlayer((OfflinePlayer) p);
        head.setItemMeta(skull);
        pane.addItem(new GuiItem(head), 4, 0);

        List<LevelColorTheme> themes = LevelColorCatalog.themes();
        for (int i = 0; i < themes.size(); i++) {
            LevelColorTheme theme = themes.get(i);
            boolean unlocked = this.colors.unlocked(p, theme);
            boolean active = selected.id().equals(theme.id());
            ArrayList<Component> lore = new ArrayList<>();
            lore.add(this.text("ID: " + theme.id(), NamedTextColor.DARK_GRAY));
            lore.add(this.text(l.choose("Requer Nível Global ", "Requires Global Level ") + theme.requiredLevel(), unlocked ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(this.text(this.palette(theme), NamedTextColor.GRAY));
            lore.add(this.text(active ? l.choose("SELECIONADO", "SELECTED") : (unlocked ? l.choose("Clique para selecionar", "Click to select") : l.choose("BLOQUEADO", "LOCKED")), active ? NamedTextColor.GOLD : (unlocked ? NamedTextColor.YELLOW : NamedTextColor.RED)));
            ItemStack icon = this.item(unlocked ? theme.icon() : Material.GRAY_DYE, theme.name(), lore, active);
            int slot = 9 + i;
            pane.addItem(new GuiItem(icon, event -> this.select(p, theme)), slot % 9, slot / 9);
        }

        pane.addItem(new GuiItem(this.item(Material.ARROW, l.choose("Voltar", "Back"), List.of(), false), event -> this.back.accept(p)), 4, 5);

        gui.addPane(Slot.fromXY(0, 0), pane);
        gui.show(p);
    }

    private void select(Player p, LevelColorTheme theme) {
        Language l = Language.of(p);
        if (!this.colors.select(p, theme)) {
            p.sendMessage(Component.text(l.choose("Você precisa do Nível Global ", "You need Global Level ") + theme.requiredLevel() + l.choose(" para usar este tema.", " to use this theme."), NamedTextColor.RED));
            return;
        }
        p.sendMessage(Component.text(l.choose("Tema de nível selecionado: ", "Level theme selected: ") + theme.name(), NamedTextColor.GREEN));
        this.open(p);
    }

    private String palette(LevelColorTheme theme) {
        StringJoiner out = new StringJoiner(" → ");
        for (int color : theme.palette()) {
            out.add(String.format("#%06X", color));
        }
        return out.toString();
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, (TextColor) color);
    }

    private ItemStack item(Material material, String name, List<Component> lore, boolean glint) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, (TextColor) NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        meta.setEnchantmentGlintOverride(glint);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
}
