package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.AccessoryBagService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Cobblestone Collection's (Mining) items:
 * <ul>
 * <li><b>M3 Personal Compactor</b> (Tier C), <b>M5 Personal Compactor 3000</b> (Tier D),
 * <b>M7 Personal Compactor 4000</b> (Tier A) and <b>M9 Personal Compactor 5000</b> (Tier S) - one
 * upgrade line of accessories (only the best one in the Accessory Bag counts) with 1 / 3 / 7 / 7
 * slots. Each slot holds a block; whenever the player has enough of that block's own ingredient
 * in their inventory (9 Iron Ingots -> 1 Iron Block, 4 Snowballs -> 1 Snow Block...), they are
 * compacted into it, until fewer are left.
 * <li><b>M4 Enchanted Cobblestone</b> - an enchanted-looking Cobblestone, the upgrades' ingredient.
 * </ul>
 *
 * <p>The chosen blocks belong to the player, not the item ({@link #CONFIG_KEY} in the player's
 * PDC), so they carry over to every upgrade and the compactor can be configured from anywhere:
 * right-click while holding one, or right-click it inside the Accessory Bag. The menu shows as
 * many slots as the player's best compactor has; a block goes in by clicking a slot with it on the
 * cursor - only a ghost copy, the player keeps theirs - and clicking a filled slot clears it.
 *
 * <p>The 4000 and 5000 recipes need 2 and 4 Enchanted Cobblestone per slot, which a vanilla recipe
 * can't express (it always takes one): {@link #prepare} hides the result until every surrounding
 * slot has enough, and {@link #craft} does the craft by hand, taking the right amounts.
 */
public final class CobblestoneCollectionService implements Listener {
    public static final String COMPACTOR_FAMILY = "personal_compactor";
    private static final NamespacedKey SLOTS_KEY = new NamespacedKey("foodtooltips", "personal_compactor_slots");
    private static final NamespacedKey CONFIG_KEY = new NamespacedKey("foodtooltips", "personal_compactor_blocks");
    private static final NamespacedKey ENCHANTED_COBBLESTONE_KEY = new NamespacedKey("foodtooltips", "enchanted_cobblestone");
    private static final NamespacedKey GHOST_KEY = new NamespacedKey("foodtooltips", "personal_compactor_ghost");
    /** Enchanted Cobblestone needed in each of the 8 surrounding slots, per recipe - see this class's own doc. */
    private static final Map<NamespacedKey, Integer> RING_AMOUNTS = Map.of(
            CollectionsCatalog.PERSONAL_COMPACTOR_4000_RECIPE, 2,
            CollectionsCatalog.PERSONAL_COMPACTOR_5000_RECIPE, 4);
    private static final int MAX_SLOTS = 7;

    private final Plugin plugin;
    private final ItemTierService tiers;
    private final AccessoryBagService accessoryBag;
    /** Block -> (ingredient, how many), from the vanilla "9 of X -> 1 block" style recipes - built on first use, see {@link #compactions}. */
    private Map<Material, Compaction> compactions;

    record Compaction(Material ingredient, int amount) {
    }

    /** Marks the configuration screen. */
    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;
        private int[] slots = new int[0];

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    public CobblestoneCollectionService(Plugin plugin, ItemTierService tiers, AccessoryBagService accessoryBag) {
        this.plugin = plugin;
        this.tiers = tiers;
        this.accessoryBag = accessoryBag;
    }

    // ---- Recipes ----

    public void registerRecipes() {
        RecipeChoice enchanted = new RecipeChoice.ExactChoice(this.enchantedCobblestone());
        this.shaped(CollectionsCatalog.PERSONAL_COMPACTOR_RECIPE, this.compactor(1), 'C', new RecipeChoice.MaterialChoice(Material.COBBLESTONE),
                new RecipeChoice.MaterialChoice(Material.REDSTONE_BLOCK));
        this.shaped(CollectionsCatalog.ENCHANTED_COBBLESTONE_RECIPE, this.enchantedCobblestone(), 'C', new RecipeChoice.MaterialChoice(Material.COBBLESTONE),
                new RecipeChoice.MaterialChoice(Material.DIAMOND_BLOCK));
        this.shaped(CollectionsCatalog.PERSONAL_COMPACTOR_3000_RECIPE, this.compactor(3000), 'E', enchanted, new RecipeChoice.ExactChoice(this.compactor(1)));
        this.shaped(CollectionsCatalog.PERSONAL_COMPACTOR_4000_RECIPE, this.compactor(4000), 'E', enchanted, new RecipeChoice.ExactChoice(this.compactor(3000)));
        this.shaped(CollectionsCatalog.PERSONAL_COMPACTOR_5000_RECIPE, this.compactor(5000), 'E', enchanted, new RecipeChoice.ExactChoice(this.compactor(4000)));
    }

    /** "8 of {@code ring} around 1 {@code center}". */
    private void shaped(NamespacedKey key, ItemStack result, char ringChar, RecipeChoice ring, RecipeChoice center) {
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        String r = String.valueOf(ringChar);
        recipe.shape(r + r + r, r + "X" + r, r + r + r);
        recipe.setIngredient(ringChar, ring);
        recipe.setIngredient('X', center);
        Bukkit.addRecipe(recipe);
    }

    // ---- Items ----

    public ItemStack enchantedCobblestone() {
        ItemStack item = new ItemStack(Material.COBBLESTONE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ENCHANTED_COBBLESTONE_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Enchanted Cobblestone", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    /** The compactor of {@code model} - 1 for the plain one, else 3000/4000/5000. */
    public ItemStack compactor(int model) {
        String name = model == 1 ? "Personal Compactor" : "Personal Compactor " + model;
        ItemTier tier = switch (model) {
            case 3000 -> ItemTier.D;
            case 4000 -> ItemTier.A;
            case 5000 -> ItemTier.S;
            default -> ItemTier.C;
        };
        int slots = model == 1 ? 1 : model == 3000 ? 3 : MAX_SLOTS;
        AccessoryType type = model == 1 ? AccessoryType.TALISMAN : model == 3000 ? AccessoryType.RING : AccessoryType.ARTIFACT;
        ItemStack item = new ItemStack(Material.DISPENSER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, type, COMPACTOR_FAMILY, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0);
        meta.getPersistentDataContainer().set(SLOTS_KEY, PersistentDataType.INTEGER, slots);
        this.tiers.forceTier(meta, tier);
        meta.setEnchantmentGlintOverride(true);
        meta.lore(List.of(
                line("Compacts up to " + slots + " kind" + (slots == 1 ? "" : "s") + " of item into", NamedTextColor.AQUA),
                line("their block (9 Iron Ingots -> Iron Block).", NamedTextColor.AQUA),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                line("Right click to choose the blocks.", NamedTextColor.YELLOW),
                line("Store in the Accessory Bag.", NamedTextColor.GRAY),
                line("Only one Personal Compactor at a time.", NamedTextColor.DARK_GRAY)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    public static boolean isCompactor(ItemStack item) {
        return item != null && !item.isEmpty() && COMPACTOR_FAMILY.equals(AccessoryItems.family(item));
    }

    private static boolean isEnchantedCobblestone(ItemStack item) {
        return item != null && !item.isEmpty() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ENCHANTED_COBBLESTONE_KEY, PersistentDataType.BYTE);
    }

    private static int slotsOf(ItemStack item) {
        return item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().getOrDefault(SLOTS_KEY, PersistentDataType.INTEGER, 1) : 1;
    }

    /** Slots of {@code p}'s best compactor in the Accessory Bag, 0 without one. */
    public int equippedSlots(Player p) {
        int slots = 0;
        for (ItemStack item : this.accessoryBag.equippedAccessories(p)) {
            if (isCompactor(item)) {
                slots = Math.max(slots, slotsOf(item));
            }
        }
        return slots;
    }

    // ---- Configuration (player PDC) ----

    /** {@code p}'s chosen blocks, one per slot (null for an empty slot), always {@value #MAX_SLOTS} long. */
    List<Material> configured(Player p) {
        String raw = p.getPersistentDataContainer().getOrDefault(CONFIG_KEY, PersistentDataType.STRING, "");
        List<Material> out = new ArrayList<>();
        String[] parts = raw.split(",", -1);
        for (int i = 0; i < MAX_SLOTS; i++) {
            Material m = i < parts.length && !parts[i].isEmpty() ? Material.matchMaterial(parts[i]) : null;
            out.add(m);
        }
        return out;
    }

    private void save(Player p, List<Material> blocks) {
        List<String> parts = new ArrayList<>();
        for (Material m : blocks) {
            parts.add(m == null ? "" : m.name());
        }
        p.getPersistentDataContainer().set(CONFIG_KEY, PersistentDataType.STRING, String.join(",", parts));
    }

    /** Every vanilla block made from N (4 or 9) of one single item - Iron Block from Iron Ingots, Snow Block from Snowballs... */
    Map<Material, Compaction> compactions() {
        if (this.compactions != null) {
            return this.compactions;
        }
        Map<Material, Compaction> found = new EnumMap<>(Material.class);
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (!(recipe instanceof org.bukkit.Keyed keyed) || !NamespacedKey.MINECRAFT.equals(keyed.getKey().getNamespace())
                    || recipe.getResult().getAmount() != 1 || !recipe.getResult().getType().isBlock()) {
                continue;
            }
            List<RecipeChoice> choices = new ArrayList<>();
            if (recipe instanceof ShapedRecipe shaped) {
                for (String row : shaped.getShape()) {
                    for (char c : row.toCharArray()) {
                        choices.add(c == ' ' ? null : shaped.getChoiceMap().get(c));
                    }
                }
                int rows = shaped.getShape().length;
                if (!(rows == 3 && choices.size() == 9) && !(rows == 2 && choices.size() == 4)) {
                    continue;
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                choices.addAll(shapeless.getChoiceList());
            } else {
                continue;
            }
            Material single = null;
            boolean ok = choices.size() == 4 || choices.size() == 9;
            for (RecipeChoice choice : choices) {
                if (!(choice instanceof RecipeChoice.MaterialChoice mc) || mc.getChoices().size() != 1) {
                    ok = false;
                    break;
                }
                Material m = mc.getChoices().get(0);
                if (single == null) {
                    single = m;
                } else if (single != m) {
                    ok = false;
                    break;
                }
            }
            if (ok && single != null && single != recipe.getResult().getType()) {
                found.putIfAbsent(recipe.getResult().getType(), new Compaction(single, choices.size()));
            }
        }
        this.compactions = found;
        return found;
    }

    // ---- Menu ----

    public void openMenu(Player p, int slots) {
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 9, "Personal Compactor");
        holder.inventory = inv;
        holder.slots = menuSlots(slots);
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.WHITE);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        List<Material> blocks = this.configured(p);
        for (int i = 0; i < holder.slots.length; i++) {
            inv.setItem(holder.slots[i], this.slotIcon(blocks.get(i)));
        }
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p, holder.slots);
    }

    /** The menu's slot positions for {@code slots} slots, centered in its one row. */
    static int[] menuSlots(int slots) {
        int n = Math.max(1, Math.min(MAX_SLOTS, slots));
        int start = (9 - n) / 2;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = start + i;
        }
        return out;
    }

    private ItemStack slotIcon(Material block) {
        if (block == null) {
            ItemStack empty = named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "Empty slot", NamedTextColor.GRAY);
            ItemMeta meta = empty.getItemMeta();
            meta.lore(List.of(line("Click with a block to compact into it.", NamedTextColor.YELLOW)));
            meta.getPersistentDataContainer().set(GHOST_KEY, PersistentDataType.BYTE, (byte) 1);
            empty.setItemMeta(meta);
            return empty;
        }
        Compaction c = this.compactions().get(block);
        ItemStack icon = new ItemStack(block);
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (c != null) {
            lore.add(line(c.amount() + " " + pretty(c.ingredient()) + " -> 1 " + pretty(block), NamedTextColor.AQUA));
        }
        lore.add(line("Click to clear this slot.", NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(GHOST_KEY, PersistentDataType.BYTE, (byte) 1);
        icon.setItemMeta(meta);
        return icon;
    }

    @EventHandler
    public void menuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        e.setCancelled(true);
        int raw = e.getRawSlot();
        int index = -1;
        for (int i = 0; i < holder.slots.length; i++) {
            if (holder.slots[i] == raw) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        List<Material> blocks = this.configured(p);
        ItemStack cursor = e.getCursor();
        if (cursor == null || cursor.isEmpty()) {
            blocks.set(index, null);
        } else if (this.compactions().containsKey(cursor.getType())) {
            blocks.set(index, cursor.getType());
        } else {
            p.sendActionBar(Component.text("That block can't be compacted into.", NamedTextColor.RED));
            return;
        }
        this.save(p, blocks);
        holder.inventory.setItem(raw, this.slotIcon(blocks.get(index)));
    }

    @EventHandler
    public void menuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            e.setCancelled(true);
        }
    }

    /** Right-click while holding a compactor opens its menu (and never places it - it's a Dispenser). */
    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isCompactor(e.getItem())
                || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()
                && !e.getPlayer().isSneaking()) {
            return;
        }
        e.setCancelled(true);
        this.openMenu(e.getPlayer(), Math.max(slotsOf(e.getItem()), this.equippedSlots(e.getPlayer())));
    }

    /** Called by the Accessory Bag on a right-click of one of its items - opens the menu for a compactor there. */
    public boolean bagRightClick(Player p, ItemStack item) {
        if (!isCompactor(item)) {
            return false;
        }
        int slots = Math.max(slotsOf(item), this.equippedSlots(p));
        Bukkit.getScheduler().runTask(this.plugin, () -> this.openMenu(p, slots));
        return true;
    }

    /** Neither the compactors (Dispensers) nor Enchanted Cobblestone are meant to be placed. */
    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (isCompactor(item) || isEnchantedCobblestone(item)) {
            e.setCancelled(true);
        }
    }

    // ---- Compacting ----

    /** Compacts every online player's inventory - call from a repeating task, see {@link #start}. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, () -> Bukkit.getOnlinePlayers().forEach(this::compact), 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.compact(p));
        }
    }

    /** For each configured block (within {@code p}'s compactor's own slot count), turns every full set of its ingredient in the inventory into the block. */
    public void compact(Player p) {
        int slots = this.equippedSlots(p);
        if (slots <= 0 || !p.isOnline() || p.isDead()) {
            return;
        }
        List<Material> blocks = this.configured(p);
        for (int i = 0; i < slots && i < blocks.size(); i++) {
            Material block = blocks.get(i);
            Compaction c = block == null ? null : this.compactions().get(block);
            if (c == null) {
                continue;
            }
            ItemStack plain = new ItemStack(c.ingredient());
            int have = 0;
            for (ItemStack stack : p.getInventory().getStorageContents()) {
                if (stack != null && stack.isSimilar(plain)) {
                    have += stack.getAmount();
                }
            }
            int made = have / c.amount();
            if (made <= 0) {
                continue;
            }
            plain.setAmount(made * c.amount());
            p.getInventory().removeItem(plain);
            for (ItemStack overflow : p.getInventory().addItem(new ItemStack(block, made)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
    }

    // ---- Crafts needing 2/4 per slot ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent e) {
        Integer need = ringAmount(e.getRecipe());
        if (need != null && !hasRing(e.getInventory().getMatrix(), need)) {
            e.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        Integer need = ringAmount(e.getRecipe());
        if (need == null || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        e.setCancelled(true);
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (!hasRing(matrix, need) || inv.getResult() == null) {
            return;
        }
        ItemStack result = inv.getResult().clone();
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] == null) {
                continue;
            }
            int take = i == 4 ? 1 : need;
            int left = matrix[i].getAmount() - take;
            matrix[i] = left > 0 ? matrix[i].asQuantity(left) : null;
        }
        inv.setMatrix(matrix);
        ItemStack cursor = e.getView().getCursor();
        if (!e.isShiftClick() && (cursor == null || cursor.isEmpty())) {
            e.getView().setCursor(result);
        } else {
            for (ItemStack overflow : p.getInventory().addItem(result).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
    }

    private static Integer ringAmount(Recipe recipe) {
        return recipe instanceof org.bukkit.Keyed keyed ? RING_AMOUNTS.get(keyed.getKey()) : null;
    }

    /** Whether all 8 slots around the center of the 3x3 {@code matrix} hold at least {@code need} items. */
    static boolean hasRing(ItemStack[] matrix, int need) {
        if (matrix.length != 9) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            if (i != 4 && (matrix[i] == null || matrix[i].getAmount() < need)) {
                return false;
            }
        }
        return true;
    }

    // ---- Helpers ----

    private static String pretty(Material m) {
        String[] words = m.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
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

    private static ItemStack named(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
