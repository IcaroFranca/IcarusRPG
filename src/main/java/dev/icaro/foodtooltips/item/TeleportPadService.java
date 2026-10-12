package dev.icaro.foodtooltips.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Ender Pearl Collection's Teleport Pad, placed as an End Portal Frame (unbreakable in
 * survival, can't be pushed or blown up). Every placed pad has an owner, a name and a color; its
 * name floats above it as a hologram ({@link TextDisplay}) in that color.
 *
 * <p>Right-clicking a pad opens its 4-row menu for its owner: slot {@value #DEST_SLOT} the
 * destinations - every OTHER pad the same owner has placed, in any world - slot {@value
 * #COLOR_SLOT} the 16 dye colors, slot {@value #RENAME_SLOT} renaming (the client's own sign
 * editor, on a sign that only exists for that player for a moment - see {@link #startRename}),
 * slot {@value #CLOSE_SLOT} close and slot {@value #PICKUP_SLOT} picking the pad back up as an
 * item, and slot {@value #ACCESS_SLOT} who else may use it.
 *
 * <p><b>Access</b> is per pad: "allowed players only" (the default - the owner plus whoever they
 * added from the online players) or "public" (everyone). A player with access can travel with the
 * pad but never change it: their right-click opens just the destination list - the owner's other
 * pads they also have access to - and every settings screen/button stays owner-only.
 *
 * <p>Pads live in {@code plugins/IcarusRPG/teleport_pads.yml}, saved on every change. A pad whose
 * block isn't an End Portal Frame anymore (broken in creative, replaced by a command...) is
 * dropped the next time anything looks at it.
 */
public final class TeleportPadService implements Listener {
    public static final NamespacedKey ITEM_KEY = new NamespacedKey("foodtooltips", "teleport_pad");
    private static final NamespacedKey HOLOGRAM_KEY = new NamespacedKey("foodtooltips", "teleport_pad_hologram");
    private static final String CLOSE_TEXTURE = HeadTexture.CLOSE;
    private static final String DEFAULT_NAME = "Teleport Pad";
    private static final int MAX_NAME_LENGTH = 32;

    static final int DEST_SLOT = 11;
    static final int COLOR_SLOT = 13;
    static final int RENAME_SLOT = 15;
    static final int CLOSE_SLOT = 31;
    static final int PICKUP_SLOT = 35;
    static final int ACCESS_SLOT = 22;
    /** Access screen: mode toggle, "add player" and the allowed players' heads. */
    private static final int MODE_SLOT = 4;
    private static final int ADD_PLAYER_SLOT = 48;
    private static final int FIRST_PLAYER_SLOT = 9;
    private static final int LAST_PLAYER_SLOT = 44;
    /** Destination/color screens: back button. */
    private static final int BACK_SLOT = 49;
    private static final int COLOR_BACK_SLOT = 31;
    /** The 16 dyes, two rows of 8 in a 4-row screen. */
    private static final int[] COLOR_SLOTS = {1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int MAX_DESTINATIONS = 45;

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Pad> pads = new LinkedHashMap<>();
    /** Player -> pad they're renaming, plus where their fake sign is. */
    private final Map<UUID, Rename> renaming = new HashMap<>();

    /** One placed pad. Mutable name/color - saved through {@link #save}. */
    static final class Pad {
        final UUID id;
        final UUID owner;
        final String world;
        final int x;
        final int y;
        final int z;
        String name;
        DyeColor color;
        UUID hologram;
        /** Anyone may use it - otherwise only the owner and {@link #allowed}. */
        boolean publicAccess;
        final java.util.Set<UUID> allowed = new java.util.LinkedHashSet<>();

        Pad(UUID id, UUID owner, String world, int x, int y, int z, String name, DyeColor color, UUID hologram) {
            this.id = id;
            this.owner = owner;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            this.color = color;
            this.hologram = hologram;
        }

        Block block() {
            World w = Bukkit.getWorld(this.world);
            return w == null ? null : w.getBlockAt(this.x, this.y, this.z);
        }
    }

    private record Rename(UUID pad, Location signAt, BlockData realBlock) {
    }

    private enum Screen { MAIN, DESTINATIONS, COLORS, ACCESS, ADD_PLAYER }

    private static final class MenuHolder implements InventoryHolder {
        private final Screen screen;
        private final UUID pad;
        private final Map<Integer, UUID> destinations = new HashMap<>();
        /** Access screens: slot -> player shown there. */
        private final Map<Integer, UUID> players = new HashMap<>();
        private Inventory inventory;

        MenuHolder(Screen screen, UUID pad) {
            this.screen = screen;
            this.pad = pad;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    public TeleportPadService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teleport_pads.yml");
        this.load();
    }

    // ---- Item ----

    /** The Teleport Pad item - also what {@code CombatCollectionsItemsService} crafts. */
    public static ItemStack createItem() {
        ItemStack item = new ItemStack(Material.END_PORTAL_FRAME);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(DEFAULT_NAME, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Place it, then right click it to travel", NamedTextColor.GRAY),
                line("to any other Teleport Pad you placed.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    /** A Teleport Pad item - by its tag, or (crafted before it had one) an End Portal Frame named "Teleport Pad". */
    public static boolean isItem(ItemStack item) {
        if (item == null || item.getType() != Material.END_PORTAL_FRAME || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(ITEM_KEY, PersistentDataType.BYTE)
                || (meta.hasDisplayName() && DEFAULT_NAME.equals(PlainTextComponentSerializer.plainText().serialize(meta.displayName())));
    }

    // ---- Placing / breaking ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (!isItem(e.getItemInHand())) {
            return;
        }
        Block b = e.getBlockPlaced();
        Pad pad = new Pad(UUID.randomUUID(), e.getPlayer().getUniqueId(), b.getWorld().getName(), b.getX(), b.getY(), b.getZ(),
                DEFAULT_NAME, DyeColor.WHITE, null);
        this.pads.put(pad.id, pad);
        this.refreshHologram(pad);
        this.save();
        e.getPlayer().sendMessage(Component.text("Teleport Pad placed - right click it to set it up.", NamedTextColor.LIGHT_PURPLE));
    }

    /** End Portal Frames can only be broken in creative - clean the pad up then. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakPad(BlockBreakEvent e) {
        Pad pad = this.padAt(e.getBlock());
        if (pad != null) {
            this.remove(pad);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }
        Pad pad = this.padAt(e.getClickedBlock());
        if (pad == null) {
            return;
        }
        // Also stops an Eye of Ender from being put into the frame.
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!pad.owner.equals(p.getUniqueId())) {
            // A guest: travelling is allowed (with access), changing the pad isn't.
            if (!canUse(pad, p.getUniqueId())) {
                p.sendActionBar(Component.text("You don't have access to this Teleport Pad.", NamedTextColor.RED));
                return;
            }
            this.openDestinations(p, pad);
            return;
        }
        this.openMain(p, pad);
    }

    // ---- Menus ----

    void openMain(Player p, Pad pad) {
        MenuHolder holder = new MenuHolder(Screen.MAIN, pad.id);
        Inventory inv = Bukkit.createInventory(holder, 36, "Teleport Pad");
        holder.inventory = inv;
        fill(inv);
        inv.setItem(DEST_SLOT, named(Material.ENDER_PEARL, "Select Destination", NamedTextColor.LIGHT_PURPLE,
                line("Travel to another of your Teleport Pads.", NamedTextColor.GRAY)));
        inv.setItem(COLOR_SLOT, named(Material.END_PORTAL_FRAME, "Change Color", NamedTextColor.AQUA,
                line("Color of this pad's name.", NamedTextColor.GRAY)));
        inv.setItem(RENAME_SLOT, named(Material.NAME_TAG, "Rename", NamedTextColor.YELLOW,
                line("Current: ", NamedTextColor.GRAY).append(this.nameComponent(pad))));
        inv.setItem(ACCESS_SLOT, named(Material.TRIPWIRE_HOOK, "Access", NamedTextColor.GOLD,
                line(pad.publicAccess ? "Public - anyone can use it." : "Only you and " + pad.allowed.size() + " allowed player" + (pad.allowed.size() == 1 ? "" : "s") + ".", NamedTextColor.GRAY),
                line("Click to change who can use this pad.", NamedTextColor.YELLOW)));
        inv.setItem(CLOSE_SLOT, closeButton());
        inv.setItem(PICKUP_SLOT, named(Material.BEDROCK, "Pick Up", NamedTextColor.RED,
                line("Remove this Teleport Pad and get it back.", NamedTextColor.GRAY)));
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
    }

    private void openDestinations(Player p, Pad from) {
        MenuHolder holder = new MenuHolder(Screen.DESTINATIONS, from.id);
        Inventory inv = Bukkit.createInventory(holder, 54, "Select Destination");
        holder.inventory = inv;
        fill(inv);
        int slot = 0;
        for (Pad pad : this.ownedBy(from.owner)) {
            if (pad.id.equals(from.id) || slot >= MAX_DESTINATIONS || !canUse(pad, p.getUniqueId())) {
                continue;
            }
            ItemStack icon = new ItemStack(woolOf(pad.color));
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(this.nameComponent(pad).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line(worldLabel(pad.world) + " - " + pad.x + ", " + pad.y + ", " + pad.z, NamedTextColor.GRAY));
            if (pad.world.equals(p.getWorld().getName())) {
                lore.add(line(Math.round(p.getLocation().distance(new Location(p.getWorld(), pad.x, pad.y, pad.z))) + " blocks away", NamedTextColor.DARK_GRAY));
            }
            lore.add(line("Click to teleport!", NamedTextColor.YELLOW));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            holder.destinations.put(slot, pad.id);
            slot++;
        }
        if (slot == 0) {
            inv.setItem(22, named(Material.BARRIER, "No other Teleport Pads", NamedTextColor.RED,
                    line("Place another one to travel between them.", NamedTextColor.GRAY)));
        }
        inv.setItem(BACK_SLOT, backButton());
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
    }

    private void openColors(Player p, Pad pad) {
        MenuHolder holder = new MenuHolder(Screen.COLORS, pad.id);
        Inventory inv = Bukkit.createInventory(holder, 36, "Change Color");
        holder.inventory = inv;
        fill(inv);
        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length && i < COLOR_SLOTS.length; i++) {
            DyeColor c = colors[i];
            ItemStack dye = new ItemStack(dyeOf(c));
            ItemMeta meta = dye.getItemMeta();
            meta.displayName(Component.text(pretty(c.name()), textColor(c)).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(line(c == pad.color ? "Current color" : "Click to use this color.", c == pad.color ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
            if (c == pad.color) {
                meta.setEnchantmentGlintOverride(true);
            }
            dye.setItemMeta(meta);
            inv.setItem(COLOR_SLOTS[i], dye);
        }
        inv.setItem(COLOR_BACK_SLOT, backButton());
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
    }

    /** Whether {@code player} may travel with {@code pad} - its owner, anyone if it's public, else only an allowed player. */
    static boolean canUse(Pad pad, UUID player) {
        return pad.owner.equals(player) || pad.publicAccess || pad.allowed.contains(player);
    }

    private void openAccess(Player p, Pad pad) {
        MenuHolder holder = new MenuHolder(Screen.ACCESS, pad.id);
        Inventory inv = Bukkit.createInventory(holder, 54, "Access");
        holder.inventory = inv;
        fill(inv);
        inv.setItem(MODE_SLOT, pad.publicAccess
                ? named(Material.LIME_DYE, "Public", NamedTextColor.GREEN, line("Anyone can travel with this pad.", NamedTextColor.GRAY),
                        line("Click: only allowed players.", NamedTextColor.YELLOW))
                : named(Material.RED_DYE, "Allowed players only", NamedTextColor.RED, line("Only you and the players below.", NamedTextColor.GRAY),
                        line("Click: make it public.", NamedTextColor.YELLOW)));
        int slot = FIRST_PLAYER_SLOT;
        for (UUID id : pad.allowed) {
            if (slot > LAST_PLAYER_SLOT) {
                break;
            }
            inv.setItem(slot, playerHead(id, line("Click to remove.", NamedTextColor.RED)));
            holder.players.put(slot, id);
            slot++;
        }
        inv.setItem(ADD_PLAYER_SLOT, named(Material.EMERALD, "Add Player", NamedTextColor.GREEN,
                line("Pick one of the players online now.", NamedTextColor.GRAY)));
        inv.setItem(BACK_SLOT, backButton());
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
    }

    private void openAddPlayer(Player p, Pad pad) {
        MenuHolder holder = new MenuHolder(Screen.ADD_PLAYER, pad.id);
        Inventory inv = Bukkit.createInventory(holder, 54, "Add Player");
        holder.inventory = inv;
        fill(inv);
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot > LAST_PLAYER_SLOT) {
                break;
            }
            if (online.getUniqueId().equals(pad.owner) || pad.allowed.contains(online.getUniqueId())) {
                continue;
            }
            inv.setItem(slot, playerHead(online.getUniqueId(), line("Click to allow.", NamedTextColor.GREEN)));
            holder.players.put(slot, online.getUniqueId());
            slot++;
        }
        if (slot == 0) {
            inv.setItem(22, named(Material.BARRIER, "Nobody to add", NamedTextColor.RED,
                    line("Everyone online already has access.", NamedTextColor.GRAY)));
        }
        inv.setItem(BACK_SLOT, backButton());
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
    }

    private static ItemStack playerHead(UUID id, Component action) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        org.bukkit.OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        meta.setOwningPlayer(player);
        meta.displayName(Component.text(player.getName() == null ? id.toString() : player.getName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(action));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        e.setCancelled(true);
        if (e.getRawSlot() < 0 || e.getRawSlot() >= holder.inventory.getSize()) {
            return;
        }
        Pad pad = this.pads.get(holder.pad);
        if (pad == null || !this.stillPlaced(pad)) {
            p.closeInventory();
            return;
        }
        int slot = e.getRawSlot();
        boolean owner = pad.owner.equals(p.getUniqueId());
        if (!owner && holder.screen != Screen.DESTINATIONS) {
            // Only the owner gets the settings screens - a stale one (ownership can't change, but be safe) just closes.
            p.closeInventory();
            return;
        }
        switch (holder.screen) {
            case MAIN -> {
                switch (slot) {
                    case DEST_SLOT -> this.openDestinations(p, pad);
                    case COLOR_SLOT -> this.openColors(p, pad);
                    case RENAME_SLOT -> this.startRename(p, pad);
                    case CLOSE_SLOT -> p.closeInventory();
                    case PICKUP_SLOT -> this.pickUp(p, pad);
                    case ACCESS_SLOT -> this.openAccess(p, pad);
                    default -> {
                    }
                }
            }
            case ACCESS -> {
                if (slot == MODE_SLOT) {
                    pad.publicAccess = !pad.publicAccess;
                    this.save();
                    this.openAccess(p, pad);
                } else if (slot == ADD_PLAYER_SLOT) {
                    this.openAddPlayer(p, pad);
                } else if (slot == BACK_SLOT) {
                    this.openMain(p, pad);
                } else if (holder.players.containsKey(slot)) {
                    pad.allowed.remove(holder.players.get(slot));
                    this.save();
                    this.openAccess(p, pad);
                }
            }
            case ADD_PLAYER -> {
                if (slot == BACK_SLOT) {
                    this.openAccess(p, pad);
                } else if (holder.players.containsKey(slot)) {
                    pad.allowed.add(holder.players.get(slot));
                    this.save();
                    this.openAccess(p, pad);
                }
            }
            case DESTINATIONS -> {
                if (slot == BACK_SLOT) {
                    if (owner) {
                        this.openMain(p, pad);
                    } else {
                        p.closeInventory();
                    }
                } else if (holder.destinations.containsKey(slot)) {
                    Pad to = this.pads.get(holder.destinations.get(slot));
                    // Access may have been taken away while the list was open.
                    if (!canUse(pad, p.getUniqueId()) || (to != null && !canUse(to, p.getUniqueId()))) {
                        p.closeInventory();
                        p.sendActionBar(Component.text("You don't have access to this Teleport Pad.", NamedTextColor.RED));
                        return;
                    }
                    this.teleport(p, to);
                }
            }
            case COLORS -> {
                if (slot == COLOR_BACK_SLOT) {
                    this.openMain(p, pad);
                    return;
                }
                for (int i = 0; i < COLOR_SLOTS.length; i++) {
                    if (COLOR_SLOTS[i] == slot && i < DyeColor.values().length) {
                        pad.color = DyeColor.values()[i];
                        this.refreshHologram(pad);
                        this.save();
                        this.openColors(p, pad);
                    }
                }
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            e.setCancelled(true);
        }
    }

    // ---- Actions ----

    private void teleport(Player p, Pad to) {
        if (to == null || !this.stillPlaced(to)) {
            p.sendMessage(Component.text("That Teleport Pad isn't there anymore.", NamedTextColor.RED));
            p.closeInventory();
            return;
        }
        Block b = to.block();
        Location dest = b.getLocation().add(0.5, 1.0, 0.5);
        dest.setYaw(p.getLocation().getYaw());
        dest.setPitch(p.getLocation().getPitch());
        p.closeInventory();
        // Paper drops a cross-world teleport for a player with a passenger - see travel.TravelMenuService#teleportTo.
        for (Entity passenger : new ArrayList<>(p.getPassengers())) {
            p.removePassenger(passenger);
        }
        b.getChunk().load();
        if (p.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            p.sendActionBar(Component.text("Teleported to ", NamedTextColor.LIGHT_PURPLE).append(this.nameComponent(to)));
        } else {
            p.sendMessage(Component.text("Couldn't teleport right now. Try again.", NamedTextColor.RED));
        }
    }

    private void pickUp(Player p, Pad pad) {
        p.closeInventory();
        Block b = pad.block();
        this.remove(pad);
        if (b != null) {
            b.setType(Material.AIR);
        }
        for (ItemStack overflow : p.getInventory().addItem(createItem()).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
        p.sendActionBar(Component.text("Teleport Pad picked up.", NamedTextColor.LIGHT_PURPLE));
    }

    /**
     * Opens the client's sign editor on a sign that only exists for {@code p}, two blocks below
     * them (inside the ground, so it isn't in the way), pre-filled with the pad's current name -
     * {@link #renamed} reads what they typed and puts the real block back for them.
     */
    private void startRename(Player p, Pad pad) {
        p.closeInventory();
        Location at = p.getLocation().getBlock().getLocation().subtract(0, 2, 0);
        if (at.getY() < p.getWorld().getMinHeight()) {
            at.setY(p.getWorld().getMinHeight());
        }
        BlockData real = at.getBlock().getBlockData();
        this.renaming.put(p.getUniqueId(), new Rename(pad.id, at, real));
        p.sendBlockChange(at, Material.OAK_SIGN.createBlockData());
        p.sendSignChange(at, List.of(Component.text(pad.name), Component.empty(), Component.empty(), Component.empty()));
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (p.isOnline() && this.renaming.containsKey(p.getUniqueId())) {
                p.openVirtualSign(io.papermc.paper.math.Position.block(at), Side.FRONT);
            }
        }, 2L);
    }

    @EventHandler
    public void renamed(UncheckedSignChangeEvent e) {
        Player p = e.getPlayer();
        Rename rename = this.renaming.remove(p.getUniqueId());
        if (rename == null) {
            return;
        }
        e.setCancelled(true);
        p.sendBlockChange(rename.signAt(), rename.realBlock());
        StringBuilder typed = new StringBuilder();
        for (Component line : e.lines()) {
            String text = PlainTextComponentSerializer.plainText().serialize(line).trim();
            if (!text.isEmpty()) {
                if (typed.length() > 0) {
                    typed.append(' ');
                }
                typed.append(text);
            }
        }
        Pad pad = this.pads.get(rename.pad());
        if (pad == null) {
            return;
        }
        String name = typed.toString().trim();
        if (name.isEmpty()) {
            p.sendActionBar(Component.text("Name unchanged.", NamedTextColor.GRAY));
        } else {
            pad.name = name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
            this.refreshHologram(pad);
            this.save();
            p.sendActionBar(Component.text("Renamed to ", NamedTextColor.LIGHT_PURPLE).append(this.nameComponent(pad)));
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (p.isOnline() && this.stillPlaced(pad)) {
                this.openMain(p, pad);
            }
        });
    }

    // ---- Hologram ----

    /** Spawns (or updates) the pad's floating name - persistent, so it stays with the chunk; found again by its UUID. */
    private void refreshHologram(Pad pad) {
        Block b = pad.block();
        if (b == null) {
            return;
        }
        Entity existing = pad.hologram == null ? null : Bukkit.getEntity(pad.hologram);
        if (existing instanceof TextDisplay display && display.isValid()) {
            display.text(this.nameComponent(pad));
            return;
        }
        Location at = b.getLocation().add(0.5, 1.6, 0.5);
        TextDisplay display = b.getWorld().spawn(at, TextDisplay.class, d -> {
            d.text(this.nameComponent(pad));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(true);
            d.setShadowed(true);
            d.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, pad.id.toString());
        });
        pad.hologram = display.getUniqueId();
    }

    private void removeHologram(Pad pad) {
        Entity existing = pad.hologram == null ? null : Bukkit.getEntity(pad.hologram);
        if (existing != null) {
            existing.remove();
        }
        Block b = pad.block();
        if (b != null) {
            // Belt and braces: any display tagged with this pad near it (e.g. its UUID got lost).
            for (Entity e : b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 1.5, 0.5), 1.5, 2, 1.5)) {
                if (e instanceof TextDisplay && pad.id.toString().equals(e.getPersistentDataContainer().get(HOLOGRAM_KEY, PersistentDataType.STRING))) {
                    e.remove();
                }
            }
        }
    }

    private Component nameComponent(Pad pad) {
        return Component.text(pad.name, textColor(pad.color));
    }

    // ---- Registry ----

    Pad padAt(Block block) {
        for (Pad pad : this.pads.values()) {
            if (pad.x == block.getX() && pad.y == block.getY() && pad.z == block.getZ() && pad.world.equals(block.getWorld().getName())) {
                return pad;
            }
        }
        return null;
    }

    List<Pad> ownedBy(UUID owner) {
        List<Pad> out = new ArrayList<>();
        for (Pad pad : new ArrayList<>(this.pads.values())) {
            if (pad.owner.equals(owner) && this.stillPlaced(pad)) {
                out.add(pad);
            }
        }
        return out;
    }

    /** Whether {@code pad}'s block is still an End Portal Frame - drops it from the registry if not (only checked while its chunk is loaded, so an unloaded world never wipes pads). */
    private boolean stillPlaced(Pad pad) {
        Block b = pad.block();
        if (b == null) {
            return false;
        }
        if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) {
            return true;
        }
        if (b.getType() == Material.END_PORTAL_FRAME) {
            return true;
        }
        this.remove(pad);
        return false;
    }

    private void remove(Pad pad) {
        this.removeHologram(pad);
        this.pads.remove(pad.id);
        this.save();
    }

    private void load() {
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                DyeColor color = DyeColor.valueOf(s.getString("color", "WHITE"));
                String hologram = s.getString("hologram");
                this.pads.put(UUID.fromString(key), new Pad(UUID.fromString(key), UUID.fromString(s.getString("owner")),
                        s.getString("world"), s.getInt("x"), s.getInt("y"), s.getInt("z"), s.getString("name", DEFAULT_NAME), color,
                        hologram == null ? null : UUID.fromString(hologram)));
                Pad loaded = this.pads.get(UUID.fromString(key));
                loaded.publicAccess = s.getBoolean("public", false);
                for (String allowed : s.getStringList("allowed")) {
                    loaded.allowed.add(UUID.fromString(allowed));
                }
            } catch (RuntimeException ex) {
                this.plugin.getLogger().warning("teleport_pads.yml: skipped a broken pad entry " + key + " (" + ex.getMessage() + ")");
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Pad pad : this.pads.values()) {
            String k = pad.id.toString();
            yaml.set(k + ".owner", pad.owner.toString());
            yaml.set(k + ".world", pad.world);
            yaml.set(k + ".x", pad.x);
            yaml.set(k + ".y", pad.y);
            yaml.set(k + ".z", pad.z);
            yaml.set(k + ".name", pad.name);
            yaml.set(k + ".color", pad.color.name());
            yaml.set(k + ".hologram", pad.hologram == null ? null : pad.hologram.toString());
            yaml.set(k + ".public", pad.publicAccess);
            yaml.set(k + ".allowed", pad.allowed.stream().map(UUID::toString).toList());
        }
        try {
            yaml.save(this.file);
        } catch (IOException ex) {
            this.plugin.getLogger().warning("Couldn't save teleport_pads.yml: " + ex.getMessage());
        }
    }

    // ---- Helpers ----

    /** Text color of a dye - its own RGB, except Black, nudged up so the name stays readable. */
    /** {@code color} as a text color (black lightened so it reads on dark tooltips) - shared with the spawner menu's own names. */
    public static TextColor textColor(DyeColor color) {
        if (color == DyeColor.BLACK) {
            return TextColor.color(0x3A3A3A);
        }
        return TextColor.color(color.getColor().asRGB());
    }

    private static Material dyeOf(DyeColor c) {
        return Material.valueOf(c.name() + "_DYE");
    }

    private static Material woolOf(DyeColor c) {
        return Material.valueOf(c.name() + "_WOOL");
    }

    private static String worldLabel(String world) {
        World w = Bukkit.getWorld(world);
        if (w == null) {
            return world;
        }
        return switch (w.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> "Overworld";
        };
    }

    private static String pretty(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String w : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack named(Material material, String name, NamedTextColor color, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private static void fill(Inventory inv) {
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.WHITE);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private static ItemStack closeButton() {
        return head(CLOSE_TEXTURE, "Close");
    }

    private static ItemStack backButton() {
        return head(HeadTexture.BACK, "Back");
    }

    private static ItemStack head(String texture, String name) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: plain head.
        }
        meta.displayName(Component.text(name, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
