package dev.icaro.foodtooltips.travel;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.collections.CollectionsEntry;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
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
 * the player - each destination gets a human-readable name instead.
 */
public final class TravelMenuService {
    /** How far {@link #travelToNearestBirchForest} searches for a Birch Forest before giving up - a moderate bound so a search launched far from any match can't hang the main thread for long (this runs synchronously, same as every other destination here). */
    private static final int BIOME_SEARCH_RADIUS = 512;

    private final Plugin plugin;
    private final Consumer<Player> back;
    private final CollectionsProgressService collectionsProgress;
    private final String defaultWorld;

    public TravelMenuService(Plugin plugin, Consumer<Player> back, CollectionsProgressService collectionsProgress) {
        this.plugin = plugin;
        this.back = back;
        this.collectionsProgress = collectionsProgress;
        // Blank/unset falls back to the server's actual primary world (server.properties'
        // level-name, always Bukkit.getWorlds().get(0)) instead of a hardcoded guess like
        // "world" - Multiverse and similar setups often name it something else entirely.
        String configuredDefault = plugin.getConfig().getString("travel.default-world", "");
        this.defaultWorld = configuredDefault.isBlank() ? Bukkit.getWorlds().get(0).getName() : configuredDefault;
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

        pane.addItem(new GuiItem(this.item(Material.RED_BED, l.choose("Respawn (Cama/Âncora)", "Bed/Anchor Spawn"),
                List.of(this.text(l.choose("Clique para teleportar.", "Click to teleport."), NamedTextColor.YELLOW)), NamedTextColor.GOLD),
                event -> this.travelToBedSpawn(p)), 5, 1);

        if (this.collectionsProgress.achieved(p, this.birchLogEntry()) >= 3) {
            pane.addItem(new GuiItem(this.item(Material.BIRCH_SAPLING, l.choose("Floresta de Bétulas Mais Próxima", "Nearest Birch Forest"),
                    List.of(this.text(l.choose("Clique para teleportar.", "Click to teleport."), NamedTextColor.YELLOW)), NamedTextColor.GOLD),
                    event -> this.travelToNearestBirchForest(p)), 1, 1);
        }

        pane.addItem(new GuiItem(this.customHeadItem(HeadTexture.BACK, l.choose("Voltar", "Back"), List.of(), NamedTextColor.GOLD), event -> this.back.accept(p)), 4, 2);

        gui.addPane(Slot.fromXY(0, 0), pane);
        gui.show(p);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
    }

    private void travel(Player p, String worldName) {
        Language l = Language.of(p);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            p.sendMessage(Component.text(l.choose("Esse local não está disponível agora.", "That location isn't available right now."), NamedTextColor.RED));
            return;
        }
        p.closeInventory();
        this.teleportTo(p, world.getSpawnLocation());
    }

    /**
     * {@code Player#getRespawnLocation()} - the player's own last-set bed or respawn
     * anchor, whichever they most recently slept in/activated (Paper's modern
     * replacement for the deprecated per-world {@code getBedSpawnLocation()}, so this
     * already covers a Nether respawn anchor same as an Overworld bed). Null if
     * they've never set one, or it's since become invalid (block broken, wrong
     * dimension for a bed, etc. - same cases vanilla itself falls back to world spawn
     * for on death).
     */
    private void travelToBedSpawn(Player p) {
        Language l = Language.of(p);
        Location destination = p.getRespawnLocation();
        if (destination == null || destination.getWorld() == null) {
            p.sendMessage(Component.text(l.choose("Você não tem uma cama ou âncora de respawn marcada.", "You don't have a bed or respawn anchor set."), NamedTextColor.RED));
            return;
        }
        p.closeInventory();
        this.teleportTo(p, destination);
    }

    private CollectionsEntry birchLogEntry() {
        return CollectionsCatalog.find(Material.BIRCH_LOG).orElseThrow();
    }

    /** Birch Log Collections Milestone 3's own reward (gated in {@link #open} before this button is even shown) - a synchronous {@link World#locateNearestBiome} search, same "no async hop" choice every other destination here already makes, bounded by {@link #BIOME_SEARCH_RADIUS} so a search launched somewhere with no Birch Forest nearby can't hang the server for long. */
    private void travelToNearestBirchForest(Player p) {
        Language l = Language.of(p);
        Location origin = p.getLocation();
        Location destination = origin.getWorld().locateNearestBiome(origin, Biome.BIRCH_FOREST, BIOME_SEARCH_RADIUS);
        if (destination == null) {
            p.sendMessage(Component.text(l.choose("Nenhuma Floresta de Bétulas encontrada por perto.", "No Birch Forest found nearby."), NamedTextColor.RED));
            return;
        }
        p.closeInventory();
        this.teleportTo(p, destination);
    }

    /** Shared teleport plumbing both {@link #travel} and {@link #travelToBedSpawn} go through - chunk-loading and the passenger workaround apply the same way regardless of which destination was picked. */
    private void teleportTo(Player p, Location destination) {
        Language l = Language.of(p);
        // Force the destination chunk to be loaded before teleporting (harmless even
        // when it's already loaded, as it always is for a world's own spawn point).
        destination.getWorld().getChunkAt(destination);
        // Paper has a confirmed bug (github.com/PaperMC/Paper/issues/10168): a
        // cross-world teleport never even raises PlayerTeleportEvent - Entity#teleport
        // just returns false - if the player has any passenger riding them. Confirmed
        // live: another plugin on this server rides a TextDisplay on the player (not
        // IcarusRPG's own code - the only place this plugin ever mounts a TextDisplay is
        // MobVisualService, and only on mobs, never on a player). Dismounting right
        // before teleporting sidesteps the bug regardless of which plugin put it there;
        // if it's a live-updating overhead label, whatever attaches it re-mounts it on
        // its own on the next refresh.
        List<Entity> passengers = new ArrayList<>(p.getPassengers());
        for (Entity passenger : passengers) {
            p.removePassenger(passenger);
        }
        boolean success = p.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND);
        if (success) {
            p.sendMessage(Component.text(l.choose("Teleportado!", "Teleported!"), NamedTextColor.GREEN));
        } else {
            p.sendMessage(Component.text(l.choose("Não foi possível teleportar agora. Tente de novo.", "Couldn't teleport right now. Try again."), NamedTextColor.RED));
            this.plugin.getLogger().warning("Teleport recusado sem PlayerTeleportEvent - jogador=" + p.getName()
                    + " destino=" + destination.getWorld().getName() + " " + destination.getBlockX() + "," + destination.getBlockY() + "," + destination.getBlockZ()
                    + " chunkCarregado=" + destination.getWorld().isChunkLoaded(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)
                    + " jogadorValido=" + p.isValid() + " jogadorOnline=" + p.isOnline() + " mundoAtual=" + p.getWorld().getName()
                    + " passageirosAntesDeDesmontar=" + passengers + " veiculo=" + p.getVehicle());
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
