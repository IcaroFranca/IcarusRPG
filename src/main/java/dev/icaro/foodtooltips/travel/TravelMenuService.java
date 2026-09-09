package dev.icaro.foodtooltips.travel;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
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
        // Blank/unset falls back to the server's actual primary world (server.properties'
        // level-name, always Bukkit.getWorlds().get(0)) instead of a hardcoded guess like
        // "world" - Multiverse and similar setups often name it something else entirely.
        String configuredDefault = plugin.getConfig().getString("travel.default-world", "");
        this.defaultWorld = configuredDefault.isBlank() ? Bukkit.getWorlds().get(0).getName() : configuredDefault;
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
        ItemStack islandIcon = unlocked
                ? this.customHeadItem(HeadTexture.ALIEN_GRASS, l.choose("Ilha de Combate", "Combat Island"), islandLore, NamedTextColor.GOLD)
                : this.item(Material.GRAY_DYE, l.choose("Ilha de Combate", "Combat Island"), islandLore, NamedTextColor.DARK_GRAY);
        pane.addItem(new GuiItem(islandIcon,
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
        // Deliberately synchronous (Entity#teleport, not #teleportAsync) AND explicitly
        // TeleportCause.COMMAND, not the implicit PLUGIN default a bare teleport() call
        // gets - live testing showed /execute ... run tp (cause COMMAND) always lands,
        // while both Multiverse's own /mvtp (via PaperLib) and a bare teleportAsync/
        // teleport() call here (cause PLUGIN) always failed the same way (no exception,
        // just a false result) - something on this server treats those two causes
        // differently. Matching the cause that's proven to work sidesteps whatever that
        // is, whatever it turns out to be, without needing to actually find it. Trade-off
        // of going synchronous: a teleport into a chunk that still needs generating from
        // scratch blocks the main thread for that moment instead of loading in the
        // background - a one-time cost per chunk, and both destinations here are always
        // a world's own spawn point, so in practice it's already generated almost every
        // time this runs.
        boolean success = p.teleport(world.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.COMMAND);
        if (success) {
            p.sendMessage(Component.text(l.choose("Teleportado!", "Teleported!"), NamedTextColor.GREEN));
        } else {
            p.sendMessage(Component.text(l.choose("Não foi possível teleportar agora. Tente de novo.", "Couldn't teleport right now. Try again."), NamedTextColor.RED));
        }
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

    /** A player head wearing {@code texture} (base64 "Value"), same name/lore treatment as {@link #item} - falls back to a plain head if the texture is bad. */
    private ItemStack customHeadItem(String texture, String name, List<Component> lore, NamedTextColor color) {
        ItemStack stack = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        meta.displayName(Component.text(name, (TextColor) color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
}
