package dev.icaro.foodtooltips.enchant;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalSkill;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import dev.icaro.foodtooltips.skills.SkillProgress;
import dev.icaro.foodtooltips.skills.SkillProgressBarService;
import dev.icaro.foodtooltips.skills.SkillType;
import dev.icaro.foodtooltips.util.LoreWrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/**
 * The reworked Enchanting Table screen - opened by right-clicking a real Enchanting
 * Table block (see {@code EnchantMenuListener}) instead of vanilla's own random-offer
 * UI. Every {@link EnchantEntry} - the plugin's own {@link IcarusEnchant}s AND every
 * real vanilla enchantment (see {@link EnchantService#allEntries}) - is picked
 * explicitly (never random). The main screen's catalog is empty until an item is
 * placed in {@link #ITEM_SLOT}, then shows only entries that item can actually take
 * (see {@link EnchantService#compatibleEntries}) and live-refreshes whenever the item
 * changes. Picking one opens a level-select screen ({@link #openLevelSelect}) showing
 * every level's cost/effect, and applying a level spends real vanilla XP levels (like
 * an anvil) - see {@link EnchantService}. No limit on how many distinct entries an
 * item can carry. An item can be brought back to the table to add more levels/entries
 * - removing one it already has is the Grindstone's job now (see {@code
 * GrindstoneMenuService}), not this screen's. A separate Guide screen ({@link
 * #openGuide}) lists every entry regardless of any item, searchable via a sign (see
 * {@code EnchantMenuListener}).
 *
 * <p>v1 scope: this is the interface and application/storage layer only - none of
 * the plugin's own custom enchants (currently none defined - see {@link
 * IcarusEnchant}) actually change combat numbers yet, that wiring is a deliberate
 * follow-up. Vanilla enchantments applied here work exactly as they always have.
 */
