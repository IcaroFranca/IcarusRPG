package dev.icaro.foodtooltips.builder;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.prisma.PrismaPumpService;
import dev.icaro.foodtooltips.util.LoreWrap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
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
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

/**
 * The Builder's Wand: right-click a placed block to extend it into a full line
 * (horizontal face), column (top/bottom face), or - in {@link FillMode#FACE} - the
 * whole connected wall/floor area beyond it, starting from the block adjacent to the
 * one clicked. In Creative it's free (matches Creative's own "unlimited blocks" rule);
 * in Survival it consumes one matching block from the player's inventory per block
 * placed and stops the moment it runs out.
 *
 * <p>A third mode, {@link FillMode#COPY}, turns the wand into a copy/paste tool instead:
 * right-click marks corner A, shift + right-click marks corner B and copies the box
 * between them, then a plain right-click drops a live ghost preview of the copy that
 * follows the player's crosshair (see {@link #tickPreview}) until another right-click
 * confirms the paste - see {@link #handleCopyClick} for the full state machine.
 *
 * <p>For now the wand is admin-only and only reachable via a command (see
 * {@code FoodTooltipsPlugin}'s {@code /builderwand} executor) - no drop/craft source yet.
 */
public final class BuilderWandService {
    /**
     * Whether a right-click extends only along the clicked face's line/column, floods the
     * whole connected area on that face, {@link FillMode#COPY} copies an existing area to
     * paste elsewhere with a live preview (see {@link #handleCopyClick}), or {@link
     * FillMode#WATER} levels a connected pocket of air into water next to the clicked block
     * (see {@link #pumpWater} - the same {@link PrismaPumpService} logic backing the
     * Prismapump item itself). Stored per-item (see {@link #modeKey}), not per-player - the
     * wand itself remembers its own setting.
     */
    public enum FillMode {
        LINE, FACE, COPY, WATER
    }

    /** One placement (extend or paste): the exact blocks it placed, and how much of each material it consumed (empty in Creative) so undo knows what to refund. */
    private record LastAction(List<Block> placed, Map<Material, Integer> consumed) {
    }

    /**
     * How many past actions {@link #undo} can walk back through per player, not just the
     * very last one - each undo pops one step further back, like a normal editor's undo
     * stack. Bounded (not unlimited) because every entry pins a full {@link List} of {@link
     * Block} references in memory for as long as it's kept; 50 is generous - many minutes of
     * continuous building - without letting an hours-long session's history grow forever.
     */
    private static final int MAX_UNDO_HISTORY = 50;

    /** One block from a {@link CopySession#buffer}: its offset from the buffer's own minimum corner, and its captured {@link BlockData}. Air blocks are never captured (see {@link #captureBuffer}), so a paste never punches holes in whatever it lands on beyond the copied shape itself. */
    private record CopiedBlock(int dx, int dy, int dz, BlockData data) {
    }

    /**
     * One player's in-progress {@link FillMode#COPY} state: the first corner picked (before
     * the second completes the selection), the captured buffer once copied (kept across
     * pastes, like a clipboard), and - while a live preview is up - the ghost {@link
     * BlockDisplay} entities (index-aligned with {@code buffer}) and the last block position
     * they were moved to.
     */
    private static final class CopySession {
        private Block pos1;
        private List<CopiedBlock> buffer;
        private final List<BlockDisplay> previewEntities = new ArrayList<>();
        private Block previewAnchor;

        private boolean previewing() {
            return !this.previewEntities.isEmpty();
        }
    }

    // 3-row grid (27 slots); all 5 controls centered on the middle row (9-17).
    private static final int MODE_LINE_SLOT = 9;
    private static final int MODE_FACE_SLOT = 11;
    private static final int MODE_COPY_SLOT = 13;
    private static final int MODE_WATER_SLOT = 15;
    private static final int RANGE_SLOT = 17;

    private final NamespacedKey wandKey;
    private final NamespacedKey modeKey;
    private final NamespacedKey rangeKey;
    private final int maxLength;
    private final ItemTierService tiers;
    private final PrismaPumpService prismaPump;
    private final Map<UUID, Deque<LastAction>> undoHistory = new HashMap<>();
    private final Set<UUID> viewingMenu = new HashSet<>();
    private final Map<UUID, CopySession> copySessions = new HashMap<>();

    public BuilderWandService(Plugin plugin, ItemTierService tiers, PrismaPumpService prismaPump) {
        this.prismaPump = prismaPump;
        this.wandKey = new NamespacedKey(plugin, "builder_wand");
        this.modeKey = new NamespacedKey(plugin, "builder_wand_mode");
        this.rangeKey = new NamespacedKey(plugin, "builder_wand_range");
        this.maxLength = Math.max(1, plugin.getConfig().getInt("builder-wand.max-length", 64));
        this.tiers = tiers;
    }

