package dev.icaro.foodtooltips.travel;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * The /skills "Locais" button: a short list of teleport destinations, free and
 * unlimited (replaces the old consumable ticket item). World names are never shown to
 * the player - each destination gets a human-readable name instead. The Combat Island
 * entry is gated by Combat Level (see {@link #combatIslandMinLevel()}, read by
 * SkillsMenuService to add a matching "Unlocks: ..." line on that level's node).
 */
public final class TravelMenuService {
    private final Plugin plugin;
    private final CombatSkillService combat;
    private final Consumer<Player> back;
    private final String defaultWorld;
    private final String islandWorld;
    private final int islandMinLevel;

    public TravelMenuService(Plugin plugin, CombatSkillService combat, Consumer<Player> back) {
        this.plugin = plugin;
        this.combat = combat;
        this.back = back;
        this.defaultWorld = plugin.getConfig().getString("travel.default-world", "world");
        this.islandWorld = plugin.getConfig().getString("island-mobs.world", "combat_island");
        this.islandMinLevel = plugin.getConfig().getInt("travel.combat-island-min-level", 5);
    }

    public int combatIslandMinLevel() {
        return this.islandMinLevel;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        ChestGui gui = new ChestGui(3, l.choose("Locais", "Locations"), this.plugin);
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(9, 3);
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), NamedTextColor.GRAY);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        pane.addItem(new GuiItem(this.item(Material.GRASS_BLOCK, l.choose("Mundo Padrão", "Default World"),
                List.of(this.text(l.choose("Clique para teleportar.", "Click to teleport."), NamedTextColor.YELLOW)), NamedTextColor.GOLD),
                event -> this.travel(p, this.defaultWorld)), 3, 1);

        boolean unlocked = this.combat.progress(p).level() >= this.islandMinLevel;
        List<Component> islandLore = unlocked
                ? List.of(this.text(l.choose("Clique para teleportar.", "Click to teleport."), NamedTextColor.YELLOW))
                : List.of(this.text(l.choose("Requer Nível de Combate ", "Requires Combat Level ") + this.islandMinLevel + ".", NamedTextColor.RED));
        pane.addItem(new GuiItem(this.item(unlocked ? Material.NETHERITE_SWORD : Material.GRAY_DYE, l.choose("Ilha de Combate", "Combat Island"), islandLore, unlocked ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY),
                event -> {
                    if (!unlocked) {
                        p.sendMessage(Component.text(l.choose("Você precisa do Nível de Combate ", "You need Combat Level ") + this.islandMinLevel
                                + l.choose(" para acessar a Ilha de Combate.", " to access the Combat Island."), NamedTextColor.RED));
                        return;
                    }
                    this.travel(p, this.islandWorld);
                }), 5, 1);

        pane.addItem(new GuiItem(this.item(Material.ARROW, l.choose("Voltar", "Back"), List.of(), NamedTextColor.GOLD), event -> this.back.accept(p)), 4, 2);

        gui.addPane(Slot.fromXY(0, 0), pane);
        gui.show(p);
    }

    private void travel(Player p, String worldName) {
        Language l = Language.of(p);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            p.sendMessage(Component.text(l.choose("Esse local não está disponível agora.", "That location isn't available right now."), NamedTextColor.RED));
            return;
        }
        p.closeInventory();
        p.teleportAsync(world.getSpawnLocation());
        p.sendMessage(Component.text(l.choose("Teleportado!", "Teleported!"), NamedTextColor.GREEN));
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, (TextColor) color);
    }

    private ItemStack item(Material material, String name, List<Component> lore, NamedTextColor color) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, (TextColor) color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
}
