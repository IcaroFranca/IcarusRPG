package dev.icaro.foodtooltips.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.util.RightClickTrigger;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/**
 * The Weather Wand's ability (Redstone Collection M4 - the item itself is built by {@link
 * MiningCollectionsItemsService}): right-click opens a 4-row menu that sets the holder's world to
 * Thundering (slot {@value #THUNDER_SLOT}, Blaze Rod), Raining ({@value #RAIN_SLOT}, Water
 * Bucket) or Sunny ({@value #SUN_SLOT}, Sunflower), with a close button at {@value #CLOSE_SLOT} -
 * the player's own spec, names only on hover.
 *
 * <p>Right-click comes from Bukkit's {@link PlayerInteractEvent} deliberately WITHOUT {@code
 * ignoreCancelled}: Bukkit fires a right-click in the air already marked cancelled, so a listener
 * that skips cancelled events never sees it - which is how an earlier wand concluded the event
 * "never fired". When ProtocolLib is installed {@link RightClickTrigger} also catches the raw
 * packet (and cancels it, so the two paths never both open the menu). A right-click on a door,
 * chest or any other interactable block is left alone either way.
 *
 * <p>Each choice gets a random length in the same ranges vanilla's own {@code /weather} uses
 * when no duration is given, so the weather keeps cycling naturally afterwards.
 */
public final class WeatherWandService implements Listener {
    private static final int SIZE = 36;
    private static final int THUNDER_SLOT = 11;
    private static final int RAIN_SLOT = 13;
    private static final int SUN_SLOT = 15;
    private static final int CLOSE_SLOT = 31;

    private final Plugin plugin;

    public WeatherWandService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Marks this menu's own inventory - see {@link #isMenu}. */
    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    /** Call once from {@code FoodTooltipsPlugin#onEnable} - adds the ProtocolLib right-click path when it's installed. */
    public void start() {
        RightClickTrigger.registerIfAvailable(this.plugin,
                p -> MiningCollectionsItemsService.isWeatherWand(p.getInventory().getItemInMainHand()), this::open);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !MiningCollectionsItemsService.isWeatherWand(e.getItem())) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = e.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable()) {
                return;
            }
        } else if (e.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        e.setCancelled(true);
        this.open(e.getPlayer());
    }

    public void open(Player p) {
        if (isMenu(p.getOpenInventory().getTopInventory())) {
            return;
        }
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, "Weather Wand");
        holder.inventory = inv;
        ItemStack filler = this.named(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.WHITE);
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(THUNDER_SLOT, this.named(Material.BLAZE_ROD, "Thundering", NamedTextColor.GOLD));
        inv.setItem(RAIN_SLOT, this.named(Material.WATER_BUCKET, "Raining", NamedTextColor.AQUA));
        inv.setItem(SUN_SLOT, this.named(Material.SUNFLOWER, "Sunny", NamedTextColor.YELLOW));
        inv.setItem(CLOSE_SLOT, this.closeButton());
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !isMenu(e.getView().getTopInventory())) {
            return;
        }
        e.setCancelled(true);
        switch (e.getRawSlot()) {
            case THUNDER_SLOT -> this.apply(p, true, true, "Thundering");
            case RAIN_SLOT -> this.apply(p, true, false, "Raining");
            case SUN_SLOT -> this.apply(p, false, false, "Sunny");
            case CLOSE_SLOT -> p.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (isMenu(e.getView().getTopInventory())) {
            e.setCancelled(true);
        }
    }

    private void apply(Player p, boolean storm, boolean thunder, String label) {
        World world = p.getWorld();
        p.closeInventory();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            p.sendActionBar(Component.text("There's no weather in this dimension.", NamedTextColor.RED));
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (!storm) {
            // setStorm/setThundering reset the clear-weather timer, so they go first.
            world.setStorm(false);
            world.setThundering(false);
            world.setClearWeatherDuration(random.nextInt(12000, 180001));
        } else {
            world.setStorm(true);
            world.setThundering(thunder);
            world.setWeatherDuration(thunder ? random.nextInt(3600, 15601) : random.nextInt(12000, 24001));
            if (thunder) {
                world.setThunderDuration(world.getWeatherDuration());
            }
        }
        p.sendActionBar(Component.text("Weather set to " + label + ".", NamedTextColor.GREEN));
    }

    private static boolean isMenu(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof MenuHolder;
    }

    private ItemStack named(Material material, String name, NamedTextColor color) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack closeButton() {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", HeadTexture.CLOSE));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        meta.displayName(Component.text("Close", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