public final class EnchantMenuService {
    public static final int ITEM_SLOT = 19;
    private static final int TABLE_ICON_SLOT = 28;
    private static final int BOOKSHELF_SLOT = 48;
    private static final int GUIDE_SLOT = 50;
    private static final int SCROLL_UP_SLOT = 17;
    private static final int SCROLL_DOWN_SLOT = 35;
    private static final int BACK_SLOT = 49;
    /** Where enchant books sit in the main screen's catalog grid - 3 rows of 5, matching the requested layout. */
    private static final int[] CATALOG_SLOTS = {12, 13, 14, 15, 16, 21, 22, 23, 24, 25, 30, 31, 32, 33, 34};
    /** Where each of an enchant's levels sits on the level-select screen (up to {@link EnchantEntry#maxLevel()} used - the max across every vanilla entry is 5, so this covers them all too). */
    private static final int[] LEVEL_SLOTS = {20, 21, 22, 23, 24};
    private static final int LEVEL_PREVIEW_SLOT = 4;
    /** Every position on the 5x5 perimeter ring 2 blocks from the table (X/Z offsets where the larger of the two is 2) - see {@link #bookshelfPower}. */
    private static final int[][] BOOKSHELF_DIRECTIONS = {
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
            {1, 2}, {1, -2}, {-1, 2}, {-1, -2},
            {2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
    /** Never exceeded by {@link #bookshelfPower} - well below the real achievable max ({@link #BOOKSHELF_DIRECTIONS}' 16 positions x 2 floors = 32), matching {@link EnchantEntry#MAX_BOOKSHELF_POWER} (what {@link EnchantEntry#requiredBookshelfPower} scales up to) - a single full ring on one floor alone already maxes it out. */
    private static final int BOOKSHELF_POWER_CAP = EnchantEntry.MAX_BOOKSHELF_POWER;

    private static final int GUIDE_TITLE_SLOT = 4;
    private static final int GUIDE_PREV_PAGE_SLOT = 45;
    private static final int GUIDE_BACK_SLOT = 48;
    private static final int GUIDE_CLOSE_SLOT = 49;
    static final int GUIDE_SEARCH_SLOT = 50;
    private static final int GUIDE_NEXT_PAGE_SLOT = 53;
    /** Where enchant books sit in the Guide screen's catalog grid - 4 rows of 7. */
    private static final int[] GUIDE_CATALOG_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /**
     * Real vanilla's own Enchanting XP curve (Enchantment Table/Anvil, per the wiki:
     * {@code XP = 3.5 * X^1.5} for X levels spent) - reused here as the Enchanting
     * skill's own XP gain per application, X being {@link #discountedCost}'s result
     * (the real levels actually charged, discount already applied). See {@link
     * #gainEnchantingXp}.
     */
    private static final double ENCHANTING_XP_COEFFICIENT = 3.5;
    private static final double ENCHANTING_XP_EXPONENT = 1.5;
    /** Global XP for achieving a milestone (an entry/level applied through this table for the first time) - {@link #MILESTONE_MAX_LEVEL_XP} instead if that level is the entry's own max. See {@link #applyLevel}. */
    private static final long MILESTONE_XP = 1;
    private static final long MILESTONE_MAX_LEVEL_XP = 2;

    private static final int CATEGORY_WEAPONS_SLOT = 20;
    private static final int CATEGORY_TOOLS_SLOT = 22;
    private static final int CATEGORY_ARMOR_SLOT = 24;
    private static final int CATEGORY_BACK_SLOT = 49;
    private static final int MILESTONE_TITLE_SLOT = 4;
    private static final int MILESTONE_BACK_SLOT = 48;
    private static final int MILESTONE_PREV_PAGE_SLOT = 45;
    private static final int MILESTONE_NEXT_PAGE_SLOT = 53;
    /** Where enchant books sit in the Milestones list screen's catalog grid - same 4x7 shape as the Guide's own {@link #GUIDE_CATALOG_SLOTS}. */
    private static final int[] MILESTONE_CATALOG_SLOTS = GUIDE_CATALOG_SLOTS;

    /** Broad item-type groupings the Milestones screen filters by - see {@link #ITEM_TYPES}. */
    private enum EnchantCategory { WEAPONS, TOOLS, ARMOR }

    /** One representative Material used purely to test {@link EnchantEntry} compatibility against (real vanilla {@code canEnchantItem}/custom {@code canApplyTo}) - material TIER doesn't affect compatibility, only its base type does, so a Netherite/top-tier example stands in for its whole family. */
    private record ItemType(Material material, String pt, String en, EnchantCategory category) {
    }

    private static final List<ItemType> ITEM_TYPES = buildItemTypes();

    private static List<ItemType> buildItemTypes() {
        List<ItemType> types = new ArrayList<>(List.of(
                new ItemType(Material.NETHERITE_SWORD, "Espada", "Sword", EnchantCategory.WEAPONS),
                new ItemType(Material.TRIDENT, "Tridente", "Trident", EnchantCategory.WEAPONS),
                new ItemType(Material.MACE, "Maça", "Mace", EnchantCategory.WEAPONS),
                new ItemType(Material.BOW, "Arco", "Bow", EnchantCategory.WEAPONS),
                new ItemType(Material.CROSSBOW, "Besta", "Crossbow", EnchantCategory.WEAPONS),
                new ItemType(Material.NETHERITE_PICKAXE, "Picareta", "Pickaxe", EnchantCategory.TOOLS),
                new ItemType(Material.NETHERITE_AXE, "Machado", "Axe", EnchantCategory.TOOLS),
                new ItemType(Material.NETHERITE_SHOVEL, "Pá", "Shovel", EnchantCategory.TOOLS),
                new ItemType(Material.NETHERITE_HOE, "Enxada", "Hoe", EnchantCategory.TOOLS),
                new ItemType(Material.NETHERITE_HELMET, "Capacete", "Helmet", EnchantCategory.ARMOR),
                new ItemType(Material.NETHERITE_CHESTPLATE, "Peitoral", "Chestplate", EnchantCategory.ARMOR),
                new ItemType(Material.NETHERITE_LEGGINGS, "Calça", "Leggings", EnchantCategory.ARMOR),
                new ItemType(Material.NETHERITE_BOOTS, "Bota", "Boots", EnchantCategory.ARMOR),
                new ItemType(Material.ELYTRA, "Elytra", "Elytra", EnchantCategory.ARMOR)));
        // Spear (Wood/Stone/Copper/Iron/Gold/Diamond/Netherite - Mounts of Mayhem)
        // looked up by name rather than referencing Material.NETHERITE_SPEAR directly,
        // same reasoning PolearmDamageService's own _SPEAR-suffix check uses - avoids
        // ever needing to know the exact enum constant spelling at compile time.
        Material spear = Material.matchMaterial("NETHERITE_SPEAR");
        if (spear != null) {
            types.add(new ItemType(spear, "Lança", "Spear", EnchantCategory.WEAPONS));
        }
        return List.copyOf(types);
    }

    private final Plugin plugin;
    private final EnchantService enchants;
    private final GeneralSkillService general;
    private final SkillProgressBarService bars;
    private final GlobalLevelService global;
    private final EnchantMilestoneService milestones;
    /** Where GUIDE_BACK_SLOT/CATEGORY_BACK_SLOT go when there's no real table to return to (see {@link #hasRealTable}) - reopens the Enchanting skill screen in /skills, since that's the only other entry point into the Guide/Milestones screens. */
    private final java.util.function.Consumer<Player> skillsBack;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, ItemStack> pendingItem = new HashMap<>();
    private final Map<UUID, Location> tableLocation = new HashMap<>();
    /** Which {@link EnchantCategory} a player is currently browsing on the Milestones screen - see {@link #openMilestoneList}. */
    private final Map<UUID, EnchantCategory> milestoneCategory = new HashMap<>();
    /** Current search filter for the Guide screen, per player - absent/blank means unfiltered. Cleared whenever the player leaves the Guide screen. */
    private final Map<UUID, String> guideSearch = new HashMap<>();
    /**
     * Players in the middle of one of OUR OWN screen transitions (this class calling
     * {@link Player#openInventory}, which switches screens by implicitly firing an
     * {@link org.bukkit.event.inventory.InventoryCloseEvent} for the old one before the
     * new one opens) - {@code EnchantMenuListener}'s close handler checks this to tell
     * a deliberate transition apart from the player actually closing the menu, so it
     * doesn't return/drop the in-progress item or clear state mid-transition.
     */
    private final Set<UUID> transitioning = new HashSet<>();

    public EnchantMenuService(Plugin plugin, EnchantService enchants, GeneralSkillService general, SkillProgressBarService bars, GlobalLevelService global, EnchantMilestoneService milestones, java.util.function.Consumer<Player> skillsBack) {
        this.plugin = plugin;
        this.enchants = enchants;
        this.general = general;
        this.bars = bars;
        this.global = global;
        this.milestones = milestones;
        this.skillsBack = skillsBack;
    }

    /** Whether {@code p} has a real table backing the current menu session - see {@link #open}/{@link #close} - vs. having entered the Guide or Milestones screens straight from the Enchanting skill screen in /skills, with no table involved at all. */
    private boolean hasRealTable(Player p) {
        return this.tableLocation.containsKey(p.getUniqueId());
    }

    /** Routes a "back to menu" click to the physical table's own main screen if there is one, or back to the Enchanting skill screen in /skills otherwise (see {@link #hasRealTable}) - shared by the Guide's and the Milestones categories screen's own back buttons. */
    private void backFromStandaloneScreen(Player p) {
        if (this.hasRealTable(p)) {
            this.openMain(p, 0);
        } else if (this.skillsBack != null) {
            // Leaving this menu system entirely for SkillsMenuService's own - clear our
            // own state proactively first, same as every other cross-service handoff in
            // this plugin does (e.g. SkillsMenuService#handleClick removing its own view
            // before calling into mining/crafting/trash), rather than relying on the
            // implicit InventoryCloseEvent this triggers to clean it up after the fact.
            this.close(p);
            this.skillsBack.accept(p);
        } else {
            p.closeInventory();
        }
    }

    /** Opens the table for {@code p} - {@code table} is the physical block right-clicked, used to compute Bookshelf Power (see {@link #bookshelfPower}), which some enchantments/levels require a minimum amount of (see {@link EnchantEntry#requiredBookshelfPower}). */
    public void open(Player p, Location table) {
        this.tableLocation.put(p.getUniqueId(), table);
        this.openMain(p, 0);
    }

    private void openMain(Player p, int page) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Mesa de Encantamento", "Enchanting Table"));
        this.fill(v);
        ItemStack pending = this.pendingItem.remove(p.getUniqueId());
        v.setItem(ITEM_SLOT, pending);
        v.setItem(TABLE_ICON_SLOT, this.item(Material.ENCHANTING_TABLE,
                l.choose("Mesa de Encantamento", "Enchanting Table"),
                List.of(this.text(l.choose("Apenas representação.", "Just a representation."), NamedTextColor.GRAY))));
        v.setItem(BOOKSHELF_SLOT, this.item(Material.BOOKSHELF,
                l.choose("Poder das Estantes", "Bookshelf Power"),
                List.of(this.text(this.bookshelfPower(p) + " / " + BOOKSHELF_POWER_CAP, NamedTextColor.AQUA),
                        this.text(l.choose("Necessário para desbloquear alguns encantamentos e níveis.", "Needed to unlock some enchantments and levels."), NamedTextColor.DARK_GRAY))));
        v.setItem(GUIDE_SLOT, this.item(Material.BOOK,
                l.choose("Guia de Encantamentos", "Enchantment Guide"),
                List.of(this.text(l.choose("Clique para ver todos os encantamentos.", "Click to see every enchantment."), NamedTextColor.YELLOW))));
        this.renderMainCatalog(v, p, page);
        this.openScreen(p, v);
        this.views.put(p.getUniqueId(), new View(Type.MAIN, page, null));
    }

    /** Rebuilds just the catalog grid + scroll arrows, from whatever item is currently sitting in {@link #ITEM_SLOT} - shared by a fresh {@link #openMain} and {@link #refreshCatalog} (called live, without reopening the screen, when the item changes). */
    private void renderMainCatalog(Inventory v, Player p, int page) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        List<EnchantEntry> all = this.enchants.compatibleEntries(v.getItem(ITEM_SLOT), pt);
        for (int i = 0; i < CATALOG_SLOTS.length; i++) {
            int index = page * CATALOG_SLOTS.length + i;
            v.setItem(CATALOG_SLOTS[i], index < all.size() ? this.catalogIcon(all.get(index), l, pt) : this.filler());
        }
        v.setItem(SCROLL_UP_SLOT, page > 0 ? this.head(HeadKind.SCROLL_UP, l.choose("Página anterior", "Previous page")) : this.filler());
        v.setItem(SCROLL_DOWN_SLOT, (page + 1) * CATALOG_SLOTS.length < all.size() ? this.head(HeadKind.SCROLL_DOWN, l.choose("Próxima página", "Next page")) : this.filler());
    }

