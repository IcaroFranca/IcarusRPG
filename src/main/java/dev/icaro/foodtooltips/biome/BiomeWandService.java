package dev.icaro.foodtooltips.biome;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Biome's Wand: left-click (swing) opens a menu of curated grass-tinted biomes (see
 * {@link BiomeOption}) to pick from, and a radius control; right-click a block paints
 * the picked biome onto every biome cell within a square area of that radius around the
 * clicked column, spanning the world's full height - biome is a horizontal-area
 * concept, not a 3D one, same reasoning WorldEdit's {@code //biome} brush uses. Shift +
 * left-click undoes the last paint. Same admin-only-via-command, no-craft-no-drop
 * status as {@code BuilderWandService}/{@code DestroyerHandService} for now.
 *
 * <p>Biomes are stored in 4x4x4 cells, not per-block ({@link World#setBiome(int, int,
 * int, Biome)} silently applies to the whole cell containing the given coordinate
 * regardless of which sub-block you pass) - so painting and undo both step by 4 instead
 * of 1, which is both exactly the real granularity and ~64x fewer calls than a naive
 * per-block loop.
 */
public final class BiomeWandService {
    /** Biome storage is aligned to 4-block cells - see this class's doc. */
    private static final int CELL = 4;

    private final NamespacedKey wandKey;
    private final NamespacedKey biomeKey;
    private final NamespacedKey radiusKey;
    private final int maxRadius;
    private final ItemTierService tiers;
    private final Map<UUID, List<Snapshot>> lastAction = new HashMap<>();
    private final Set<UUID> viewingMenu = new HashSet<>();

    /** One previously-different biome cell, captured before painting, for {@link #undo}. */
    private record Snapshot(World world, int x, int y, int z, Biome previous) {
    }

