package dev.icaro.foodtooltips.spawner;

import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import dev.icaro.foodtooltips.combat.MobDifficultyService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.spawner.SpawnRule;
import org.bukkit.block.spawner.SpawnerEntry;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Apotheosis-style spawner upgrades. Right-clicking a spawner (empty hand or any item but a
 * spawn egg, which keeps vanilla's mob swap) opens a 6-row menu with one slot per {@link
 * SpawnerModifier}: items put there are stored in the spawner itself ({@link SpawnerData} in the
 * block's PDC) and can be taken back out, which undoes their effect. The menu is only a view -
 * every click is handled by hand against the stored counts, never by moving real items through
 * its slots, so nothing can be duplicated or lost.
 *
 * <p>Stat modifiers are written straight onto the vanilla spawner ({@link #apply}), always
 * recomputed from {@link SpawnerData#base} so taking items out restores the original numbers.
 * The rest acts when it spawns: redstone control ({@link #gate}), No AI/silent/less health
 * ({@link #spawned}). Breaking it with Spawner Touch carries everything inside the item
 * ({@link #carryInto}/{@link #place}); any other way of breaking it drops the stored items.
 */
public final class SpawnerUpgradeService implements Listener {
    private static final NamespacedKey DATA_KEY = new NamespacedKey("foodtooltips", "spawner_upgrades");
    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int[] MODIFIER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24};
    private static final Map<SpawnerModifier.Toggle, Integer> TOGGLE_SLOTS = Map.of(
            SpawnerModifier.Toggle.REDSTONE, 38, SpawnerModifier.Toggle.NO_AI, 40, SpawnerModifier.Toggle.SILENT, 42);
    private static final int CLOSE_SLOT = 49;
    /** Neither delay goes below this (2 seconds), whatever Sugar/Clock do - a default spawner (200-800) reaches it with 32 Sugar and 32 Clocks. */
    static final int MIN_DELAY_FLOOR = 40;
    /** Health each Pointed Dripstone takes off a mob as it spawns (Max Health stays) - 20 (the limit) leave it at 20%. */
    static final int DRIPSTONE_PERCENT = 4;

    private final Plugin plugin;
    private final MobDifficultyService difficulty;
    /** Spawners whose data {@link #carryInto} just moved into a Spawner Touch item - {@link #broken} mustn't drop their items too. */
    private final Set<Location> carried = new HashSet<>();

    /** Marks the menu, and which spawner it shows. */
    static final class Menu implements InventoryHolder {
        private final Block block;
        private Inventory inventory;

        Menu(Block block) {
            this.block = block;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    public SpawnerUpgradeService(Plugin plugin, MobDifficultyService difficulty) {
        this.plugin = plugin;
        this.difficulty = difficulty;
    }

    // ---- Storage ----

    static SpawnerData read(PersistentDataContainer pdc) {
        return SpawnerData.decode(pdc.get(DATA_KEY, PersistentDataType.STRING));
    }

    private static SpawnerData read(Block block) {
        return block.getState(false) instanceof CreatureSpawner s ? read(s.getPersistentDataContainer()) : new SpawnerData();
    }

    /** Saves {@code data} into the spawner and rewrites its stats from it. */
    private void save(Block block, SpawnerData data) {
        if (!(block.getState() instanceof CreatureSpawner state)) {
            return;
        }
        if (data.base == null) {
            data.base = new int[]{state.getMinSpawnDelay(), state.getMaxSpawnDelay(), state.getSpawnCount(),
                    state.getMaxNearbyEntities(), state.getRequiredPlayerRange(), state.getSpawnRange()};
        }
        apply(state, data);
        state.getPersistentDataContainer().set(DATA_KEY, PersistentDataType.STRING, data.encode());
        state.update();
    }

    /** The spawner's stats with {@code data}'s modifiers on top of its {@link SpawnerData#base}. */
    static void apply(CreatureSpawner state, SpawnerData data) {
        int[] b = data.base;
        int min = Math.max(MIN_DELAY_FLOOR, b[0] - 5 * data.count(SpawnerModifier.SUGAR));
        int max = Math.max(min, Math.max(MIN_DELAY_FLOOR, b[1] - 24 * data.count(SpawnerModifier.CLOCK)));
        // Bukkit refuses a min above the current max (and vice versa), so order the two writes.
        if (min > state.getMaxSpawnDelay()) {
            state.setMaxSpawnDelay(max);
            state.setMinSpawnDelay(min);
        } else {
            state.setMinSpawnDelay(min);
            state.setMaxSpawnDelay(max);
        }
        state.setSpawnCount(b[2] + data.count(SpawnerModifier.FERMENTED_SPIDER_EYE));
        state.setMaxNearbyEntities(b[3] + 2 * data.count(SpawnerModifier.GHAST_TEAR));
        // A required range of 0 or less keeps the spawner always active (as long as its chunk is loaded).
        state.setRequiredPlayerRange(data.count(SpawnerModifier.NETHER_STAR) > 0 ? -1 : b[4] + 4 * data.count(SpawnerModifier.PRISMARINE_CRYSTALS));
        state.setSpawnRange(b[5] + data.count(SpawnerModifier.BLAZE_ROD));
        EntitySnapshot snapshot = state.getSpawnedType() == null ? null : state.getSpawnedEntity();
        if (snapshot != null) {
            // Custom spawn rules replace the mob's own placement checks (light, ground block)
            // entirely - only "is there room" is still checked.
            if (data.count(SpawnerModifier.GLOWSTONE) > 0) {
                state.setSpawnedEntity(new SpawnerEntry(snapshot, 1, new SpawnRule(0, 15, 0, 15)));
            } else {
                state.setSpawnedEntity(snapshot);
            }
        }
    }

    // ---- Opening ----

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || block.getType() != Material.SPAWNER
                || e.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item != null && item.getType().name().endsWith("_SPAWN_EGG")) {
            // Vanilla's own mob swap - Glowstone's spawn rules are re-applied right after it, see #eggUsed.
            return;
        }
        if (p.isSneaking() && item != null && item.getType().isBlock()) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) {
            this.open(p, block);
        }
    }

    /** A spawn egg swap rebuilds the spawner's spawn data from scratch, dropping Glowstone's rules - put them back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void eggUsed(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && block != null && block.getType() == Material.SPAWNER
                && e.getItem() != null && e.getItem().getType().name().endsWith("_SPAWN_EGG")) {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                SpawnerData data = read(block);
                if (data.hasModifiers()) {
                    this.save(block, data);
                }
                this.refresh(block);
            });
        }
    }

    public void open(Player p, Block block) {
        Menu menu = new Menu(block);
        menu.inventory = Bukkit.createInventory(menu, SIZE, "Spawner Upgrades");
        this.render(menu);
        p.openInventory(menu.inventory);
        int[] persistent = new int[MODIFIER_SLOTS.length + TOGGLE_SLOTS.size() + 2];
        int i = 0;
        for (int slot : MODIFIER_SLOTS) {
            persistent[i++] = slot;
        }
        for (int slot : TOGGLE_SLOTS.values()) {
            persistent[i++] = slot;
        }
        persistent[i++] = INFO_SLOT;
        persistent[i] = CLOSE_SLOT;
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p, persistent);
    }

    // ---- Rendering ----

    private void render(Menu menu) {
        Inventory inv = menu.inventory;
        SpawnerData data = read(menu.block);
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.WHITE);
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(INFO_SLOT, this.infoIcon(menu.block, data));
        SpawnerModifier[] mods = SpawnerModifier.values();
        for (int i = 0; i < mods.length; i++) {
            inv.setItem(MODIFIER_SLOTS[i], modifierIcon(mods[i], data));
        }
        for (Map.Entry<SpawnerModifier.Toggle, Integer> t : TOGGLE_SLOTS.entrySet()) {
            inv.setItem(t.getValue(), toggleIcon(t.getKey(), data));
        }
        ItemStack close = named(Material.BARRIER, "Close", NamedTextColor.RED);
        inv.setItem(CLOSE_SLOT, close);
    }

    /** Re-renders every open menu showing {@code block}. */
    private void refresh(Block block) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof Menu m && m.block.equals(block)) {
                if (block.getType() != Material.SPAWNER) {
                    viewer.closeInventory();
                } else {
                    this.render(m);
                }
            }
        }
    }

    private ItemStack infoIcon(Block block, SpawnerData data) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        String mob = "Empty";
        if (block.getState(false) instanceof CreatureSpawner s) {
            mob = s.getSpawnedType() == null ? "Empty" : mobName(s.getSpawnedType());
            lore.add(line("Spawn delay: " + s.getMinSpawnDelay() + " - " + s.getMaxSpawnDelay() + " ticks", NamedTextColor.GRAY));
            lore.add(line("Mobs per spawn: " + s.getSpawnCount(), NamedTextColor.GRAY));
            lore.add(line("Max nearby mobs: " + s.getMaxNearbyEntities(), NamedTextColor.GRAY));
            lore.add(line("Player range: " + (s.getRequiredPlayerRange() <= 0 ? "any distance" : s.getRequiredPlayerRange() + " blocks"), NamedTextColor.GRAY));
            lore.add(line("Spawn area: " + s.getSpawnRange() + " blocks", NamedTextColor.GRAY));
            lore.add(line("Mobs spawn at: " + (100 - DRIPSTONE_PERCENT * data.count(SpawnerModifier.POINTED_DRIPSTONE)) + "% health", NamedTextColor.GRAY));
            lore.add(line("Ignores spawn conditions: " + (data.count(SpawnerModifier.GLOWSTONE) > 0 ? "yes" : "no"), NamedTextColor.GRAY));
            lore.add(Component.empty());
            for (SpawnerModifier.Toggle t : SpawnerModifier.Toggle.values()) {
                lore.add(line(t.label + ": " + toggleState(t, data), data.active(t) ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
            }
            lore.add(Component.empty());
            String paused = pausedReason(block, s, data);
            lore.add(paused == null ? line("Status: spawning", NamedTextColor.GREEN) : line("Status: paused - " + paused, NamedTextColor.RED));
        }
        meta.displayName(Component.text("Spawner: " + mob, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Why the spawner isn't spawning right now, or null when nothing obvious stops it - the
     * menu shows it so a stopped spawner explains itself (an unpowered redstone-controlled one
     * looked just like a broken one). Same checks vanilla runs: a player in range, then the
     * nearby-mob cap counted over the same box vanilla counts.
     */
    static String pausedReason(Block block, CreatureSpawner s, SpawnerData data) {
        if (s.getSpawnedType() == null) {
            return "no mob set (use a spawn egg)";
        }
        if (data.active(SpawnerModifier.Toggle.REDSTONE) && !block.isBlockIndirectlyPowered()) {
            return "needs a redstone signal";
        }
        int range = s.getRequiredPlayerRange();
        if (range > 0) {
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            boolean near = false;
            for (Player p : block.getWorld().getPlayers()) {
                if (!p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && p.getLocation().distanceSquared(center) <= (double) range * range) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                return "no player within " + range + " blocks";
            }
        }
        int r = s.getSpawnRange();
        org.bukkit.util.BoundingBox box = org.bukkit.util.BoundingBox.of(block).expand(r);
        long nearby = block.getWorld().getNearbyEntities(box, en -> en.getType() == s.getSpawnedType()).size();
        if (nearby >= s.getMaxNearbyEntities()) {
            return "too many mobs nearby (" + nearby + "/" + s.getMaxNearbyEntities() + ")";
        }
        return null;
    }

    private static String toggleState(SpawnerModifier.Toggle t, SpawnerData data) {
        if (data.count(t.unlockedBy()) <= 0) {
            return "locked";
        }
        return data.active(t) ? "ON" : "OFF";
    }

    /** The real item (stack size = how many are in), glinting while applied; a single dark-named one while the slot is empty. */
    static ItemStack modifierIcon(SpawnerModifier m, SpawnerData data) {
        int n = data.count(m);
        ItemStack item = new ItemStack(m == SpawnerModifier.WOOL ? data.wool : m.material, Math.max(1, n));
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(line(m.effect, NamedTextColor.AQUA));
        if (m == SpawnerModifier.NETHER_STAR) {
            lore.add(line("(only while its chunk is loaded)", NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());
        if (n > 0) {
            meta.displayName(Component.text(m.label + " (" + n + "/" + m.max + ")", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.setEnchantmentGlintOverride(true);
            if (n < m.max) {
                lore.add(line("Click with " + m.label + " to add more", NamedTextColor.YELLOW));
            }
            lore.add(line("Left-click: take all  Right-click: take one", NamedTextColor.YELLOW));
        } else {
            meta.displayName(Component.text(m.label + " (empty)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(line("Click with " + m.label + " to add (up to " + m.max + ")", NamedTextColor.YELLOW));
            lore.add(line("Shift-click it in your inventory works too", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack toggleIcon(SpawnerModifier.Toggle t, SpawnerData data) {
        boolean unlocked = data.count(t.unlockedBy()) > 0;
        boolean on = data.active(t);
        Material type = !unlocked ? Material.GRAY_DYE : on ? Material.LIME_DYE : Material.RED_DYE;
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(t.label + ": " + toggleState(t, data), !unlocked ? NamedTextColor.GRAY : on ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(line(t.description, NamedTextColor.AQUA));
        lore.add(Component.empty());
        if (t == SpawnerModifier.Toggle.REDSTONE && unlocked && !on) {
            lore.add(line("While on, the spawner stops without a signal", NamedTextColor.GRAY));
        }
        lore.add(unlocked ? line("Click to turn " + (on ? "off" : "on"), NamedTextColor.YELLOW)
                : line("Put a " + t.unlockedBy().label + " in the spawner to unlock", NamedTextColor.YELLOW));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ---- Clicks ----

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        if (menu.block.getType() != Material.SPAWNER) {
            e.setCancelled(true);
            p.closeInventory();
            return;
        }
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= SIZE) {
            if (e.getClick() == ClickType.DOUBLE_CLICK) {
                // Collecting to the cursor would pull the display stacks out of the menu.
                e.setCancelled(true);
            } else if (e.isShiftClick()) {
                e.setCancelled(true);
                this.shiftIn(p, menu, e.getCurrentItem());
            }
            return;
        }
        e.setCancelled(true);
        ClickType type = e.getClick();
        if (type != ClickType.LEFT && type != ClickType.RIGHT && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) {
            // Number keys, Q, middle click... would each pull stored items out in their own odd way.
            return;
        }
        if (raw == CLOSE_SLOT) {
            p.closeInventory();
            return;
        }
        for (Map.Entry<SpawnerModifier.Toggle, Integer> t : TOGGLE_SLOTS.entrySet()) {
            if (t.getValue() == raw) {
                this.flip(p, menu, t.getKey());
                return;
            }
        }
        for (int i = 0; i < MODIFIER_SLOTS.length; i++) {
            if (MODIFIER_SLOTS[i] == raw) {
                this.modifierClick(p, menu, SpawnerModifier.values()[i], e);
                return;
            }
        }
    }

    private void modifierClick(Player p, Menu menu, SpawnerModifier m, InventoryClickEvent e) {
        SpawnerData data = read(menu.block);
        int n = data.count(m);
        ItemStack cursor = e.getView().getCursor();
        if (cursor != null && !cursor.isEmpty()) {
            if (!m.accepts(cursor)) {
                p.sendActionBar(Component.text("Only " + m.label + " goes in this slot.", NamedTextColor.RED));
                return;
            }
            if (m == SpawnerModifier.WOOL && n > 0 && cursor.getType() != data.wool) {
                p.sendActionBar(Component.text("This spawner already has Wool.", NamedTextColor.RED));
                return;
            }
            int add = Math.min(e.isRightClick() ? 1 : cursor.getAmount(), m.max - n);
            if (add <= 0) {
                p.sendActionBar(Component.text(m.label + " is already at its limit (" + m.max + ").", NamedTextColor.RED));
                return;
            }
            if (m == SpawnerModifier.WOOL) {
                data.wool = cursor.getType();
            }
            data.set(m, n + add);
            e.getView().setCursor(cursor.getAmount() > add ? cursor.asQuantity(cursor.getAmount() - add) : null);
        } else {
            if (n <= 0) {
                return;
            }
            int take = e.isRightClick() ? 1 : n;
            ItemStack out = new ItemStack(m == SpawnerModifier.WOOL ? data.wool : m.material, take);
            data.set(m, n - take);
            if (e.isShiftClick()) {
                for (ItemStack overflow : p.getInventory().addItem(out).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), overflow);
                }
            } else {
                e.getView().setCursor(out);
            }
        }
        this.save(menu.block, data);
        this.refresh(menu.block);
    }

    /** Shift-click in the player's own inventory: sends as much of that stack as fits into its modifier's slot. */
    private void shiftIn(Player p, Menu menu, ItemStack clicked) {
        SpawnerModifier m = SpawnerModifier.of(clicked);
        if (m == null) {
            return;
        }
        SpawnerData data = read(menu.block);
        int n = data.count(m);
        if (m == SpawnerModifier.WOOL && n > 0 && clicked.getType() != data.wool) {
            p.sendActionBar(Component.text("This spawner already has Wool.", NamedTextColor.RED));
            return;
        }
        int add = Math.min(clicked.getAmount(), m.max - n);
        if (add <= 0) {
            p.sendActionBar(Component.text(m.label + " is already at its limit (" + m.max + ").", NamedTextColor.RED));
            return;
        }
        if (m == SpawnerModifier.WOOL) {
            data.wool = clicked.getType();
        }
        data.set(m, n + add);
        clicked.setAmount(clicked.getAmount() - add);
        this.save(menu.block, data);
        this.refresh(menu.block);
    }

    private void flip(Player p, Menu menu, SpawnerModifier.Toggle t) {
        SpawnerData data = read(menu.block);
        if (data.count(t.unlockedBy()) <= 0) {
            p.sendActionBar(Component.text("Put a " + t.unlockedBy().label + " in the spawner first.", NamedTextColor.RED));
            return;
        }
        if (!data.flipped.remove(t)) {
            data.flipped.add(t);
        }
        this.save(menu.block, data);
        this.refresh(menu.block);
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu && e.getRawSlots().stream().anyMatch(s -> s < SIZE)) {
            e.setCancelled(true);
        }
    }

    // ---- Spawning ----

    /** Redstone control: a spawner with it on skips its whole spawn cycle (resetting the delay, like a real spawn) while unpowered. */
    @EventHandler(ignoreCancelled = true)
    public void gate(PreSpawnerSpawnEvent e) {
        Block block = e.getSpawnerLocation().getBlock();
        SpawnerData data = read(block);
        if (data.active(SpawnerModifier.Toggle.REDSTONE) && !block.isBlockIndirectlyPowered()) {
            e.setCancelled(true);
            e.setShouldAbortSpawn(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawned(SpawnerSpawnEvent e) {
        if (e.getSpawner() == null) {
            return;
        }
        SpawnerData data = read(e.getSpawner().getPersistentDataContainer());
        if (!data.hasModifiers()) {
            return;
        }
        if (e.getEntity() instanceof Mob mob && data.active(SpawnerModifier.Toggle.NO_AI)) {
            // Not setAI(false): vanilla's NoAI also turns gravity off, so mobs Glowstone lets spawn
            // in mid-air just floated there. Unaware mobs never move, target or attack, but still
            // fall and get pushed by water - what a kill chamber needs.
            mob.setAware(false);
        }
        if (data.active(SpawnerModifier.Toggle.SILENT)) {
            e.getEntity().setSilent(true);
        }
        int dripstone = data.count(SpawnerModifier.POINTED_DRIPSTONE);
        if (dripstone > 0 && e.getEntity() instanceof LivingEntity living) {
            // Next tick: the difficulty scaling runs on the CreatureSpawnEvent that follows this one.
            double factor = 1.0 - DRIPSTONE_PERCENT / 100.0 * dripstone;
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (living.isValid()) {
                    this.difficulty.startDamaged(living, factor);
                }
            });
        }
    }

    // ---- Breaking / placing ----

    /**
     * Called by Spawner Touch right before the spawner becomes an item: moves this spawner's
     * upgrades into it (with a lore summary) so {@link #place} can restore them, and tells
     * {@link #broken} not to drop the stored items.
     */
    public void carryInto(CreatureSpawner state, ItemStack item) {
        SpawnerData data = read(state.getPersistentDataContainer());
        if (!data.hasModifiers()) {
            return;
        }
        this.carried.add(state.getLocation());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(DATA_KEY, PersistentDataType.STRING, data.encode());
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(line("Upgrades:", NamedTextColor.GRAY));
        for (SpawnerModifier m : SpawnerModifier.values()) {
            if (data.count(m) > 0) {
                lore.add(line(" " + m.label + " x" + data.count(m), NamedTextColor.AQUA));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }
        if (!this.carried.remove(block.getLocation())) {
            this.dropStored(block);
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> this.refresh(block));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(EntityExplodeEvent e) {
        this.explode(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(BlockExplodeEvent e) {
        this.explode(e.blockList());
    }

    private void explode(List<Block> blocks) {
        for (Block block : blocks) {
            if (block.getType() == Material.SPAWNER) {
                this.dropStored(block);
                Bukkit.getScheduler().runTask(this.plugin, () -> this.refresh(block));
            }
        }
    }

    private void dropStored(Block block) {
        SpawnerData data = read(block);
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack stored : data.items()) {
            block.getWorld().dropItemNaturally(at, stored);
        }
    }

    /** A spawner item carrying upgrades ({@link #carryInto}) restores them - after Spawner Touch's own NORMAL handler has set its mob. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (e.getBlock().getType() != Material.SPAWNER || !e.getItemInHand().hasItemMeta()) {
            return;
        }
        String raw = e.getItemInHand().getItemMeta().getPersistentDataContainer().get(DATA_KEY, PersistentDataType.STRING);
        if (raw != null) {
            this.save(e.getBlock(), SpawnerData.decode(raw));
        }
    }

    // ---- Helpers ----

    private static String mobName(EntityType type) {
        String v = type.key().value().replace('_', ' ');
        return Character.toUpperCase(v.charAt(0)) + v.substring(1).toLowerCase(Locale.ROOT);
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