    public ItemStack create(Language l) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.wandKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(this.modeKey, PersistentDataType.STRING, FillMode.LINE.name());
        meta.getPersistentDataContainer().set(this.rangeKey, PersistentDataType.INTEGER, this.maxLength);
        // A one-of-a-kind admin tool, not a plain Blaze Rod - pin it to Tier S so
        // ItemTierService's periodic pass doesn't sort it into Tier E with every other one.
        this.tiers.forceTier(meta, ItemTier.S);
        item.setItemMeta(meta);
        this.refreshLore(item, l);
        return item;
    }

    /** Rewrites the wand's name/lore to reflect its current {@link FillMode} and range - called on creation and whenever either setting changes. */
    private void refreshLore(ItemStack item, Language l) {
        ItemMeta meta = item.getItemMeta();
        FillMode mode = this.mode(item);
        meta.displayName(this.line(l.choose("Varinha do Construtor", "Builder's Wand"), NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(l.choose("Clique direito num bloco pra estender", "Right-click a block to extend"), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("na direção da face clicada.", "in the clicked face's direction."), NamedTextColor.GRAY));
        for (String part : LoreWrap.wrapText(l.choose("Clique esquerdo abre o menu de configurações.", "Left-click opens the settings menu."), LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.line(part, NamedTextColor.GRAY));
        }
        for (String part : LoreWrap.wrapText(l.choose("Shift + clique esquerdo desfaz a última ação (repita pra desfazer mais).", "Shift + left-click undoes the last action (repeat to undo further back)."), LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.line(part, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(this.line(l.choose("Modo: ", "Mode: ") + switch (mode) {
            case LINE -> l.choose("Linha/Coluna", "Line/Column");
            case FACE -> l.choose("Face inteira (parede/chão)", "Whole face (wall/floor)");
            case COPY -> l.choose("Copiar & Colar", "Copy & Paste");
            case WATER -> l.choose("Nivelar Água", "Level Water");
        }, NamedTextColor.YELLOW));
        lore.add(this.line(l.choose("Alcance: ", "Range: ") + this.rangeLabel(this.range(item), l), NamedTextColor.YELLOW));
        lore.add(this.line(l.choose("Criativo: não gasta blocos.", "Creative: doesn't use blocks."), NamedTextColor.DARK_GRAY));
        lore.add(this.line(l.choose("Sobrevivência: precisa ter os blocos.", "Survival: needs the blocks."), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
    }

    /** Drops the remembered last action for a player (call on quit - no point holding Block references for an offline player), and despawns any ghost preview entities left over from an in-progress {@link FillMode#COPY}. */
    public void forget(UUID playerId) {
        this.undoHistory.remove(playerId);
        this.viewingMenu.remove(playerId);
        CopySession session = this.copySessions.remove(playerId);
        if (session != null) {
            session.previewEntities.forEach(BlockDisplay::remove);
        }
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.wandKey, PersistentDataType.BYTE);
    }

    /** The wand's current fill mode - {@link FillMode#LINE} if the item predates this setting or the tag is missing/corrupted. */
    public FillMode mode(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String raw = meta == null ? null : meta.getPersistentDataContainer().get(this.modeKey, PersistentDataType.STRING);
        if (raw == null) {
            return FillMode.LINE;
        }
        try {
            return FillMode.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return FillMode.LINE;
        }
    }

    /**
     * Sentinel {@link #range} value meaning "no cap at all" - the top preset in {@link
     * #rangePresets}, past the server's own max-length. Admin-only tool, so this is a deliberate
     * opt-in, not a safety hole. Deliberately a bounded number, not {@code Integer.MAX_VALUE}:
     * {@link #extendLine}/{@link #extendFace} place every block synchronously, in the same
     * tick, on the main server thread - each {@code setBlockData} call can trigger physics and
     * lighting recalculation, so tens of thousands of blocks in one go is already enough to
     * freeze the server for a noticeable moment, independent of ever looping forever. 10,000
     * blocks in one action is generous - many full builds' worth - while still keeping a
     * worst case bounded.
     */
    public static final int UNLIMITED = 10_000;

    /**
     * The wand's current per-item block limit: {@link #UNLIMITED} if that's what's picked in
     * the menu, otherwise clamped to [1, {@code builder-wand.max-length}] - a player can only
     * ever dial it down from the server's own cap (or explicitly opt out via Unlimited), never
     * accidentally end up past it from a stale stored value after an admin lowers the config.
     */
    public int range(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Integer stored = meta == null ? null : meta.getPersistentDataContainer().get(this.rangeKey, PersistentDataType.INTEGER);
        if (stored == null) {
            return this.maxLength;
        }
        if (stored == UNLIMITED) {
            return UNLIMITED;
        }
        return Math.max(1, Math.min(this.maxLength, stored));
    }

    /** The selectable range presets shown in the mode menu: powers of two from 8 up to the configured max-length, plus {@link #UNLIMITED} as the last, top option. */
    private List<Integer> rangePresets() {
        List<Integer> presets = new ArrayList<>();
        int v = 8;
        while (v < this.maxLength) {
            presets.add(v);
            v *= 2;
        }
        presets.add(this.maxLength);
        presets.add(UNLIMITED);
        return presets;
    }

    // ---- Mode menu ---------------------------------------------------------

    /** Opens the small 1-row settings menu (fill mode + range) for {@code item} (the wand currently in the player's hand). */
    public void openModeMenu(Player p, ItemStack item) {
        Language l = Language.of(p);
        // 54 (6 rows), not 27 (3 rows): menu.MenuBackground#apply only actually renders
        // its background for a 3- or 6-row inventory, and the 3-row variant was found to
        // render blank white in practice (see skills.PersonalStorageService's own doc on
        // LARGE_CANVAS) - the extra rows below stay pure filler.
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Varinha: Configurações", "Wand: Settings"));
        this.renderMenu(v, item, l);
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewingMenu.add(p.getUniqueId());
    }

    private void renderMenu(Inventory v, ItemStack item, Language l) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 54; i++) {
            v.setItem(i, filler);
        }
        FillMode current = this.mode(item);
        v.setItem(MODE_LINE_SLOT, this.modeOption(FillMode.LINE, current, l));
        v.setItem(MODE_FACE_SLOT, this.modeOption(FillMode.FACE, current, l));
        v.setItem(MODE_COPY_SLOT, this.modeOption(FillMode.COPY, current, l));
        v.setItem(MODE_WATER_SLOT, this.modeOption(FillMode.WATER, current, l));
        v.setItem(RANGE_SLOT, this.rangeItem(this.range(item), l));
    }

    public boolean viewingMenu(Player p) {
        return this.viewingMenu.contains(p.getUniqueId());
    }

    public void closeMenu(Player p) {
        this.viewingMenu.remove(p.getUniqueId());
    }

    /**
     * Handles a click inside the settings menu - applies the picked mode or range step to
     * whatever wand {@code p} is currently holding, then re-renders the menu in place (it
     * never auto-closes, so mode and range can both be tweaked in the same session).
     */
    public void handleMenuClick(Player p, int slot, ClickType click) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.isWand(held)) {
            return;
        }
        Language l = Language.of(p);
        if (slot == MODE_LINE_SLOT) {
            this.setMode(held, FillMode.LINE, l);
        } else if (slot == MODE_FACE_SLOT) {
            this.setMode(held, FillMode.FACE, l);
        } else if (slot == MODE_COPY_SLOT) {
            this.setMode(held, FillMode.COPY, l);
        } else if (slot == MODE_WATER_SLOT) {
            this.setMode(held, FillMode.WATER, l);
        } else if (slot == RANGE_SLOT) {
            this.cycleRange(held, click.isLeftClick(), l);
        } else {
            return;
        }
        this.renderMenu(p.getOpenInventory().getTopInventory(), held, l);
    }

    private void setMode(ItemStack item, FillMode mode, Language l) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.modeKey, PersistentDataType.STRING, mode.name());
        item.setItemMeta(meta);
        this.refreshLore(item, l);
    }

    /** Steps the wand's range one preset up ({@code forward}) or down, clamped at the ends of {@link #rangePresets}. */
    private void cycleRange(ItemStack item, boolean forward, Language l) {
        List<Integer> presets = this.rangePresets();
        int index = presets.indexOf(this.range(item));
        if (index < 0) {
            index = presets.size() - 1;
        }
        int next = forward ? Math.min(presets.size() - 1, index + 1) : Math.max(0, index - 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.rangeKey, PersistentDataType.INTEGER, presets.get(next));
        item.setItemMeta(meta);
        this.refreshLore(item, l);
    }

    private ItemStack modeOption(FillMode option, FillMode current, Language l) {
        boolean selected = option == current;
        Material material = switch (option) {
            case LINE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case FACE -> Material.ORANGE_STAINED_GLASS_PANE;
            case COPY -> Material.LIME_STAINED_GLASS_PANE;
            case WATER -> Material.CYAN_STAINED_GLASS_PANE;
        };
        String label = switch (option) {
            case LINE -> l.choose("Linha/Coluna", "Line/Column");
            case FACE -> l.choose("Face inteira (parede/chão)", "Whole face (wall/floor)");
            case COPY -> l.choose("Copiar & Colar", "Copy & Paste");
            case WATER -> l.choose("Nivelar Água", "Level Water");
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.line((selected ? "✔ " : "") + label, selected ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, selected));
        List<Component> lore = new ArrayList<>();
        String description = switch (option) {
            case LINE -> l.choose("Estende só na direção da face clicada.", "Extends only along the clicked face's direction.");
            case FACE -> l.choose("Copia a parede/chão existente pra camada de fora.", "Copies the existing wall/floor onto the layer beyond it.");
            case COPY -> l.choose("Copia uma área e cola em outro lugar, com preview.", "Copies an area and pastes it elsewhere, with a preview.");
            case WATER -> l.choose("Clique num bloco do lado de uma água pra nivelar o buraco/canal.", "Click a block next to water to level the hole/channel.");
        };
        lore.add(this.line(description, NamedTextColor.GRAY));
        if (option == FillMode.LINE || option == FillMode.FACE) {
            for (String part : LoreWrap.wrapText(l.choose(
                    "Também planta trigo/cenoura/batata/beterraba/nether wart em terra arável já pronta, e cana-de-açúcar do lado de água.",
                    "Also plants wheat/carrots/potatoes/beetroot/nether wart onto already-tilled farmland, and sugar cane next to water."), LoreWrap.DEFAULT_WIDTH)) {
                lore.add(this.line(part, NamedTextColor.DARK_GRAY));
            }
        }
        if (selected) {
            lore.add(Component.empty());
            lore.add(this.line(l.choose("Modo atual", "Current mode"), NamedTextColor.GREEN));
        }
        meta.lore(lore);
        if (selected) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /** {@code current} as shown to the player - the plain number, or "Ilimitado"/"Unlimited" for {@link #UNLIMITED}. */
    private String rangeLabel(int current, Language l) {
        return current == UNLIMITED ? l.choose("Ilimitado", "Unlimited") : current + l.choose(" blocos", " blocks");
    }

    private ItemStack rangeItem(int current, Language l) {
        ItemStack item = new ItemStack(current == UNLIMITED ? Material.ENDER_EYE : Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.line(l.choose("Alcance: ", "Range: ") + this.rangeLabel(current, l), NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(l.choose("Clique esquerdo: aumenta", "Left-click: increase"), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Clique direito: diminui", "Right-click: decrease"), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(this.line(l.choose("Máximo do servidor: " + this.maxLength, "Server max: " + this.maxLength), NamedTextColor.DARK_GRAY));
        if (current == UNLIMITED) {
            for (String part : LoreWrap.wrapText(l.choose("Sem teto - cuidado em áreas muito grandes.", "No cap - be careful in very large areas."), LoreWrap.DEFAULT_WIDTH)) {
                lore.add(this.line(part, NamedTextColor.RED));
            }
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    // ---- Extending ----------------------------------------------------------

    /**
     * Crop-like plants {@link #extend} can place even though none of them are solid
     * ({@link Material#isSolid()} is false for every one of these) - each still has its
     * own real placement rule enforced by {@link #canPlantAt}, so extending one across
     * the rest of an already-prepared field or canal (farmland already tilled, a dirt
     * strip already dug next to water) plants exactly where planting it by hand would
     * actually take, instead of forcing a floating crop onto whatever's beyond.
     */
    private static final Set<Material> PLANTABLE = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.SUGAR_CANE);

    private static final BlockFace[] HORIZONTAL_FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    /** Sugar Cane's own valid ground: vanilla's real base-block list, minus nothing - {@link #canPlantAt} still requires water adjacent to it (or another Sugar Cane stacked below, handled separately). */
    private static final Set<Material> SUGAR_CANE_BASES = EnumSet.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.PODZOL,
            Material.ROOTED_DIRT, Material.MYCELIUM, Material.SAND, Material.RED_SAND, Material.FARMLAND);

    /**
     * Extends {@code clicked}'s block starting from the block adjacent to it (in
     * {@code face}'s direction), replacing air only, per {@code item}'s current {@link
     * FillMode}: {@link FillMode#LINE} walks a straight line/column; {@link
     * FillMode#FACE} traces the whole existing wall/floor (or planted field) {@code
     * clicked} belongs to and paints a matching new layer one block further out (see
     * {@link #extendFace}). Both stop at {@code item}'s own {@link #range}, or
     * (Survival only) the moment {@code p} runs out of the matching seed/material.
     * Returns how many blocks were actually placed.
     */
    public int extend(Player p, Block clicked, BlockFace face, ItemStack item) {
        Material material = clicked.getType();
        if (!material.isBlock() || (!material.isSolid() && !PLANTABLE.contains(material))) {
            return 0;
        }
        BlockData data = clicked.getBlockData();
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        int limit = this.range(item);
        List<Block> placedBlocks = this.mode(item) == FillMode.FACE
                ? this.extendFace(p, clicked, face, material, data, creative, limit)
                : this.extendLine(p, clicked, face, material, data, creative, limit);
        if (!placedBlocks.isEmpty()) {
            Map<Material, Integer> consumed = creative ? Map.of() : Map.of(seedItemFor(material), placedBlocks.size());
            this.pushUndo(p, new LastAction(placedBlocks, consumed));
        }
        return placedBlocks.size();
    }

    /**
     * The inventory item {@link #consume}/{@link #giveBack} should use for {@code
     * cropBlock} - almost always the block's own Material (a Stone block is consumed as
     * a Stone item), except the 4 farmland row crops, whose planted-block Material
     * differs from the seed item that actually sows them (there's no such thing as a
     * "Wheat block" item, only {@link Material#WHEAT_SEEDS}).
     */
    private static Material seedItemFor(Material cropBlock) {
        return switch (cropBlock) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            default -> cropBlock;
        };
    }

    /**
     * Whether {@code plant} could actually take root at {@code target} right now,
     * mirroring vanilla's own placement rule for each: the 4 farmland crops need {@link
     * Material#FARMLAND} directly below, Nether Wart needs {@link Material#SOUL_SAND},
     * and Sugar Cane needs either another Sugar Cane directly below (stacking taller is
     * always legal) or one of {@link #SUGAR_CANE_BASES} with water horizontally adjacent
     * to THAT base block (not to {@code target} itself, which sits one block above the
     * water's own level) - see {@link #canHoldSugarCane}. Every other Material (the
     * pre-existing solid-block extend path) never calls this at all - see {@link
     * #extend}'s {@link #PLANTABLE} gate.
     */
    private boolean canPlantAt(Material plant, Block target) {
        Block below = target.getRelative(BlockFace.DOWN);
        return switch (plant) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS -> below.getType() == Material.FARMLAND;
            case NETHER_WART -> below.getType() == Material.SOUL_SAND;
            case SUGAR_CANE -> this.canHoldSugarCane(below);
            default -> true;
        };
    }

    private boolean canHoldSugarCane(Block below) {
        if (below.getType() == Material.SUGAR_CANE) {
            return true;
        }
        if (!SUGAR_CANE_BASES.contains(below.getType())) {
            return false;
        }
        for (BlockFace face : HORIZONTAL_FACES) {
            if (below.getRelative(face).getType() == Material.WATER) {
                return true;
            }
        }
        return false;
    }

    private List<Block> extendLine(Player p, Block clicked, BlockFace face, Material material, BlockData data, boolean creative, int limit) {
        boolean plant = PLANTABLE.contains(material);
        Material item = seedItemFor(material);
        List<Block> placedBlocks = new ArrayList<>();
        Block cursor = clicked.getRelative(face);
        for (int i = 0; i < limit; i++) {
            if (!cursor.getType().isAir()) {
                break;
            }
            if (plant && !this.canPlantAt(material, cursor)) {
                break;
            }
            if (!creative && !this.consume(p, item)) {
                break;
            }
            cursor.setBlockData(data);
            placedBlocks.add(cursor);
            cursor = cursor.getRelative(face);
        }
        return placedBlocks;
    }

    /**
     * {@link FillMode#FACE} version of {@link #extendLine}: first traces the real,
     * existing shape of the wall/floor {@code clicked} belongs to - a BFS flood-fill
     * across the plane perpendicular to {@code face}, through contiguous blocks of the
     * same Material as {@code clicked} (same algorithm {@link
     * dev.icaro.foodtooltips.destroyer.DestroyerHandService#clear} uses to find what to
     * clear) - then paints a matching new layer one block further out along {@code
     * face}, wherever that's still air. Tracing the existing structure first, instead of
     * flood-filling the target air layer directly, is what keeps this bounded by the
     * wall's own real footprint: air with nothing behind it has no natural edge to stop
     * at, so filling it directly would just balloon outward in every direction until it
     * hit {@code limit}, regardless of any actual wall shape.
     */
    private List<Block> extendFace(Player p, Block clicked, BlockFace face, Material material, BlockData data, boolean creative, int limit) {
        BlockFace[] axes = planeAxes(face);
        List<Block> wallBlocks = new ArrayList<>();
        Set<Pos> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(clicked);
        visited.add(Pos.of(clicked));
        while (!queue.isEmpty() && wallBlocks.size() < limit) {
            Block b = queue.poll();
            if (b.getType() != material) {
                continue;
            }
            wallBlocks.add(b);
            for (BlockFace dir : axes) {
                Block next = b.getRelative(dir);
                if (visited.add(Pos.of(next))) {
                    queue.add(next);
                }
            }
        }
        boolean plant = PLANTABLE.contains(material);
        Material item = seedItemFor(material);
        List<Block> placedBlocks = new ArrayList<>();
        for (Block wallBlock : wallBlocks) {
            Block target = wallBlock.getRelative(face);
            if (!target.getType().isAir()) {
                continue;
            }
            if (plant && !this.canPlantAt(material, target)) {
                continue;
            }
            if (!creative && !this.consume(p, item)) {
                break;
            }
            target.setBlockData(data);
            placedBlocks.add(target);
        }
        return placedBlocks;
    }

    /** Plain block coordinates, used only as a reliable-by-value HashSet key for flood fills (see {@link #extendFace}). */
    private record Pos(int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getX(), b.getY(), b.getZ());
        }
    }

    /** The 4 {@link BlockFace}s spanning the plane perpendicular to {@code face} (e.g. UP/DOWN's plane is North/South/East/West - a floor or ceiling). */
    private static BlockFace[] planeAxes(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            case EAST, WEST -> new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH};
            default -> new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.EAST, BlockFace.WEST};
        };
    }

    /**
     * Shift + left-click's counterpart to {@link #extend}: puts back air where the most
     * recent not-yet-undone placement (extend or paste) put blocks, and - only for whatever
     * it actually paid for (Survival) - returns that material to the player's inventory,
     * dropping anything that doesn't fit. Walks one step further back in {@code p}'s history
     * each time it's called (up to {@link #MAX_UNDO_HISTORY} steps), like a normal editor's
     * undo - not just the single very last action.
     */
    public int undo(Player p) {
        Deque<LastAction> history = this.undoHistory.get(p.getUniqueId());
        if (history == null || history.isEmpty()) {
            return 0;
        }
        LastAction action = history.removeFirst();
        for (Block b : action.placed()) {
            b.setType(Material.AIR);
        }
        for (Map.Entry<Material, Integer> entry : action.consumed().entrySet()) {
            this.giveBack(p, entry.getKey(), entry.getValue());
        }
        return action.placed().size();
    }

    /** Records {@code action} as {@code p}'s most recent undo-able step, trimming the oldest entry once {@link #MAX_UNDO_HISTORY} is exceeded. */
    private void pushUndo(Player p, LastAction action) {
        Deque<LastAction> history = this.undoHistory.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        history.addFirst(action);
        while (history.size() > MAX_UNDO_HISTORY) {
            history.removeLast();
        }
    }

    /** Hands back {@code count} of {@code material}, splitting into full stacks and dropping whatever doesn't fit in the inventory. */
    private void giveBack(Player p, Material material, int count) {
        int max = material.getMaxStackSize();
        while (count > 0) {
            int amount = Math.min(max, count);
            count -= amount;
            for (ItemStack leftover : p.getInventory().addItem(new ItemStack(material, amount)).values()) {
                p.getWorld().dropItem(p.getLocation(), leftover);
            }
        }
    }

    /** Removes one item of {@code material} from the player's main storage; returns false if none was found. */
    private boolean consume(Player p, Material material) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            if (stack != null && stack.getType() == material) {
                stack.setAmount(stack.getAmount() - 1);
                inv.setItem(i, stack.getAmount() <= 0 ? null : stack);
                return true;
            }
        }
        return false;
    }

    // ---- Copy & Paste --------------------------------------------------------

    /**
     * Handles a right-click while the wand is in {@link FillMode#COPY}, driving the whole
     * copy/paste state machine for {@code p}:
     * <ul>
     *   <li>No buffer yet, plain right-click: sets/replaces corner A.</li>
     *   <li>No buffer yet, shift + right-click: requires corner A already set, and captures
     *       the box between it and {@code clicked} into the buffer (see {@link #captureBuffer}).</li>
     *   <li>Buffer ready, not previewing, plain right-click: starts a live preview anchored
     *       one block beyond {@code clicked}'s {@code face} - the same "adjacent to the
     *       clicked face" convention {@link #extend} uses.</li>
     *   <li>Buffer ready, previewing, plain right-click: pastes for real at the preview's
     *       current position (see {@link #confirmPaste}).</li>
     *   <li>Buffer ready, shift + right-click: clears the buffer so a new area can be copied.</li>
     * </ul>
     * The buffer survives a confirmed paste (like a clipboard) so the same copy can be
     * pasted repeatedly in different spots - that's the whole point of this mode.
     */
    public Component handleCopyClick(Player p, Block clicked, BlockFace face, boolean sneaking) {
        Language l = Language.of(p);
        CopySession session = this.copySessions.computeIfAbsent(p.getUniqueId(), k -> new CopySession());
        if (session.buffer == null) {
            if (sneaking) {
                if (session.pos1 == null) {
                    return this.line(l.choose("Marque a posição A primeiro (clique direito).", "Mark position A first (right-click)."), NamedTextColor.RED);
                }
                return this.captureBuffer(session, session.pos1, clicked, l);
            }
            session.pos1 = clicked;
            return this.line(l.choose("Posição A definida. Shift + clique direito na posição B.", "Position A set. Shift + right-click position B."), NamedTextColor.YELLOW);
        }
        if (sneaking) {
            this.clearPreview(session);
            session.buffer = null;
            session.pos1 = null;
            return this.line(l.choose("Cópia limpa.", "Copy cleared."), NamedTextColor.GOLD);
        }
        if (!session.previewing()) {
            this.startPreview(session, clicked, face);
            return this.line(l.choose("Preview iniciado - olhe ao redor pra posicionar, clique direito de novo pra colar.", "Preview started - look around to position it, right-click again to paste."), NamedTextColor.GREEN);
        }
        return this.confirmPaste(p, session);
    }

    /**
     * Max block volume {@link #captureBuffer} will copy in one go. Deliberately its own
     * constant, not {@link #range}: that field caps a 1D line/face extend's length (8 up to
     * {@code builder-wand.max-length}, 64 by default), which would silently cap even a
     * modest structure's copy - a plain 5x5x5 selection is already 125 blocks, well past a
     * default range of 64. Capturing (and later pasting) still happens synchronously on the
     * main thread, same as {@link #extend}, so this stays bounded rather than unlimited, but
     * generous enough for a real build.
     */
    private static final int COPY_VOLUME_LIMIT = 20_000;

    /**
     * Captures every non-air block in the box between {@code pos1} and {@code pos2} into
     * {@code session}'s buffer, relative to the box's own minimum corner. Air blocks are
     * deliberately skipped - a paste only ever adds the copied shape, it never punches holes
     * in whatever it lands on. {@link #COPY_VOLUME_LIMIT} caps the box's volume.
     */
    private Component captureBuffer(CopySession session, Block pos1, Block pos2, Language l) {
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return this.line(l.choose("As duas posições precisam estar no mesmo mundo.", "Both positions must be in the same world."), NamedTextColor.RED);
        }
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > COPY_VOLUME_LIMIT) {
            return this.line(l.choose("Área grande demais (" + volume + " blocos, máximo " + COPY_VOLUME_LIMIT + "). Diminua a área.",
                    "Area too large (" + volume + " blocks, max " + COPY_VOLUME_LIMIT + "). Shrink the area."), NamedTextColor.RED);
        }
        World w = pos1.getWorld();
        List<CopiedBlock> buffer = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir()) {
                        continue;
                    }
                    buffer.add(new CopiedBlock(x - minX, y - minY, z - minZ, b.getBlockData()));
                }
            }
        }
        session.pos1 = null;
        session.buffer = buffer;
        return this.line(l.choose("Área copiada: " + buffer.size() + " blocos.", "Area copied: " + buffer.size() + " blocks."), NamedTextColor.GREEN);
    }

    /** Spawns one ghost {@link BlockDisplay} per {@code session.buffer} entry, anchored at {@code anchor}. {@link #tickPreview} then keeps them following the player's crosshair every tick. */
    private void startPreview(CopySession session, Block clicked, BlockFace face) {
        Block anchor = clicked.getRelative(face);
        session.previewAnchor = anchor;
        for (CopiedBlock cb : session.buffer) {
            Location loc = anchor.getRelative(cb.dx(), cb.dy(), cb.dz()).getLocation();
            BlockDisplay display = anchor.getWorld().spawn(loc, BlockDisplay.class, e -> {
                e.setBlock(cb.data());
                e.setGlowing(true);
            });
            session.previewEntities.add(display);
        }
    }

    /** Teleports every ghost entity to sit at {@code anchor} plus its own buffer offset - moving, not respawning, so following the player's crosshair every tick stays cheap. */
    private void movePreview(CopySession session, Block anchor) {
        session.previewAnchor = anchor;
        for (int i = 0; i < session.buffer.size(); i++) {
            CopiedBlock cb = session.buffer.get(i);
            Block target = anchor.getRelative(cb.dx(), cb.dy(), cb.dz());
            session.previewEntities.get(i).teleport(target.getLocation());
        }
    }

    /** Despawns {@code session}'s ghost entities (if any) without touching the buffer itself. */
    private void clearPreview(CopySession session) {
        session.previewEntities.forEach(BlockDisplay::remove);
        session.previewEntities.clear();
        session.previewAnchor = null;
    }

    /**
     * How far in front of {@code p}'s eyes {@link #previewTarget} floats the preview when
     * their crosshair isn't on any block at all (open sky, looking out past {@code
     * BLOCK_RAY_DISTANCE}) - close enough to place comfortably, far enough not to sit
     * awkwardly inside the player's own head.
     */
    private static final double FLOAT_DISTANCE = 5.0;
    private static final double BLOCK_RAY_DISTANCE = 64.0;

    /**
     * Called every tick (from the plugin's periodic loop) for every online player - a no-op
     * unless {@code p} currently has a live {@link FillMode#COPY} preview up, in which case
     * it re-targets {@code p}'s crosshair (see {@link #previewTarget}) and moves the ghost
     * blocks to follow it, exactly like the real paste target would track a right-click.
     * Skips the move entirely when the targeted spot hasn't changed, since teleporting a
     * whole buffer's worth of entities every tick for a player stood still and looking at
     * the same spot would be pure waste.
     */
    public void tickPreview(Player p) {
        CopySession session = this.copySessions.get(p.getUniqueId());
        if (session == null || !session.previewing()) {
            return;
        }
        Block anchor = this.previewTarget(p);
        if (anchor.equals(session.previewAnchor)) {
            return;
        }
        this.movePreview(session, anchor);
    }

    /**
     * Where the live preview should sit right now: the block just beyond whatever {@code p}
     * is looking at, same convention a real right-click targets ({@link #extend}, {@link
     * #startPreview}) - or, when the crosshair isn't on any block at all, a spot floating
     * {@link #FLOAT_DISTANCE} blocks straight ahead of their eyes, so the preview (and the
     * eventual paste, which lands wherever the preview currently is) can be positioned in
     * mid-air too, not just against something solid.
     */
    private Block previewTarget(Player p) {
        RayTraceResult ray = p.rayTraceBlocks(BLOCK_RAY_DISTANCE, FluidCollisionMode.NEVER);
        if (ray != null && ray.getHitBlock() != null && ray.getHitBlockFace() != null) {
            return ray.getHitBlock().getRelative(ray.getHitBlockFace());
        }
        Location eye = p.getEyeLocation();
        return eye.clone().add(eye.getDirection().normalize().multiply(FLOAT_DISTANCE)).getBlock();
    }

    /** True if {@code p} had a live preview that got cancelled (despawns the ghost entities, keeps the buffer); false if there was nothing to cancel. */
    public boolean cancelPreview(Player p) {
        CopySession session = this.copySessions.get(p.getUniqueId());
        if (session == null || !session.previewing()) {
            return false;
        }
        this.clearPreview(session);
        return true;
    }

    /**
     * Places {@code session.buffer} at its current preview position for real, consuming one
     * matching block per placement in Survival (stopping early, same as {@link #extend}, the
     * moment {@code p} runs out of a needed material) and registering the result as this
     * player's {@link #undo}-able last action. The buffer itself is kept afterwards, so the
     * same copy can be pasted again elsewhere.
     */
    private Component confirmPaste(Player p, CopySession session) {
        Language l = Language.of(p);
        Block anchor = session.previewAnchor;
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        List<Block> placedBlocks = new ArrayList<>();
        Map<Material, Integer> consumed = new HashMap<>();
        for (CopiedBlock cb : session.buffer) {
            Block target = anchor.getRelative(cb.dx(), cb.dy(), cb.dz());
            Material material = cb.data().getMaterial();
            Material item = seedItemFor(material);
            if (!creative && !this.consume(p, item)) {
                break;
            }
            target.setBlockData(cb.data());
            placedBlocks.add(target);
            if (!creative) {
                consumed.merge(item, 1, Integer::sum);
            }
        }
        this.clearPreview(session);
        if (placedBlocks.isEmpty()) {
            return this.line(l.choose("Nada pra colar aqui.", "Nothing to paste here."), NamedTextColor.RED);
        }
        this.pushUndo(p, new LastAction(placedBlocks, consumed));
        return this.line("+" + placedBlocks.size() + " " + l.choose("blocos colados", "blocks pasted"), NamedTextColor.GREEN);
    }

    // ---- Water leveling --------------------------------------------------------

    /**
     * {@link FillMode#WATER}'s whole job: right-clicking a block next to water levels the
     * connected air pocket on that same layer into water, via the exact same {@link
     * PrismaPumpService#fillAdjacentWater} logic the Prismapump item itself places to
     * trigger. Not undo-able (unlike {@link #extend}/{@link #confirmPaste}) - reversing a
     * flood fill would mean remembering every single block it touched, and turning water
     * back into air one-for-one is trivial to do by hand if it ever goes wrong.
     */
    public Component pumpWater(Player p, Block clicked) {
        Language l = Language.of(p);
        int filled = this.prismaPump.fillAdjacentWater(clicked);
        if (filled <= 0) {
            return this.line(l.choose("Nenhuma água do lado pra nivelar.", "No water nearby to level."), NamedTextColor.RED);
        }
        return this.line("+" + filled + " " + l.choose("água", "water"), NamedTextColor.AQUA);
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