    /**
     * Turns the main screen's catalog to {@code page} in place - reuses the SAME open
     * {@link Inventory} instance and only re-renders the catalog grid/scroll arrows
     * (see {@link #renderMainCatalog}), instead of {@link #openMain}'s full rebuild
     * (a brand new {@link Inventory}, which would only ever get {@code pendingItem}'s
     * contents back into {@link #ITEM_SLOT} - empty on a plain page turn, since nothing
     * populates it for one). Previously this called {@code openMain} directly, which
     * silently discarded whatever real item was sitting on the table the moment a
     * player turned the page - same "reuse the existing instance" fix {@code
     * GrindstoneMenuService#handleNav} already gets right for its own pagination.
     */
    private void turnMainPage(Player p, int page) {
        Inventory v = p.getOpenInventory().getTopInventory();
        this.renderMainCatalog(v, p, page);
        this.views.put(p.getUniqueId(), new View(Type.MAIN, page, null));
    }

    /**
     * Called (next tick, after the click that changed it actually lands - see {@code
     * EnchantMenuListener}) whenever {@link #ITEM_SLOT} changes on the main screen:
     * re-renders the catalog from the new item without closing/reopening the
     * inventory, resetting to page 0 since the previous page might not exist for the
     * new item's (possibly much shorter) compatible list.
     */
    public void scheduleCatalogRefresh(Player p) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            View view = this.views.get(p.getUniqueId());
            if (view == null || view.type() != Type.MAIN) {
                return;
            }
            Inventory v = p.getOpenInventory().getTopInventory();
            if (v.getSize() != 54) {
                return;
            }
            this.renderMainCatalog(v, p, 0);
            this.views.put(p.getUniqueId(), new View(Type.MAIN, 0, null));
        });
    }

    /** Called by the listener when a catalog icon is clicked with an item present - grabs the item off the (about to close) main screen and switches to the level-select screen for {@code enchant}. */
    public void chooseEnchant(Player p, ItemStack item, EnchantEntry enchant) {
        this.pendingItem.put(p.getUniqueId(), item);
        this.openLevelSelect(p, enchant);
    }

    private void openLevelSelect(Player p, EnchantEntry enchant) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        Inventory v = Bukkit.createInventory(null, 54, enchant.catalogName(pt));
        this.fill(v);
        ItemStack item = this.pendingItem.get(p.getUniqueId());
        v.setItem(LEVEL_PREVIEW_SLOT, item == null ? this.item(Material.BARRIER, l.choose("Nenhum item", "No item"), List.of()) : item.clone());
        int current = item == null ? 0 : this.enchants.levelOf(item, enchant);
        for (int level = 1; level <= enchant.maxLevel() && level <= LEVEL_SLOTS.length; level++) {
            v.setItem(LEVEL_SLOTS[level - 1], this.levelIcon(p, enchant, level, current, l, pt));
        }
        v.setItem(BACK_SLOT, this.item(Material.BARRIER, l.choose("Voltar", "Back"), List.of()));
        this.openScreen(p, v);
        this.views.put(p.getUniqueId(), new View(Type.LEVEL, 0, enchant));
    }

    /** Called by the listener when a clickable (higher-than-current) level is clicked - validates, charges the XP for that level directly (no need to apply every level in between first), applies it, and returns to the main screen with the updated item. */
    public void applyLevel(Player p, EnchantEntry enchant, int level) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        ItemStack item = this.pendingItem.get(p.getUniqueId());
        if (item == null) {
            return;
        }
        int current = this.enchants.levelOf(item, enchant);
        if (level <= current) {
            return;
        }
        if (enchant instanceof VanillaEnchantEntry v) {
            String blockReason = this.enchants.vanillaBlockReason(item, v.enchantment(), pt);
            if (blockReason != null) {
                p.sendMessage(this.msg(blockReason, NamedTextColor.RED));
                return;
            }
        } else if (enchant instanceof CustomEnchantEntry c) {
            String blockReason = this.enchants.customBlockReason(item, c.enchant(), pt);
            if (blockReason != null) {
                p.sendMessage(this.msg(blockReason, NamedTextColor.RED));
                return;
            }
        }
        int requiredPower = enchant.requiredBookshelfPower(level);
        if (requiredPower > 0 && this.bookshelfPower(p) < requiredPower) {
            // No chat message here on purpose either - same reasoning as the XP check
            // right below: the level's own icon already shows this in red lore.
            return;
        }
        int cost = this.discountedCost(enchant, current, level);
        if (p.getLevel() < cost) {
            // No chat message here on purpose - the level's own icon already shows this
            // in red lore (see #levelIcon) before the player even clicks it.
            return;
        }
        EnchantService.chargeXp(p, cost);
        this.gainEnchantingXp(p, cost);
        this.enchants.setLevel(item, enchant, level, pt);
        String appliedMsg = l.choose("Aplicado: ", "Applied: ") + enchant.leveledName(pt, level);
        // Milestone XP - only for a level actually purchased through this table (never
        // a level skipped over buying straight to a higher one, and never an item
        // that arrived enchanted some other way, since #achieve only ever runs from
        // here) - see EnchantMilestoneService's own doc.
        if (this.milestones.achieve(p, enchant, level)) {
            long milestoneXp = level == enchant.maxLevel() ? MILESTONE_MAX_LEVEL_XP : MILESTONE_XP;
            this.global.addGlobalXp(p, milestoneXp, GlobalXpSource.ENCHANT_MILESTONE);
            appliedMsg += " " + l.choose("(+" + milestoneXp + " XP Global - Milestone!)", "(+" + milestoneXp + " Global XP - Milestone!)");
        }
        p.sendMessage(this.msg(appliedMsg, NamedTextColor.GREEN));
        this.openMain(p, 0);
    }

    /** Grants Enchanting skill XP for spending {@code levelsSpent} real XP levels here, using real vanilla's own curve (see {@link #ENCHANTING_XP_COEFFICIENT}) - same progress-bar/level-up/global-credit treatment {@code GeneralSkillListener#gain} gives every other skill. */
    private void gainEnchantingXp(Player p, int levelsSpent) {
        double xp = ENCHANTING_XP_COEFFICIENT * Math.pow(levelsSpent, ENCHANTING_XP_EXPONENT);
        SkillProgress before = this.general.progress(p, SkillType.ENCHANTING);
        int levelsGained = this.general.addXp(p, SkillType.ENCHANTING, xp);
        SkillProgress after = this.general.progress(p, SkillType.ENCHANTING);
        this.bars.show(p, SkillType.ENCHANTING, xp, after, this.general.maxLevel());
        if (levelsGained > 0) {
            long reward = this.global.creditSkillLevels(p, GlobalSkill.of(SkillType.ENCHANTING), before.level(), after.level());
            this.enchantingLevelUpMessage(p, before.level(), after.level(), reward);
        }
    }

    /** Same boxed multi-line style as {@code GeneralSkillListener#levelUpMessage}/{@code CombatListener#levelUpMessage}, just scoped to Enchanting's own two rewards (Intelligence, XP Orb %) since this is the only skill this class ever grants XP for. */
    private void enchantingLevelUpMessage(Player p, int before, int after, long globalXp) {
        Language l = Language.of(p);
        int gained = after - before;
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("✦ " + SkillType.ENCHANTING.name(l == Language.PT).toUpperCase(Locale.ROOT) + " " + l.choose("SUBIU DE NÍVEL!", "LEVEL UP!") + " ✦", NamedTextColor.GOLD));
        p.sendMessage(Component.text(before + " → " + after, NamedTextColor.GREEN));
        p.sendMessage(Component.text("+" + (gained * this.general.intelligencePerLevel()) + " " + l.choose("Inteligência", "Intelligence")
                + ", +" + (gained * this.general.xpOrbPercentPerLevel()) + "% " + l.choose("Orbs de XP", "XP Orbs"), NamedTextColor.AQUA));
        p.sendMessage(Component.text("+" + globalXp + " " + l.choose("XP de Nível Global", "Global Level XP"), NamedTextColor.AQUA));
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    /**
     * Opens the read-only Guide screen at {@code page} - lists every {@link
     * EnchantEntry}, filtered by {@link #guideSearch} if the player has an active
     * search (see {@code EnchantMenuListener}'s sign-based search flow). Public so the
     * listener can reopen it directly after a search is submitted.
     */
    public void openGuide(Player p, int page) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        List<EnchantEntry> all = this.filteredGuideEntries(p, pt);
        int pages = Math.max(1, (all.size() + GUIDE_CATALOG_SLOTS.length - 1) / GUIDE_CATALOG_SLOTS.length);
        page = Math.max(0, Math.min(pages - 1, page));
        String title = "(" + (page + 1) + "/" + pages + ") " + l.choose("Guia de Encantamentos", "Enchantment Guide");
        Inventory v = Bukkit.createInventory(null, 54, title);
        this.fill(v);
        for (int i = 0; i < GUIDE_CATALOG_SLOTS.length; i++) {
            int index = page * GUIDE_CATALOG_SLOTS.length + i;
            v.setItem(GUIDE_CATALOG_SLOTS[i], index < all.size() ? this.guideIcon(all.get(index), pt) : this.filler());
        }
        // KNOWLEDGE_BOOK is the green-covered book material - matches the reference image, purely decorative here.
        v.setItem(GUIDE_TITLE_SLOT, this.item(Material.KNOWLEDGE_BOOK, title, List.of()));
        v.setItem(GUIDE_PREV_PAGE_SLOT, page > 0 ? this.head(HeadKind.SCROLL_UP, l.choose("Página anterior", "Previous page")) : this.filler());
        v.setItem(GUIDE_NEXT_PAGE_SLOT, page + 1 < pages ? this.head(HeadKind.SCROLL_DOWN, l.choose("Próxima página", "Next page")) : this.filler());
        v.setItem(GUIDE_BACK_SLOT, this.customHead(HeadTexture.BACK, l.choose("Voltar ao menu", "Back to menu"), List.of()));
        v.setItem(GUIDE_CLOSE_SLOT, this.customHead(HeadTexture.CLOSE, l.choose("Fechar", "Close"), List.of()));
        String search = this.guideSearch.get(p.getUniqueId());
        List<Component> searchLore = search == null || search.isBlank()
                ? List.of(this.text(l.choose("Clique para pesquisar.", "Click to search."), NamedTextColor.YELLOW))
                : List.of(this.text(l.choose("Pesquisando: ", "Searching: ") + search, NamedTextColor.AQUA),
                        this.text(l.choose("Clique para pesquisar de novo.", "Click to search again."), NamedTextColor.YELLOW),
                        this.text(l.choose("Shift+clique para limpar.", "Shift+click to clear."), NamedTextColor.GRAY));
        v.setItem(GUIDE_SEARCH_SLOT, this.item(Material.OAK_SIGN, l.choose("Pesquisar", "Search"), searchLore));
        this.openScreen(p, v);
        this.views.put(p.getUniqueId(), new View(Type.GUIDE, page, null));
    }

    /**
     * Top-level filter screen for the Milestones list ({@link #openMilestoneList}) -
     * reached from the Enchanting skill screen in {@code /skills} ({@code
     * SkillsMenuService}), entirely independent of any real table/item (same as the
     * Guide).
     */
    public void openMilestoneCategories(Player p) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Milestones de Encantamento", "Enchantment Milestones"));
        this.fill(v);
        v.setItem(CATEGORY_WEAPONS_SLOT, this.item(Material.NETHERITE_SWORD, l.choose("Armas", "Weapons"),
                List.of(this.text(l.choose("Espada, Tridente, Maça, Arco, Besta, Lança.", "Sword, Trident, Mace, Bow, Crossbow, Spear."), NamedTextColor.GRAY))));
        v.setItem(CATEGORY_TOOLS_SLOT, this.item(Material.NETHERITE_PICKAXE, l.choose("Ferramentas", "Tools"),
                List.of(this.text(l.choose("Picareta, Machado, Pá, Enxada.", "Pickaxe, Axe, Shovel, Hoe."), NamedTextColor.GRAY))));
        v.setItem(CATEGORY_ARMOR_SLOT, this.item(Material.NETHERITE_CHESTPLATE, l.choose("Armadura", "Armor"),
                List.of(this.text(l.choose("Capacete, Peitoral, Calça, Bota, Elytra.", "Helmet, Chestplate, Leggings, Boots, Elytra."), NamedTextColor.GRAY))));
        v.setItem(CATEGORY_BACK_SLOT, this.customHead(HeadTexture.BACK, l.choose("Voltar", "Back"), List.of()));
        this.openScreen(p, v);
        this.views.put(p.getUniqueId(), new View(Type.CATEGORIES, 0, null));
    }

    /**
     * Every {@link EnchantEntry} applicable to at least one item type in {@code
     * category} (see {@link #ITEM_TYPES}) - an entry can be cross-listed under more
     * than one category (Sharpness applies to both a Sword - Weapons - and an Axe -
     * Tools). Each icon's lore shows every level colored green (achieved - see {@link
     * EnchantMilestoneService}) or red (not yet), plus which real item types it can
     * go on.
     */
    public void openMilestoneList(Player p, EnchantCategory category, int page) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        List<EnchantEntry> all = this.entriesForCategory(category, pt);
        int pages = Math.max(1, (all.size() + MILESTONE_CATALOG_SLOTS.length - 1) / MILESTONE_CATALOG_SLOTS.length);
        page = Math.max(0, Math.min(pages - 1, page));
        String categoryName = this.categoryName(category, pt);
        String title = "(" + (page + 1) + "/" + pages + ") " + categoryName;
        Inventory v = Bukkit.createInventory(null, 54, title);
        this.fill(v);
        for (int i = 0; i < MILESTONE_CATALOG_SLOTS.length; i++) {
            int index = page * MILESTONE_CATALOG_SLOTS.length + i;
            v.setItem(MILESTONE_CATALOG_SLOTS[i], index < all.size() ? this.milestoneIcon(p, all.get(index), pt) : this.filler());
        }
        v.setItem(MILESTONE_TITLE_SLOT, this.item(Material.KNOWLEDGE_BOOK, categoryName, List.of()));
        v.setItem(MILESTONE_PREV_PAGE_SLOT, page > 0 ? this.head(HeadKind.SCROLL_UP, l.choose("Página anterior", "Previous page")) : this.filler());
        v.setItem(MILESTONE_NEXT_PAGE_SLOT, page + 1 < pages ? this.head(HeadKind.SCROLL_DOWN, l.choose("Próxima página", "Next page")) : this.filler());
        v.setItem(MILESTONE_BACK_SLOT, this.customHead(HeadTexture.BACK, l.choose("Voltar às categorias", "Back to categories"), List.of()));
        this.milestoneCategory.put(p.getUniqueId(), category);
        this.openScreen(p, v);
        this.views.put(p.getUniqueId(), new View(Type.MILESTONES, page, null));
    }

    private List<EnchantEntry> entriesForCategory(EnchantCategory category, boolean pt) {
        List<EnchantEntry> result = new ArrayList<>();
        for (EnchantEntry e : this.enchants.allEntries(pt)) {
            for (ItemType t : ITEM_TYPES) {
                if (t.category() == category && this.appliesTo(e, t.material())) {
                    result.add(e);
                    break;
                }
            }
        }
        return result;
    }

    /** Every real item type (any category) {@code e} can actually be enchanted onto - same compatibility check {@code EnchantService#compatibleEntries} uses, just run against one representative Material per type instead of a real held item. */
    private List<ItemType> applicableItemTypes(EnchantEntry e) {
        List<ItemType> result = new ArrayList<>();
        for (ItemType t : ITEM_TYPES) {
            if (this.appliesTo(e, t.material())) {
                result.add(t);
            }
        }
        return result;
    }

    private boolean appliesTo(EnchantEntry e, Material material) {
        if (e instanceof VanillaEnchantEntry v) {
            return v.enchantment().canEnchantItem(ItemStack.of(material));
        }
        return e instanceof CustomEnchantEntry c && c.enchant().canApplyTo(material);
    }

    private String categoryName(EnchantCategory category, boolean pt) {
        return switch (category) {
            case WEAPONS -> pt ? "Armas" : "Weapons";
            case TOOLS -> pt ? "Ferramentas" : "Tools";
            case ARMOR -> pt ? "Armadura" : "Armor";
        };
    }

    private ItemStack milestoneIcon(Player p, EnchantEntry e, boolean pt) {
        List<Component> lore = new ArrayList<>();
        int max = e.maxLevel();
        for (int level = 1; level <= max; level++) {
            boolean achieved = this.milestones.hasAchieved(p, e, level);
            String label = (pt ? "Nível " : "Level ") + EnchantService.roman(level);
            lore.add(this.text((achieved ? "✔ " : "✘ ") + label, achieved ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        lore.add(Component.empty());
        List<ItemType> applicable = this.applicableItemTypes(e);
        List<String> names = applicable.stream().map(t -> pt ? t.pt() : t.en()).collect(java.util.stream.Collectors.toList());
        for (String line : LoreWrap.wrapList(pt ? "Aplicável em: " : "Applies to: ", names, LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(line, NamedTextColor.GRAY));
        }
        return this.enchantedBook(e.catalogName(pt), lore);
    }

    private List<EnchantEntry> filteredGuideEntries(Player p, boolean pt) {
        List<EnchantEntry> all = this.enchants.allEntries(pt);
        String query = this.guideSearch.get(p.getUniqueId());
        if (query == null || query.isBlank()) {
            return all;
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<EnchantEntry> filtered = new ArrayList<>();
        for (EnchantEntry e : all) {
            if (e.catalogName(pt).toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    /** Sets (or, if {@code query} is null/blank, clears) the Guide screen's active search for {@code p} - called by {@code EnchantMenuListener} once the sign-based search input is submitted. Doesn't reopen the screen itself. */
    public void setGuideSearch(Player p, String query) {
        if (query == null || query.isBlank()) {
            this.guideSearch.remove(p.getUniqueId());
        } else {
            this.guideSearch.put(p.getUniqueId(), query.trim());
        }
    }

    /** The catalog/level/guide slot a click on {@code rawSlot} refers to - used by {@code EnchantMenuListener} for both the main screen's and the Guide's identically-shaped-per-type grids. */
    public EnchantEntry catalogEnchantAt(Player p, Type screen, int page, int rawSlot) {
        if (screen == Type.GUIDE) {
            for (int i = 0; i < GUIDE_CATALOG_SLOTS.length; i++) {
                if (GUIDE_CATALOG_SLOTS[i] == rawSlot) {
                    List<EnchantEntry> all = this.filteredGuideEntries(p, Language.of(p) == Language.PT);
                    int index = page * GUIDE_CATALOG_SLOTS.length + i;
                    return index < all.size() ? all.get(index) : null;
                }
            }
            return null;
        }
        boolean pt = Language.of(p) == Language.PT;
        for (int i = 0; i < CATALOG_SLOTS.length; i++) {
            if (CATALOG_SLOTS[i] == rawSlot) {
                Inventory v = p.getOpenInventory().getTopInventory();
                List<EnchantEntry> all = this.enchants.compatibleEntries(v.getItem(ITEM_SLOT), pt);
                int index = page * CATALOG_SLOTS.length + i;
                return index < all.size() ? all.get(index) : null;
            }
        }
        return null;
    }

    /** The level (1-based) a level-select screen click on {@code rawSlot} refers to, or -1 if that slot isn't one of the level slots. */
    public int levelAt(int rawSlot) {
        for (int i = 0; i < LEVEL_SLOTS.length; i++) {
            if (LEVEL_SLOTS[i] == rawSlot) {
                return i + 1;
            }
        }
        return -1;
    }

    public boolean viewing(Player p) {
        return this.views.containsKey(p.getUniqueId());
    }

    public View view(Player p) {
        return this.views.get(p.getUniqueId());
    }

    /** Whether {@code p} is mid-transition between two of this menu's own screens - see {@link #transitioning}'s own doc. Checked by {@code EnchantMenuListener}'s close handler. */
    public boolean isTransitioning(Player p) {
        return this.transitioning.contains(p.getUniqueId());
    }

    /** Opens {@code v} for {@code p} while marking the implicit close of whatever screen of ours is currently open as an internal transition, not a real close - see {@link #transitioning}. */
    private void openScreen(Player p, Inventory v) {
        this.transitioning.add(p.getUniqueId());
        p.openInventory(v);
        this.transitioning.remove(p.getUniqueId());
    }

    /**
     * Same idea as {@link #openScreen}'s internal marking, exposed for {@code
     * EnchantMenuListener}'s sign-based search flow: it closes our Guide screen
     * itself (to open a real sign-editing UI, not one of our own inventories) and
     * needs that specific close treated as a transition too, not a real close.
     */
    public void markTransitioning(Player p, boolean value) {
        if (value) {
            this.transitioning.add(p.getUniqueId());
        } else {
            this.transitioning.remove(p.getUniqueId());
        }
    }

    public void close(Player p) {
        this.views.remove(p.getUniqueId());
        this.pendingItem.remove(p.getUniqueId());
        this.tableLocation.remove(p.getUniqueId());
        this.guideSearch.remove(p.getUniqueId());
        this.milestoneCategory.remove(p.getUniqueId());
    }

    /**
     * Removes and returns whatever item is being carried across screens for {@code p}
     * (see {@link #chooseEnchant}), or null if there isn't one - used by {@code
     * EnchantMenuListener} to hand it back to the player if they close the menu
     * entirely while on the level-select or guide screen (where the real item isn't
     * sitting in any real, interactive slot - only a display clone is).
     */
    public ItemStack takePendingItem(Player p) {
        return this.pendingItem.remove(p.getUniqueId());
    }

    /** Routes a click by slot/view - used by {@code EnchantMenuListener}. Returns true if it handled navigation (caller doesn't need to do anything else). */
    public boolean handleNav(Player p, int slot) {
        View v = this.views.get(p.getUniqueId());
        if (v == null) {
            return false;
        }
        switch (v.type()) {
            case MAIN -> {
                if (slot == GUIDE_SLOT) {
                    // Stash whatever's in the item slot - the Guide's own screen has no
                    // real item slot, and this MAIN inventory is about to implicitly
                    // close (see #openScreen) as openGuide opens its own.
                    ItemStack current = p.getOpenInventory().getTopInventory().getItem(ITEM_SLOT);
                    if (current != null && !current.isEmpty()) {
                        this.pendingItem.put(p.getUniqueId(), current);
                    }
                    this.openGuide(p, 0);
                    return true;
                } else if (slot == SCROLL_UP_SLOT && v.page() > 0) {
                    this.turnMainPage(p, v.page() - 1);
                    return true;
                } else if (slot == SCROLL_DOWN_SLOT) {
                    this.turnMainPage(p, v.page() + 1);
                    return true;
                }
            }
            case LEVEL -> {
                if (slot == BACK_SLOT) {
                    this.openMain(p, 0);
                    return true;
                }
            }
            case GUIDE -> {
                if (slot == GUIDE_BACK_SLOT) {
                    // Don't touch pendingItem here - openMain (when there's a real table -
                    // see #backFromStandaloneScreen) restores it into the item slot itself
                    // (removing it from the map exactly once).
                    this.guideSearch.remove(p.getUniqueId());
                    this.backFromStandaloneScreen(p);
                    return true;
                } else if (slot == GUIDE_CLOSE_SLOT) {
                    p.closeInventory();
                    return true;
                } else if (slot == GUIDE_PREV_PAGE_SLOT && v.page() > 0) {
                    this.openGuide(p, v.page() - 1);
                    return true;
                } else if (slot == GUIDE_NEXT_PAGE_SLOT) {
                    this.openGuide(p, v.page() + 1);
                    return true;
                }
                // GUIDE_SEARCH_SLOT is deliberately NOT handled here - it needs to close
                // the inventory and open a sign, which EnchantMenuListener owns.
            }
            case CATEGORIES -> {
                if (slot == CATEGORY_WEAPONS_SLOT) {
                    this.openMilestoneList(p, EnchantCategory.WEAPONS, 0);
                    return true;
                } else if (slot == CATEGORY_TOOLS_SLOT) {
                    this.openMilestoneList(p, EnchantCategory.TOOLS, 0);
                    return true;
                } else if (slot == CATEGORY_ARMOR_SLOT) {
                    this.openMilestoneList(p, EnchantCategory.ARMOR, 0);
                    return true;
                } else if (slot == CATEGORY_BACK_SLOT) {
                    this.backFromStandaloneScreen(p);
                    return true;
                }
            }
            case MILESTONES -> {
                EnchantCategory category = this.milestoneCategory.get(p.getUniqueId());
                if (slot == MILESTONE_BACK_SLOT) {
                    this.openMilestoneCategories(p);
                    return true;
                } else if (slot == MILESTONE_PREV_PAGE_SLOT && v.page() > 0 && category != null) {
                    this.openMilestoneList(p, category, v.page() - 1);
                    return true;
                } else if (slot == MILESTONE_NEXT_PAGE_SLOT && category != null) {
                    this.openMilestoneList(p, category, v.page() + 1);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Every Bookshelf on the 5x5 perimeter ring around {@code p}'s current table (all
     * 16 {@link #BOOKSHELF_DIRECTIONS} positions - not just the 4 cardinal walls and 4
     * exact diagonal corners a narrower check used to require) counts for 1 point, on
     * either the table's own floor or the one directly above it. A normally-built
     * bookshelf room (a continuous wall/ring of shelves around the table) fills most
     * or all of this ring, not just its 4 cardinal midpoints and 4 corners - checking
     * only those 8 positions meant a real room with plenty of shelves around it could
     * still cap out at a handful of points if the player never happened to place one
     * at those exact 8 spots.
     *
     * <p>No line-of-sight/air-gap requirement between the table and the shelf - a
     * shelf placed right up against the table, or with something else in front of it,
     * still counts. Matches the simple "surround the table with bookshelves" mental
     * model a player actually has, rather than a geometry rule that could silently
     * zero out a shelf for being blocked or 1 block too close.
     */
    private int bookshelfPower(Player p) {
        Location table = this.tableLocation.get(p.getUniqueId());
        if (table == null || table.getWorld() == null) {
            return 0;
        }
        World world = table.getWorld();
        int bx = table.getBlockX();
        int by = table.getBlockY();
        int bz = table.getBlockZ();
        int count = 0;
        for (int dy = 0; dy <= 1; dy++) {
            for (int[] dir : BOOKSHELF_DIRECTIONS) {
                if (world.getBlockAt(bx + dir[0], by + dy, bz + dir[1]).getType() == Material.BOOKSHELF) {
                    count++;
                }
            }
        }
        return Math.min(BOOKSHELF_POWER_CAP, count);
    }

    private ItemStack catalogIcon(EnchantEntry e, Language l, boolean pt) {
        List<Component> lore = new ArrayList<>();
        List<Component> desc = e.genericDescription(pt);
        if (!desc.isEmpty()) {
            lore.addAll(desc);
            lore.add(Component.empty());
        }
        lore.add(this.text(l.choose("Clique para escolher o nível.", "Click to choose a level."), NamedTextColor.YELLOW));
        return this.enchantedBook(e.catalogName(pt), lore);
    }

    private ItemStack guideIcon(EnchantEntry e, boolean pt) {
        List<Component> lore = new ArrayList<>(e.genericDescription(pt));
        return this.enchantedBook(e.catalogName(pt), lore);
    }

    private ItemStack levelIcon(Player p, EnchantEntry e, int level, int current, Language l, boolean pt) {
        List<Component> lore = new ArrayList<>();
        List<Component> desc = e.resolvedDescription(pt, level);
        if (!desc.isEmpty()) {
            lore.addAll(desc);
            lore.add(Component.empty());
        }
        String name = e.leveledName(pt, level);
        if (level <= current) {
            lore.add(this.text(e.costAtLevel(level) + " " + l.choose("níveis de XP", "XP levels"), NamedTextColor.DARK_AQUA));
            lore.add(this.text(l.choose("JÁ APLICADO", "ALREADY APPLIED"), NamedTextColor.GREEN));
            return this.item(Material.ENCHANTED_BOOK, name, lore);
        }
        // Any level above current is directly clickable - no need to apply every
        // level in between first (e.g. straight to V without I-IV), each charged
        // exactly the flat cost already shown for that level, same as picking it in
        // any order would. No slot limit either - an item can carry as many distinct
        // entries as you want.
        int originalCost = e.costAtLevel(level);
        int cost = this.discountedCost(e, current, level);
        if (cost != originalCost) {
            // Reapplication discount (see #discountedCost) - the more of this entry's
            // own level range already applied, the bigger the cut on everything still
            // above it, shown as the discounted price next to the original struck through.
            lore.add(this.text(originalCost + " " + l.choose("níveis de XP", "XP levels"), NamedTextColor.DARK_AQUA)
                    .decoration(TextDecoration.STRIKETHROUGH, true));
            lore.add(this.text(cost + " " + l.choose("níveis de XP", "XP levels"), NamedTextColor.GREEN));
        } else {
            lore.add(this.text(cost + " " + l.choose("níveis de XP", "XP levels"), NamedTextColor.DARK_AQUA));
        }
        int requiredPower = e.requiredBookshelfPower(level);
        if (requiredPower > 0 && this.bookshelfPower(p) < requiredPower) {
            lore.add(this.text(l.choose("Requer " + requiredPower + " de Bookshelf Power.", "Requires " + requiredPower + " Bookshelf Power."), NamedTextColor.RED));
            return this.item(Material.BOOK, name, lore);
        }
        if (p.getLevel() < cost) {
            lore.add(this.text(l.choose("XP insuficiente para aplicar.", "Not enough XP to apply."), NamedTextColor.RED));
            return this.item(Material.BOOK, name, lore);
        }
        lore.add(this.text(l.choose("Clique para aplicar!", "Click to apply!"), NamedTextColor.GOLD));
        return this.item(Material.ENCHANTED_BOOK, name, lore);
    }

    /**
     * The XP cost for {@code level}, discounted if {@code current} (the entry's
     * already-applied level, 0 if none) is above 0 - the discount is proportional to
     * how far into the entry's own level range the player already is ({@code
     * current / maxLevel}), so owning a higher level already cuts more off every
     * level still above it than owning a low one would (owning level 1 of 5 cuts
     * 20% off levels 2-5; owning level 3 of 5 cuts 60% off levels 4-5).
     */
    private int discountedCost(EnchantEntry e, int current, int level) {
        int original = e.costAtLevel(level);
        if (current <= 0) {
            return original;
        }
        double discount = (double) current / e.maxLevel();
        return (int) Math.round(original * (1.0 - discount));
    }

    private ItemStack enchantedBook(String name, List<Component> lore) {
        ItemStack item = this.item(Material.ENCHANTED_BOOK, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        return this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private void fill(Inventory v) {
        ItemStack filler = this.filler();
        for (int i = 0; i < v.getSize(); i++) {
            v.setItem(i, filler);
        }
    }

    private enum HeadKind { SCROLL_UP, SCROLL_DOWN }

    /** The Enchanting Table's own pagination heads (Netherite Arrow Up/Down) - distinct from {@link HeadTexture#ARROW_LEFT}/{@link HeadTexture#ARROW_RIGHT} used elsewhere in the plugin, per this screen's original spec. */
    private ItemStack head(HeadKind kind, String name) {
        String texture = kind == HeadKind.SCROLL_UP
                ? "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGMzMGM0YWI3ZDAwZmI1NWUzOWIxY2RkM2NiYzkzNDJiMTYyYzc2MTY2ZDIyNDk3MmRlZmJiZjllYzdmZmZhOCJ9fX0="
                : "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmNmMTBiYzEwNDg3YmVhZDY2NGY2N2I0N2U4YjVhMTcwNTQyZGNjNTc5YTRjZjdjOTFjYjc1NWYwY2FiMWU3MyJ9fX0=";
        return this.customHead(texture, name, List.of());
    }

    /** A player head wearing a custom skin (base64 "Value" texture), falling back to a plain head if it's bad. */
    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        meta.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private Component text(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private Component msg(String s, NamedTextColor c) {
        return Component.text(s, c);
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.text(name, NamedTextColor.GOLD));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    public record View(Type type, int page, EnchantEntry enchant) {
    }

    public enum Type { MAIN, LEVEL, GUIDE, CATEGORIES, MILESTONES }
}