    /** One slot per {@link BiomeOption}, by declaration order - two 7-wide rows plus one, avoiding the inventory's left/right border columns. */
    private static final int[] BIOME_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28};
    private static final int RADIUS_SLOT = 40;

    public BiomeWandService(Plugin plugin, ItemTierService tiers) {
        this.wandKey = new NamespacedKey(plugin, "biome_wand");
        this.biomeKey = new NamespacedKey(plugin, "biome_wand_biome");
        this.radiusKey = new NamespacedKey(plugin, "biome_wand_radius");
        this.maxRadius = Math.max(0, plugin.getConfig().getInt("biome-wand.max-radius", 10));
        this.tiers = tiers;
    }

    // ---- Creation -------------------------------------------------------

    public ItemStack create(Language l) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.wandKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(this.biomeKey, PersistentDataType.STRING, BiomeOption.defaultOption().name());
        meta.getPersistentDataContainer().set(this.radiusKey, PersistentDataType.INTEGER, 0);
        // A one-of-a-kind admin tool, not a plain Stick - pin it to Tier S so
        // ItemTierService's periodic pass doesn't sort it into Tier E junk with every
        // other Stick (same reasoning as BuilderWandService's Blaze Rod).
        this.tiers.forceTier(meta, ItemTier.S);
        item.setItemMeta(meta);
        this.refreshLore(item, l);
        return item;
    }

    /** Rewrites the wand's name/lore to reflect its currently selected biome and radius - called on creation and whenever either changes. */
    private void refreshLore(ItemStack item, Language l) {
        ItemMeta meta = item.getItemMeta();
        BiomeOption selected = this.selectedBiome(item);
        int radius = this.radius(item);
        meta.displayName(this.line(l.choose("Varinha de Biomas", "Biome's Wand"), NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(l.choose("Clique esquerdo abre o menu de biomas.", "Left-click opens the biome menu."), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Clique direito num bloco pinta o bioma.", "Right-click a block to paint the biome."), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Shift + clique esquerdo desfaz a última pintura.", "Shift + left-click undoes the last paint."), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(this.line(l.choose("Bioma: ", "Biome: ") + selected.displayName(l == Language.PT), NamedTextColor.YELLOW));
        lore.add(this.line(l.choose("Raio: ", "Radius: ") + this.radiusLabel(radius, l), NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
    }

    private String radiusLabel(int radius, Language l) {
        return radius <= 0 ? l.choose("só o bloco", "just the block") : radius + l.choose(" blocos", " blocks");
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.wandKey, PersistentDataType.BYTE);
    }

    public BiomeOption selectedBiome(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String raw = meta == null ? null : meta.getPersistentDataContainer().get(this.biomeKey, PersistentDataType.STRING);
        if (raw == null) {
            return BiomeOption.defaultOption();
        }
        try {
            return BiomeOption.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return BiomeOption.defaultOption();
        }
    }

    public int radius(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Integer stored = meta == null ? null : meta.getPersistentDataContainer().get(this.radiusKey, PersistentDataType.INTEGER);
        return stored == null ? 0 : Math.max(0, Math.min(this.maxRadius, stored));
    }

    public void forget(UUID playerId) {
        this.lastAction.remove(playerId);
        this.viewingMenu.remove(playerId);
    }

    // ---- Menu -------------------------------------------------------------

    public void openMenu(Player p, ItemStack item) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Varinha de Biomas", "Biome's Wand"));
        this.renderMenu(v, item, l);
        p.openInventory(v);
        this.viewingMenu.add(p.getUniqueId());
    }

    private void renderMenu(Inventory v, ItemStack item, Language l) {
        ItemStack filler = this.filler();
        for (int i = 0; i < 54; i++) {
            v.setItem(i, filler);
        }
        BiomeOption selected = this.selectedBiome(item);
        BiomeOption[] options = BiomeOption.values();
        for (int i = 0; i < options.length && i < BIOME_SLOTS.length; i++) {
            v.setItem(BIOME_SLOTS[i], this.biomeOption(options[i], options[i] == selected, l));
        }
        v.setItem(RADIUS_SLOT, this.radiusItem(this.radius(item), l));
    }

    public boolean viewingMenu(Player p) {
        return this.viewingMenu.contains(p.getUniqueId());
    }

    public void closeMenu(Player p) {
        this.viewingMenu.remove(p.getUniqueId());
    }

    /**
     * Handles a click inside the biome menu - applies the picked biome or radius step to
     * whatever wand {@code p} is currently holding, then re-renders the menu in place
     * (never auto-closes, matching {@code BuilderWandService}'s settings menu).
     */
    public void handleMenuClick(Player p, int slot, ClickType click) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.isWand(held)) {
            return;
        }
        Language l = Language.of(p);
        BiomeOption picked = this.optionAtSlot(slot);
        if (picked != null) {
            this.setBiomeOption(held, picked, l);
        } else if (slot == RADIUS_SLOT) {
            this.cycleRadius(held, click.isLeftClick(), l);
        } else {
            return;
        }
        this.renderMenu(p.getOpenInventory().getTopInventory(), held, l);
    }

    /** Which {@link BiomeOption} (if any) sits at {@code slot} in {@link #BIOME_SLOTS} - mirrors {@link #renderMenu}'s placement exactly. */
    private BiomeOption optionAtSlot(int slot) {
        BiomeOption[] options = BiomeOption.values();
        for (int i = 0; i < BIOME_SLOTS.length && i < options.length; i++) {
            if (BIOME_SLOTS[i] == slot) {
                return options[i];
            }
        }
        return null;
    }

    private void setBiomeOption(ItemStack item, BiomeOption option, Language l) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.biomeKey, PersistentDataType.STRING, option.name());
        item.setItemMeta(meta);
        this.refreshLore(item, l);
    }

    private void cycleRadius(ItemStack item, boolean forward, Language l) {
        int current = this.radius(item);
        int next = forward ? Math.min(this.maxRadius, current + 1) : Math.max(0, current - 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.radiusKey, PersistentDataType.INTEGER, next);
        item.setItemMeta(meta);
        this.refreshLore(item, l);
    }

    private ItemStack biomeOption(BiomeOption option, boolean selected, Language l) {
        boolean pt = l == Language.PT;
        ItemStack item = new ItemStack(option.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.line((selected ? "✔ " : "") + option.displayName(pt), selected ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, selected));
        List<Component> lore = new ArrayList<>();
        if (selected) {
            lore.add(this.line(l.choose("Bioma selecionado", "Selected biome"), NamedTextColor.GREEN));
        } else {
            lore.add(this.line(l.choose("Clique para selecionar", "Click to select"), NamedTextColor.GRAY));
        }
        meta.lore(lore);
        if (selected) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack radiusItem(int radius, Language l) {
        ItemStack item = new ItemStack(radius <= 0 ? Material.TARGET : Material.LIGHT_BLUE_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.line(l.choose("Raio: ", "Radius: ") + this.radiusLabel(radius, l), NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(l.choose("Clique esquerdo: aumenta", "Left-click: increase"), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Clique direito: diminui", "Right-click: decrease"), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(this.line(l.choose("0 = só o bloco clicado", "0 = just the clicked block"), NamedTextColor.DARK_GRAY));
        lore.add(this.line(l.choose("Máximo: " + this.maxRadius, "Max: " + this.maxRadius), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack f = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = f.getItemMeta();
        m.displayName(Component.text(" "));
        f.setItemMeta(m);
        return f;
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    // ---- Painting -----------------------------------------------------------

    /**
     * Paints {@code item}'s selected biome onto every biome cell within its radius of
     * {@code clicked}'s column, across the world's full height - a horizontal square
     * area (not a 3D sphere, since biome is fundamentally a horizontal-area concept).
     * Square rather than circular on purpose: biome cells are 4-wide, so a "radius" in
     * plain blocks never lines up evenly with a circle's edge on that grid anyway - a
     * square keeps the geometry (and the code) exact instead of approximate. Returns how
     * many cells actually changed (cells already showing the target biome are skipped,
     * not re-counted).
     */
    public int paint(Player p, Block clicked, ItemStack item) {
        World world = clicked.getWorld();
        Biome target = this.selectedBiome(item).biome();
        int radius = this.radius(item);
        // Biome cells sit on a fixed grid anchored at the world's absolute (0,0,minY),
        // not at the clicked block, so painting has to work in whole cells anchored to
        // that same grid - snapping to the clicked block's own cell first (rather than
        // its raw coordinate) guarantees that cell is always included, and stepping out
        // by whole cells in each direction covers every other cell exactly once with no
        // gaps. Radius (in blocks) rounds UP to the nearest whole cell so every
        // requested block ends up covered rather than falling short at the edge for a
        // radius that isn't itself a multiple of 4.
        int cellSteps = (radius + CELL - 1) / CELL;
        int cellX0 = Math.floorDiv(clicked.getX(), CELL) * CELL;
        int cellZ0 = Math.floorDiv(clicked.getZ(), CELL) * CELL;
        int minX = cellX0 - cellSteps * CELL;
        int maxX = cellX0 + cellSteps * CELL;
        int minZ = cellZ0 - cellSteps * CELL;
        int maxZ = cellZ0 + cellSteps * CELL;
        int minY = Math.floorDiv(world.getMinHeight(), CELL) * CELL;
        int maxY = world.getMaxHeight() - 1;
        List<Snapshot> painted = new ArrayList<>();
        for (int x = minX; x <= maxX; x += CELL) {
            for (int z = minZ; z <= maxZ; z += CELL) {
                for (int y = minY; y <= maxY; y += CELL) {
                    Biome current = world.getBiome(x, y, z);
                    if (current == target) {
                        continue;
                    }
                    painted.add(new Snapshot(world, x, y, z, current));
                    world.setBiome(x, y, z, target);
                }
            }
        }
        if (!painted.isEmpty()) {
            this.lastAction.put(p.getUniqueId(), painted);
            this.refreshChunks(painted);
        }
        return painted.size();
    }

    /**
     * Shift + left-click's counterpart to {@link #paint}: restores every cell the last
     * paint changed back to whatever biome it had before. Only remembers one action per
     * player, matching {@code BuilderWandService#undo}'s "undo the last thing I did"
     * rather than a full undo stack.
     */
    public int undo(Player p) {
        List<Snapshot> action = this.lastAction.remove(p.getUniqueId());
        if (action == null) {
            return 0;
        }
        for (Snapshot s : action) {
            s.world().setBiome(s.x(), s.y(), s.z(), s.previous());
        }
        this.refreshChunks(action);
        return action.size();
    }

    /** Resends every distinct chunk the given cells touch, once each, so the color change is visible immediately instead of only after a relog. */
    private void refreshChunks(List<Snapshot> cells) {
        Set<Long> seen = new HashSet<>();
        for (Snapshot s : cells) {
            int chunkX = s.x() >> 4;
            int chunkZ = s.z() >> 4;
            long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
            if (seen.add(key)) {
                s.world().refreshChunk(chunkX, chunkZ);
            }
        }
    }
}
