package dev.icaro.foodtooltips.shop;

import dev.icaro.foodtooltips.economy.VaultEconomy;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * NPC shops paid in the server economy's coins (through {@link VaultEconomy} - ExcellentEconomy on
 * the player's server), defined in {@code plugins/IcarusRPG/shops.yml} (written from the jar's
 * own default, with the Combat Merchant, the first time it's missing; {@code /shop reload}
 * re-reads it). A shop opens with {@code /shop open <shop> <player>}, admin/console-only, so the
 * intended way in is a Citizens NPC running it from the console ({@code /npc command add shop open
 * combat_merchant <p>}) - players can't open a shop from anywhere by typing a command.
 *
 * <p>An entry's {@code item} is a vanilla {@link Material} or {@code icarus:<id>}, an item this
 * plugin builds itself (registered through {@link #registerItem} - e.g. the Combat Merchant's own
 * talismans). Left-click buys 1; right-click on a stackable item opens a picker for 5/10/25/50/64
 * (the player's own spec).
 */
public final class ShopService implements Listener, CommandExecutor, TabCompleter {
    private static final String CUSTOM_PREFIX = "icarus:";
    private static final int[] QUANTITIES = {5, 10, 25, 50, 64};
    private static final int[] QUANTITY_SLOTS = {20, 21, 22, 23, 24};
    private static final int HEADER_SLOT = 4;
    private static final int BACK_SLOT = 49;

    private final Plugin plugin;
    private final Map<String, Supplier<ItemStack>> customItems = new HashMap<>();
    private Map<String, Shop> shops = Map.of();

    record Shop(String id, String title, List<Entry> entries) {
    }

    record Entry(String item, long price) {
    }

    /** Marks a shop screen, so clicks on it are recognized (and every one of them cancelled - nothing in a shop can be taken out). */
    private static final class ShopHolder implements InventoryHolder {
        private final Shop shop;
        /** The item being picked a quantity for, or null on the shop's main screen. */
        private final Entry quantityFor;
        private final Map<Integer, Entry> entryButtons = new HashMap<>();
        private final Map<Integer, Integer> quantityButtons = new HashMap<>();
        private Inventory inventory;

        private ShopHolder(Shop shop, Entry quantityFor) {
            this.shop = shop;
            this.quantityFor = quantityFor;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    public ShopService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Makes {@code icarus:<id>} usable as a shop item - {@code factory} builds one fresh copy per call. */
    public void registerItem(String id, Supplier<ItemStack> factory) {
        this.customItems.put(id.toLowerCase(Locale.ROOT), factory);
    }

    /** (Re)reads {@code shops.yml}, writing the jar's own default first if it doesn't exist yet. Unknown items are skipped with a console warning. */
    public void load() {
        File file = new File(this.plugin.getDataFolder(), "shops.yml");
        if (!file.exists()) {
            this.plugin.saveResource("shops.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Shop> loaded = new LinkedHashMap<>();
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<Entry> entries = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("items")) {
                Object item = raw.get("item");
                Object price = raw.get("price");
                if (item == null || !(price instanceof Number number) || number.longValue() < 0) {
                    this.plugin.getLogger().warning("Shop '" + id + "': an item needs both 'item' and a non-negative 'price' - skipped " + raw);
                    continue;
                }
                Entry entry = new Entry(item.toString(), number.longValue());
                if (this.create(entry, 1) == null) {
                    this.plugin.getLogger().warning("Shop '" + id + "': unknown item '" + entry.item() + "' - skipped.");
                    continue;
                }
                entries.add(entry);
            }
            loaded.put(id.toLowerCase(Locale.ROOT), new Shop(id, section.getString("title", id), List.copyOf(entries)));
        }
        this.shops = loaded;
        this.plugin.getLogger().info("Loaded " + loaded.size() + " shop(s) from shops.yml.");
    }

    /** {@code amount} of {@code entry}'s item (a custom item is always built as a single fresh copy), or null if it doesn't resolve. */
    private ItemStack create(Entry entry, int amount) {
        String spec = entry.item().trim();
        if (spec.toLowerCase(Locale.ROOT).startsWith(CUSTOM_PREFIX)) {
            Supplier<ItemStack> factory = this.customItems.get(spec.substring(CUSTOM_PREFIX.length()).toLowerCase(Locale.ROOT));
            return factory == null ? null : factory.get();
        }
        Material material = Material.matchMaterial(spec);
        if (material == null || !material.isItem() || material.isAir()) {
            return null;
        }
        return new ItemStack(material, amount);
    }

    private boolean stackable(Entry entry) {
        ItemStack sample = this.create(entry, 1);
        return sample != null && !entry.item().toLowerCase(Locale.ROOT).startsWith(CUSTOM_PREFIX) && sample.getMaxStackSize() > 1;
    }

    // ---- Screens ----

    public boolean open(Player p, String shopId) {
        Shop shop = this.shops.get(shopId.toLowerCase(Locale.ROOT));
        if (shop == null) {
            return false;
        }
        ShopHolder holder = new ShopHolder(shop, null);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(shop.title()));
        holder.inventory = inv;
        fill(inv);
        inv.setItem(HEADER_SLOT, this.balanceIcon(p, shop.title()));
        List<Integer> slots = centeredSlots(shop.entries().size());
        for (int i = 0; i < shop.entries().size() && i < slots.size(); i++) {
            Entry entry = shop.entries().get(i);
            List<Component> extra = new ArrayList<>();
            extra.add(Component.empty());
            extra.add(line("Price: " + VaultEconomy.format(entry.price()), NamedTextColor.GOLD));
            extra.add(Component.empty());
            extra.add(line("Left-click to buy 1", NamedTextColor.YELLOW));
            if (this.stackable(entry)) {
                extra.add(line("Right-click to choose the amount", NamedTextColor.YELLOW));
            }
            inv.setItem(slots.get(i), withLore(this.create(entry, 1), extra));
            holder.entryButtons.put(slots.get(i), entry);
        }
        inv.setItem(BACK_SLOT, named(new ItemStack(Material.BARRIER), "Close", NamedTextColor.RED));
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        return true;
    }

    private void openQuantity(Player p, Shop shop, Entry entry) {
        ShopHolder holder = new ShopHolder(shop, entry);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(shop.title() + " - Amount"));
        holder.inventory = inv;
        fill(inv);
        inv.setItem(HEADER_SLOT, this.balanceIcon(p, shop.title()));
        for (int i = 0; i < QUANTITIES.length; i++) {
            int quantity = QUANTITIES[i];
            ItemStack icon = this.create(entry, Math.min(quantity, 64));
            List<Component> extra = List.of(Component.empty(),
                    line("Buy " + quantity + " for " + VaultEconomy.format(entry.price() * quantity), NamedTextColor.GOLD),
                    Component.empty(),
                    line("Click to buy", NamedTextColor.YELLOW));
            inv.setItem(QUANTITY_SLOTS[i], withLore(icon, extra));
            holder.quantityButtons.put(QUANTITY_SLOTS[i], quantity);
        }
        inv.setItem(BACK_SLOT, named(new ItemStack(Material.ARROW), "Back", NamedTextColor.GOLD));
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
    }

    private ItemStack balanceIcon(Player p, String title) {
        OptionalDouble balance = VaultEconomy.balance(p);
        ItemStack icon = named(new ItemStack(Material.EMERALD), title, NamedTextColor.GREEN);
        return withLore(icon, List.of(line("Your coins: " + (balance.isPresent() ? VaultEconomy.format(balance.getAsDouble()) : "-"), NamedTextColor.GOLD)));
    }

    // ---- Clicks ----

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        int slot = e.getRawSlot();
        if (holder.quantityFor == null) {
            if (slot == BACK_SLOT) {
                p.closeInventory();
                return;
            }
            Entry entry = holder.entryButtons.get(slot);
            if (entry == null) {
                return;
            }
            if (e.isRightClick()) {
                if (this.stackable(entry)) {
                    this.openQuantity(p, holder.shop, entry);
                }
            } else if (e.isLeftClick()) {
                this.buy(p, entry, 1);
                this.open(p, holder.shop.id());
            }
            return;
        }
        if (slot == BACK_SLOT) {
            this.open(p, holder.shop.id());
            return;
        }
        Integer quantity = holder.quantityButtons.get(slot);
        if (quantity != null) {
            this.buy(p, holder.quantityFor, quantity);
            this.openQuantity(p, holder.shop, holder.quantityFor);
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ShopHolder) {
            e.setCancelled(true);
        }
    }

    private void buy(Player p, Entry entry, int quantity) {
        long total = entry.price() * quantity;
        OptionalDouble balance = VaultEconomy.balance(p);
        if (balance.isEmpty()) {
            p.sendMessage(Component.text("This shop is unavailable right now (no economy installed).", NamedTextColor.RED));
            return;
        }
        if (balance.getAsDouble() < total) {
            p.sendMessage(Component.text("You need " + VaultEconomy.format(total) + " for that (you have "
                    + VaultEconomy.format(balance.getAsDouble()) + ").", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (total > 0 && !VaultEconomy.withdraw(p, total)) {
            p.sendMessage(Component.text("The purchase failed - nothing was charged.", NamedTextColor.RED));
            return;
        }
        Component name = Component.empty();
        for (int given = 0; given < quantity; ) {
            ItemStack stack = this.create(entry, quantity - given);
            int max = stack.getMaxStackSize();
            if (stack.getAmount() > max) {
                stack.setAmount(max);
            }
            given += stack.getAmount();
            name = displayName(stack);
            for (ItemStack overflow : p.getInventory().addItem(stack).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
        p.sendMessage(Component.text("Bought " + quantity + "x ", NamedTextColor.GREEN).append(name)
                .append(Component.text(" for " + VaultEconomy.format(total) + ".", NamedTextColor.GREEN)));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    // ---- Command ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            this.load();
            sender.sendMessage(Component.text("Shops reloaded: " + String.join(", ", this.shops.keySet()), NamedTextColor.GREEN));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("open")) {
            Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player self ? self : null);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            if (!this.open(target, args[1])) {
                sender.sendMessage(Component.text("Unknown shop '" + args[1] + "'. Shops: " + String.join(", ", this.shops.keySet()), NamedTextColor.RED));
            }
            return true;
        }
        sender.sendMessage(Component.text("Usage: /" + label + " open <shop> [player] | /" + label + " reload", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("open", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            return new ArrayList<>(this.shops.keySet());
        }
        return null;
    }

    // ---- Helpers ----

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.displayName() : Component.translatable(item.getType().translationKey());
    }

    private static ItemStack withLore(ItemStack item, List<Component> extra) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.addAll(extra);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack named(ItemStack item, String name, NamedTextColor color) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private static void fill(Inventory inv) {
        ItemStack pane = named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ", NamedTextColor.GRAY);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    /** Up to 28 slots, rows of 7 centered in the 4 middle rows (same layout idea as the Collections menu). */
    static List<Integer> centeredSlots(int count) {
        List<Integer> out = new ArrayList<>();
        int rows = Math.max(1, Math.min(4, (int) Math.ceil(count / 7.0)));
        int firstRow = 1 + (4 - rows) / 2;
        int placed = 0;
        for (int row = 0; row < rows && placed < count; row++) {
            int amount = Math.min(7, count - placed);
            int firstColumn = (9 - amount) / 2;
            for (int col = 0; col < amount; col++) {
                out.add((firstRow + row) * 9 + firstColumn + col);
            }
            placed += amount;
        }
        return out;
    }
}
