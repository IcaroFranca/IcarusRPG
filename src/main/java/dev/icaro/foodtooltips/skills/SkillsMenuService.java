package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import dev.icaro.foodtooltips.collections.CollectionsMenuService;
import dev.icaro.foodtooltips.crafting.CraftingMenuService;
import dev.icaro.foodtooltips.crafting.RecipeBookMenuService;
import dev.icaro.foodtooltips.enchant.EnchantMenuService;
import dev.icaro.foodtooltips.enchant.EnchantService;
import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalLevelSnapshot;
import dev.icaro.foodtooltips.global.LevelColorMenuService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.mining.MiningMenuService;
import dev.icaro.foodtooltips.reforge.ArmorReforgeStats;
import dev.icaro.foodtooltips.reforge.ReforgeService;
import dev.icaro.foodtooltips.stats.PlayerStats;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import dev.icaro.foodtooltips.travel.TravelMenuService;
import dev.icaro.foodtooltips.trash.TrashMenuService;
import dev.icaro.foodtooltips.util.LoreWrap;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class SkillsMenuService {
    private static final Key MENU_BACKGROUND_MODEL = Key.key("icarus", "menu_background");
    private static final int[] N = new int[]{9, 18, 27, 28, 29, 20, 11, 2, 3, 4, 13, 22, 31, 32, 33, 24, 15, 6, 7, 8, 17, 26, 35, 44, 53};
    private static final Map<Integer, SkillType> S = Map.of(21, SkillType.FARMING, 22, SkillType.MINING, 23, SkillType.FISHING, 24, SkillType.FORAGING, 30, SkillType.ALCHEMY, 32, SkillType.ENCHANTING);
    /** Bottom-right corner of the MAIN screen only (unused there - {@link #N} only places level nodes on this slot in the other screens). */
    private static final int TRASH_BUTTON_SLOT = 53;
    /** The MAIN screen's own "Your Bags" button - opens {@link #openBags}, which is where {@link #QUIVER_SLOT}/{@link #POTION_BAG_SLOT} actually live now (moved off MAIN once it had too many buttons crammed onto one screen). */
    private static final int BAGS_BUTTON_SLOT = 29;
    /** The BAGS screen's Potion Bag button, per the player's own spec - see {@link PotionBagService}. */
    private static final int POTION_BAG_SLOT = 28;
    /** The BAGS screen's own "coming soon" placeholder - not wired to anything yet, per the player's own "vai ficar desativada" spec. */
    private static final int ACCESSORY_BAG_SLOT = 30;
    /** The BAGS screen's own "coming soon" placeholder (one sack per skill, eventually) - not wired to anything yet, same as {@link #ACCESSORY_BAG_SLOT}. */
    private static final int SACK_OF_SACKS_SLOT = 32;
    /** The BAGS screen's Quiver button - see {@link QuiverService}. */
    private static final int QUIVER_SLOT = 34;
    /** The MAIN screen's Wardrobe button, per the player's own spec - see {@link WardrobeService}. */
    private static final int WARDROBE_SLOT = 32;
    /** The MAIN screen's Passive Abilities button - see {@link PassiveAbilityMenuService}. */
    private static final int PASSIVE_ABILITIES_SLOT = 30;
    /** The MAIN screen's Personal Storage button, per the player's own spec. */
    private static final int PERSONAL_STORAGE_SLOT = 24;
    /** The MAIN screen's Trade button, per the player's own explicit slot swap with {@link #PERSONAL_STORAGE_SLOT} - see {@code trade.TradeMenuService}. */
    private static final int TRADE_SLOT = 23;
    /** Where each general skill's summary button sits on the STATS screen (see {@link #openStats}) - same slots {@link #handleClick} reads back to know which skill was clicked. */
    private static final Map<Integer, SkillType> STATS_SKILL_SLOTS = Map.of(32, SkillType.MINING, 33, SkillType.FARMING, 41, SkillType.FISHING, 42, SkillType.FORAGING, 43, SkillType.ALCHEMY, 34, SkillType.ENCHANTING);
    /** Combat's own summary button slot on the STATS screen - the STAT_LIST equivalent of {@link #STATS_SKILL_SLOTS}, just not itself keyed by a SkillType (combat isn't a {@link SkillType}). */
    private static final int STATS_COMBAT_SLOT = 24;
    /** Grid for the STAT_LIST screens (see {@link #openStatList}) - 3 rows of 7, the same catalog shape {@code EnchantMenuService}'s Guide/Milestones screens use. Comfortably covers Combat's 16 stats, the most of any category. */
    private static final int[] STAT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final int STAT_LIST_TITLE_SLOT = 4;
    private static final int STAT_LIST_BACK_SLOT = 45;
    /** The Damage stat's own formula constant - see {@code CombatListener}'s identical constant for why (matches real Hypixel SkyBlock's own bare-hands damage, per the user's own spec). Duplicated here rather than shared since this is purely a display-facing approximation, not the real combat calculation. */
    private static final double BASE_UNARMED_DAMAGE = 5.0;
    /** The five custom melee-damage enchants {@link #damageItem} can't resolve without a real target - see {@code CombatListener#customMeleeDamagePercent}'s own doc for why each one is target-type/health-dependent. First Strike used to be listed here too, but its condition (target still at full health) is exactly what this screen's own "baseline"/first-hit framing already assumes, so it's folded straight into the resolvable enchant percent instead - see {@link #damageItem}. */
    private static final IcarusEnchant[] TARGET_DEPENDENT_ENCHANTS = {
            IcarusEnchant.CUBISM, IcarusEnchant.ENDER_SLAYER, IcarusEnchant.IMPALING,
            IcarusEnchant.EXECUTE, IcarusEnchant.GIANT_KILLER};

    private final CombatSkillService combat;
    private final GeneralSkillService general;
    private final PlayerStatsService stats;
    private final CombatAbilityService abilities;
    private final MiningMenuService mining;
    private final GlobalLevelService global;
    private final ArmorDefenseService armor;
    private final BestiaryProgressService bestiaryProgress;
    private EnchantService enchants;
    private LevelColorMenuService levelColors;
    private CombatTreeMenuService tree;
    private TravelMenuService travel;
    private CraftingMenuService crafting;
    private TrashMenuService trash;
    private EnchantMenuService enchantMenu;
    private dev.icaro.foodtooltips.potion.PotionGuideMenuService potionGuide;
    private QuiverService quiver;
    private WardrobeService wardrobe;
    private PotionBagService potionBag;
    private AccessoryBagService accessoryBag;
    private PersonalStorageService storage;
    private PassiveAbilityMenuService passiveAbilities;
    private RecipeBookMenuService recipeBook;
    private ReforgeService reforge;
    private CollectionsMenuService collections;
    private dev.icaro.foodtooltips.trade.TradeMenuService trade;
    private dev.icaro.foodtooltips.power.MagicalPowerService magicalPower;
    private final Map<UUID, View> views = new HashMap<>();

    public SkillsMenuService(CombatSkillService c, GeneralSkillService g, PlayerStatsService s, CombatAbilityService a, MiningMenuService m, GlobalLevelService global, ArmorDefenseService armor, BestiaryProgressService bestiaryProgress) {
        this.combat = c;
        this.general = g;
        this.stats = s;
        this.abilities = a;
        this.mining = m;
        this.global = global;
        this.armor = armor;
        this.bestiaryProgress = bestiaryProgress;
    }

    /** Wired after construction (same reason as {@link #levelColors}/{@link #tree}/etc. - {@code EnchantService} isn't built until after this service is, see {@code FoodTooltipsPlugin#onEnable}). Only used by {@link #damageItem} to read the held weapon's own custom melee-damage enchants. */
    public void enchants(EnchantService enchants) {
        this.enchants = enchants;
    }

    public void levelColors(LevelColorMenuService levelColors) {
        this.levelColors = levelColors;
    }

    public void tree(CombatTreeMenuService tree) {
        this.tree = tree;
    }

    public void travel(TravelMenuService travel) {
        this.travel = travel;
    }

    public void crafting(CraftingMenuService crafting) {
        this.crafting = crafting;
    }

    public void trash(TrashMenuService trash) {
        this.trash = trash;
    }

    public void enchantMenu(EnchantMenuService enchantMenu) {
        this.enchantMenu = enchantMenu;
    }

    public void potionGuide(dev.icaro.foodtooltips.potion.PotionGuideMenuService potionGuide) {
        this.potionGuide = potionGuide;
    }

    public void quiver(QuiverService quiver) {
        this.quiver = quiver;
    }

    public void wardrobe(WardrobeService wardrobe) {
        this.wardrobe = wardrobe;
    }

    public void potionBag(PotionBagService potionBag) {
        this.potionBag = potionBag;
    }

    public void accessoryBag(AccessoryBagService accessoryBag) {
        this.accessoryBag = accessoryBag;
    }

    public void storage(PersonalStorageService storage) {
        this.storage = storage;
    }

    public void passiveAbilities(PassiveAbilityMenuService passiveAbilities) {
        this.passiveAbilities = passiveAbilities;
    }

    /** Wired after construction, same reason as {@link #enchants}. Lets {@link #head}/{@link #combatStatItems} show the held weapon's own reforge bonus (Strength/Crit Chance/Crit Damage) on top of every other source. */
    public void reforge(ReforgeService reforge) {
        this.reforge = reforge;
    }

    public void recipeBook(RecipeBookMenuService recipeBook) {
        this.recipeBook = recipeBook;
    }

    public void collections(CollectionsMenuService collections) {
        this.collections = collections;
    }

    /** The MAIN screen's Trade button (slot {@value #TRADE_SLOT}, per the player's own explicit spec) - see {@link dev.icaro.foodtooltips.trade.TradeMenuService}. */
    public void trade(dev.icaro.foodtooltips.trade.TradeMenuService trade) {
        this.trade = trade;
    }

    /**
     * Wired after construction, same reason as {@link #enchants} - lets {@link #head}/{@link
     * #combatStatItems} count the Accessory Bag's selected Power. Crit Chance/Crit Damage used to
     * be recomputed here from every source EXCEPT the Power, so the stats screen never moved when
     * a player gained Magical Power even though {@code combat.CombatListener} was applying it -
     * every other Power stat was already in its total, just never named in the breakdown.
     */
    public void magicalPower(dev.icaro.foodtooltips.power.MagicalPowerService magicalPower) {
        this.magicalPower = magicalPower;
    }

    private double powerCritChance(Player p) {
        return this.magicalPower == null ? 0.0 : this.magicalPower.critChanceBonus(p);
    }

    private double powerCritDamage(Player p) {
        return this.magicalPower == null ? 0.0 : this.magicalPower.critDamageBonus(p);
    }

    /** "Power (Name) +value" breakdown line, or null (dropped by {@link #join}) when the Power grants nothing for this stat. */
    private String powerLine(Player p, double value, String formatted) {
        return this.magicalPower == null || value <= 0.0 ? null : "Power (" + this.magicalPower.effective(p).name() + ") +" + formatted;
    }

    /**
     * Bestiário and Árvore de Combate are deliberately NOT buttons here — they live only
     * on the Combat skill screen ({@link #openCombat}), reachable from the Combat icon
     * below (Bestiário is also reachable via {@code /bestiary}).
     */
    public void openMain(Player p) {
        Language l = Language.of(p);
        Inventory v = this.inv("Skills");
        v.setItem(13, this.head(p, l));
        v.setItem(19, this.item(Material.DIAMOND_SWORD, "Skills", List.of(
                this.text("Combat, Mining, Farming, Foraging,", NamedTextColor.GRAY),
                this.text("Fishing, Alchemy and Enchanting.", NamedTextColor.GRAY),
                this.click(l))));
        v.setItem(22, this.globalLevelIcon(p, l));
        if (this.levelColors != null) {
            v.setItem(45, this.item(Material.NAME_TAG, "Level Colors", List.of(this.click(l))));
        }
        if (this.travel != null) {
            v.setItem(49, this.customHead(HeadTexture.PLANET, "Locations", List.of(this.click(l))));
        }
        if (this.crafting != null) {
            v.setItem(31, this.item(Material.CRAFTING_TABLE, "Crafting Table", List.of(this.click(l))));
        }
        if (this.recipeBook != null) {
            List<Component> bookLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("See every item craftable in IcarusRPG and its recipe.", LoreWrap.DEFAULT_WIDTH)) {
                bookLore.add(this.text(part, NamedTextColor.GRAY));
            }
            bookLore.add(this.click(l));
            v.setItem(21, this.item(Material.WRITTEN_BOOK, "Recipe Book", bookLore));
        }
        if (this.collections != null) {
            List<Component> collectionsLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("See Combat, Mining, Farming, Foraging and Fishing drops, and unlock rewards by collecting each one.", LoreWrap.DEFAULT_WIDTH)) {
                collectionsLore.add(this.text(part, NamedTextColor.GRAY));
            }
            collectionsLore.add(this.click(l));
            v.setItem(20, this.customHead(HeadTexture.BUNDLE, "Collections", collectionsLore));
        }
        if (this.trash != null) {
            v.setItem(TRASH_BUTTON_SLOT, this.customHead(HeadTexture.TRASH_CAN, "Trash Can", List.of(this.click(l))));
        }
        if (this.passiveAbilities != null) {
            List<Component> passiveLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("Turn passive abilities like Telekinesis on or off, separately for mob drops and block drops.", LoreWrap.DEFAULT_WIDTH)) {
                passiveLore.add(this.text(part, NamedTextColor.GRAY));
            }
            passiveLore.add(this.click(l));
            v.setItem(PASSIVE_ABILITIES_SLOT, this.customHead(HeadTexture.SUPER_MUSHROOM, "Passive Abilities", passiveLore));
        }
        if ((this.quiver != null && this.quiver.unlocked(p)) || (this.potionBag != null && this.potionBag.unlocked(p))) {
            List<Component> bagsLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("Quiver and Potion Bag, in one place.", LoreWrap.DEFAULT_WIDTH)) {
                bagsLore.add(this.text(part, NamedTextColor.GRAY));
            }
            bagsLore.add(this.click(l));
            v.setItem(BAGS_BUTTON_SLOT, this.customHead(HeadTexture.QUIVER, "Your Bags", bagsLore));
        }
        if (this.wardrobe != null && this.wardrobe.unlocked(p)) {
            List<Component> wardrobeLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("Store armor sets and equip them instantly.", LoreWrap.DEFAULT_WIDTH)) {
                wardrobeLore.add(this.text(part, NamedTextColor.GRAY));
            }
            wardrobeLore.add(this.text(this.wardrobe.columns(p) + "/" + WardrobeService.COLUMNS + " " + "columns", NamedTextColor.GOLD));
            wardrobeLore.add(this.click(l));
            v.setItem(WARDROBE_SLOT, this.wardrobeIcon(wardrobeLore));
        }
        if (this.trade != null) {
            List<Component> tradeLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("Trade Collection items for others you can't farm or mine directly.", LoreWrap.DEFAULT_WIDTH)) {
                tradeLore.add(this.text(part, NamedTextColor.GRAY));
            }
            tradeLore.add(this.click(l));
            v.setItem(TRADE_SLOT, this.item(Material.EMERALD, "Trade", tradeLore));
        }
        if (this.storage != null && this.storage.unlocked(p)) {
            List<Component> storageLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("Store any item separately from your inventory.", LoreWrap.DEFAULT_WIDTH)) {
                storageLore.add(this.text(part, NamedTextColor.GRAY));
            }
            storageLore.add(this.text(this.storage.storageSize(p) + "/" + PersonalStorageService.MAX_SLOTS + " " + "slots", NamedTextColor.GOLD));
            storageLore.add(this.click(l));
            v.setItem(PERSONAL_STORAGE_SLOT, this.item(Material.ENDER_CHEST, "Personal Storage", storageLore));
        }
        this.open(p, v, new View(Type.MAIN, 0, null));
    }

    /**
     * The Quiver and Potion Bag buttons, previously directly on the MAIN screen -
     * consolidated into their own screen (reached from MAIN's own "Your Bags" button, slot
     * {@value #BAGS_BUTTON_SLOT}) once the main menu had too many buttons crammed onto one
     * screen. Also shows the Accessory Bag and Sack of Sacks (one sack per skill, eventually)
     * as inert "coming soon" placeholders - per the player's own explicit "vai ficar
     * desativada" spec, neither is wired to anything yet ({@link #handleClick}'s own BAGS
     * case never checks {@link #ACCESSORY_BAG_SLOT}/{@link #SACK_OF_SACKS_SLOT}, so a click
     * on either is simply a no-op).
     */
    public void openBags(Player p) {
        Language l = Language.of(p);
        Inventory v = this.inv("Your Bags");
        if (this.quiver != null && this.quiver.unlocked(p)) {
            List<Component> quiverLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("The bow pulls arrows from here directly, without needing to keep them in your inventory.", LoreWrap.DEFAULT_WIDTH)) {
                quiverLore.add(this.text(part, NamedTextColor.GRAY));
            }
            quiverLore.add(this.click(l));
            v.setItem(QUIVER_SLOT, this.customHead(HeadTexture.QUIVER, this.quiver.displayName(p, l), quiverLore));
        }
        if (this.potionBag != null && this.potionBag.unlocked(p)) {
            List<Component> potionBagLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("Store potions, XP bottles and water bottles separately from your inventory.", LoreWrap.DEFAULT_WIDTH)) {
                potionBagLore.add(this.text(part, NamedTextColor.GRAY));
            }
            potionBagLore.add(this.text(this.potionBag.storageSize(p) + "/" + PotionBagService.MAX_SLOTS + " " + "slots", NamedTextColor.GOLD));
            potionBagLore.add(this.click(l));
            v.setItem(POTION_BAG_SLOT, this.customHead(HeadTexture.POTION_BAG, "Potion Bag", potionBagLore));
        }
        if (this.accessoryBag != null && this.accessoryBag.unlocked(p)) {
            List<Component> accessoryLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("Store several accessories (Talismans, Rings, Artifacts) in here.", LoreWrap.DEFAULT_WIDTH)) {
                accessoryLore.add(this.text(part, NamedTextColor.GRAY));
            }
            accessoryLore.add(this.text(AccessoryBagService.STORAGE_SIZE + " " + "slots", NamedTextColor.GOLD));
            accessoryLore.add(this.click(l));
            v.setItem(ACCESSORY_BAG_SLOT, this.item(Material.BUNDLE, "Accessory Bag", accessoryLore));
        } else {
            v.setItem(ACCESSORY_BAG_SLOT, this.comingSoon(l, Material.BUNDLE, "Accessory Bag", "Store accessories separately from your inventory."));
        }
        v.setItem(SACK_OF_SACKS_SLOT, this.comingSoon(l, Material.BARREL, "Sack of Sacks", "One sack per skill, all stored inside here."));
        v.setItem(49, this.customHead(HeadTexture.BACK, "Back to skills", List.of()));
        this.open(p, v, new View(Type.BAGS, 0, null));
    }

    /** A greyed-out, unclickable placeholder for a feature that doesn't exist yet - same "locked" visual idiom {@link PassiveAbilityMenuService#toggleItem} already uses for a not-yet-unlocked toggle (dark grey name, {@code ✖} prefix), just for "not built yet" instead of "not unlocked yet". */
    private ItemStack comingSoon(Language l, Material icon, String name, String description) {
        List<Component> lore = new ArrayList<>();
        for (String part : LoreWrap.wrapText(description, LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(this.text("Coming soon.", NamedTextColor.DARK_GRAY));
        ItemStack item = ItemStack.of(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.text("✖ " + name, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /** The Combat button plus every {@link #S} entry, previously scattered directly on the MAIN screen - consolidated into their own screen (reached from MAIN's own Skills button, slot 19) once the main menu had too many buttons crammed onto one screen. */
    public void openSkillsList(Player p) {
        Language l = Language.of(p);
        Inventory v = this.inv("Skill Types");
        v.setItem(20, this.item(Material.IRON_SWORD, "Combat", List.of(this.combatLine(p, l), this.click(l))));
        for (Map.Entry<Integer, SkillType> e : S.entrySet()) {
            SkillType t = e.getValue();
            v.setItem(e.getKey(), this.item(t.icon(), t.name(l == Language.PT), List.of(this.skillLine(p, t, l), this.click(l))));
        }
        v.setItem(49, this.customHead(HeadTexture.BACK, "Back to skills", List.of()));
        this.open(p, v, new View(Type.SKILLS_LIST, 0, null));
    }

    public void openCombat(Player p, int page) {
        page = this.clamp(page, this.combat.maxLevel());
        Language l = Language.of(p);
        Inventory v = this.inv("Combat Skill");
        CombatProgress x = this.combat.progress(p);
        for (int i = 0; i < 25; ++i) {
            int level = page * 25 + i + 1;
            ArrayList<Component> lore = new ArrayList<>(List.of(this.text("+0.5% " + "Crit Chance", NamedTextColor.AQUA), this.text("+4% " + "Damage", NamedTextColor.RED)));
            double speedGain = this.combat.attackSpeed(level) - this.combat.attackSpeed(level - 1);
            if (speedGain > 1.0E-4) {
                lore.add(this.text("+" + String.format(Locale.US, "%.2f", speedGain) + " " + "Attack Speed", NamedTextColor.YELLOW));
            }
            if (level == x.level() + 1) {
                lore.add(this.xp(x.xp(), x.requiredXp()));
            }
            v.setItem(N[i], this.node(level, x.level(), l, lore));
        }
        v.setItem(0, this.item(Material.IRON_SWORD, "Combat Progression", List.of(this.combatLine(p, l))));
        v.setItem(39, this.item(Material.KNOWLEDGE_BOOK, "Bestiary", List.of(this.click(l))));
        v.setItem(41, this.item(Material.CHEST, "Combat Tree", List.of(this.click(l))));
        this.nav(v, l, page, this.combat.maxLevel());
        this.open(p, v, new View(Type.COMBAT, page, null));
    }

    public void openGeneral(Player p, SkillType t, int page) {
        page = this.clamp(page, this.general.maxLevel());
        Language l = Language.of(p);
        Inventory v = this.inv(t.name(l == Language.PT));
        SkillProgress x = this.general.progress(p, t);
        for (int i = 0; i < 25; ++i) {
            int level = page * 25 + i + 1;
            ArrayList<Component> lore = new ArrayList<>(List.of(this.text("Keep using this skill to level up.", NamedTextColor.GRAY)));
            lore.addAll(this.attributeRewardLines(t, l));
            List<String> enchantUnlocks = t == SkillType.ENCHANTING && this.enchantMenu != null ? this.enchantMenu.enchantingUnlocksAtLevel(level, l == Language.PT) : List.of();
            if (t == SkillType.MINING && level == 3) {
                lore.add(this.text("✦ " + "Unlocks: Vein Miner", NamedTextColor.LIGHT_PURPLE));
            } else if (!enchantUnlocks.isEmpty()) {
                for (String name : enchantUnlocks) {
                    lore.add(this.text("✦ " + "Unlocks enchantment: " + name, NamedTextColor.LIGHT_PURPLE));
                }
            } else {
                lore.add(this.text("No ability at this level.", NamedTextColor.DARK_GRAY));
            }
            if (level == x.level() + 1) {
                lore.add(this.xp(x.xp(), x.requiredXp()));
            }
            v.setItem(N[i], this.node(level, x.level(), l, lore));
        }
        v.setItem(0, this.item(t.icon(), "Progression: " + t.name(l == Language.PT), List.of(this.skillLine(p, t, l))));
        if (t == SkillType.MINING) {
            List<Component> compendiumLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("Counters, milestones, XP, drops and layers.", LoreWrap.DEFAULT_WIDTH)) {
                compendiumLore.add(this.text(part, NamedTextColor.YELLOW));
            }
            v.setItem(40, this.item(Material.BOOK, "Mining Compendium", compendiumLore));
        } else if (t == SkillType.ENCHANTING && this.enchantMenu != null) {
            List<Component> milestonesLore = new ArrayList<>();
            for (String part : LoreWrap.wrapText("Progress per enchantment, filtered by Weapons/Tools/Armor.", LoreWrap.DEFAULT_WIDTH)) {
                milestonesLore.add(this.text(part, NamedTextColor.YELLOW));
            }
            for (String part : LoreWrap.wrapText("Only counts what was applied at the Enchanting Table.", LoreWrap.DEFAULT_WIDTH)) {
                milestonesLore.add(this.text(part, NamedTextColor.GRAY));
            }
            v.setItem(39, this.item(Material.ENCHANTED_BOOK, "Enchantment Milestones", milestonesLore));
            v.setItem(41, this.item(Material.BOOK, "Enchantment Guide", List.of(this.text("See every enchantment available.", NamedTextColor.YELLOW))));
        } else if (t == SkillType.ALCHEMY && this.potionGuide != null) {
            v.setItem(39, this.item(Material.BREWING_STAND, "Potion Milestones",
                    List.of(this.text("Which potions you've already brewed.", NamedTextColor.YELLOW))));
            v.setItem(41, this.item(Material.BOOK, "Potion Guide",
                    List.of(this.text("See how to brew every potion in the game.", NamedTextColor.YELLOW))));
        }
        this.nav(v, l, page, this.general.maxLevel());
        this.open(p, v, new View(Type.GENERAL, page, t));
    }

    /** Per-level attribute rewards for a general skill (see {@link GeneralSkillService#fortune}, {@code bonusHealth}, {@code bonusStrength}, {@code bonusIntelligence}) - a skill can grant more than one. */
    private List<Component> attributeRewardLines(SkillType t, Language l) {
        List<Component> lines = new ArrayList<>();
        if (t == SkillType.MINING || t == SkillType.FARMING || t == SkillType.FORAGING) {
            lines.add(this.text("+" + this.general.fortunePerLevel() + " " + t.name(l == Language.PT) + " Fortune", NamedTextColor.AQUA));
        }
        switch (t) {
            case MINING -> lines.add(this.text("+" + this.general.defensePerLevel() + " " + "Defense", NamedTextColor.GREEN));
            case FARMING, FISHING -> lines.add(this.text("+" + this.general.healthPerLevel() + " " + "Max Health", NamedTextColor.RED));
            case FORAGING -> lines.add(this.text("+" + this.general.strengthPerLevel() + " " + "Strength", NamedTextColor.YELLOW));
            case ALCHEMY, ENCHANTING -> lines.add(this.text("+" + this.general.intelligencePerLevel() + " " + "Intelligence", NamedTextColor.LIGHT_PURPLE));
            default -> {}
        }
        if (t == SkillType.ENCHANTING) {
            lines.add(this.text("+" + this.general.xpOrbPercentPerLevel() + "% " + "XP Orbs", NamedTextColor.AQUA));
        }
        if (t == SkillType.ALCHEMY) {
            lines.add(this.text("+" + this.general.potionDurationPercentPerLevel() + "% " + "Potion Duration", NamedTextColor.DARK_PURPLE));
        }
        if (lines.isEmpty()) {
            for (String part : LoreWrap.wrapText("No attribute reward at this level.", LoreWrap.DEFAULT_WIDTH)) {
                lines.add(this.text(part, NamedTextColor.AQUA));
            }
        }
        return lines;
    }

    public void openGlobal(Player p, int page) {
        int maxLevel = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, this.global.maxAchievableLevel()));
        page = this.clamp(page, maxLevel);
        Language l = Language.of(p);
        Inventory v = this.inv("Global Level");
        GlobalLevelSnapshot g = this.global.snapshot(p);
        int currentLevel = (int) Math.min(Integer.MAX_VALUE, g.level());
        int levelsPerStrength = Math.max(1, this.global.levelsPerStrength());
        for (int i = 0; i < 25; ++i) {
            int level = page * 25 + i + 1;
            ArrayList<Component> lore = new ArrayList<>(List.of(
                    this.text("❤ +" + Math.round(this.global.hpPerLevel()) + " " + "max HP", NamedTextColor.RED)));
            if (level % levelsPerStrength == 0) {
                lore.add(this.text("✹ +" + this.global.strengthPerGroup() + " Strength", NamedTextColor.GOLD));
            }
            if (level == this.global.telekinesisRequiredLevel()) {
                lore.add(this.text("🧲 " + "Unlocks: Telekinesis", NamedTextColor.LIGHT_PURPLE));
            }
            if (lore.size() == 1) {
                lore.add(this.text("No other reward at this level.", NamedTextColor.DARK_GRAY));
            }
            if (level == currentLevel + 1) {
                lore.add(this.text(g.progress() + "/" + g.required() + " XP", NamedTextColor.GREEN));
            }
            v.setItem(N[i], this.node(level, currentLevel, l, lore));
        }
        v.setItem(0, this.item(Material.NETHER_STAR, "Global Level Progression", List.of(this.globalLine(p, l))));
        this.nav(v, l, page, maxLevel);
        this.open(p, v, new View(Type.GLOBAL, page, null));
    }

    public boolean handleClick(Player p, int slot) {
        View v = this.views.get(p.getUniqueId());
        if (v == null) {
            return false;
        }
        switch (v.type()) {
            case MAIN -> {
                if (slot == 13) {
                    this.openStats(p, p);
                } else if (slot == 22) {
                    this.openGlobal(p, 0);
                } else if (slot == 19) {
                    this.openSkillsList(p);
                } else if (slot == 45 && this.levelColors != null) {
                    this.views.remove(p.getUniqueId());
                    this.levelColors.open(p);
                } else if (slot == 49 && this.travel != null) {
                    this.views.remove(p.getUniqueId());
                    this.travel.open(p);
                } else if (slot == 31 && this.crafting != null) {
                    this.views.remove(p.getUniqueId());
                    this.crafting.open(p);
                } else if (slot == 21 && this.recipeBook != null) {
                    this.views.remove(p.getUniqueId());
                    this.recipeBook.open(p);
                } else if (slot == 20 && this.collections != null) {
                    this.views.remove(p.getUniqueId());
                    this.collections.openCategories(p);
                } else if (slot == TRASH_BUTTON_SLOT && this.trash != null) {
                    this.views.remove(p.getUniqueId());
                    this.trash.open(p);
                } else if (slot == BAGS_BUTTON_SLOT
                        && ((this.quiver != null && this.quiver.unlocked(p)) || (this.potionBag != null && this.potionBag.unlocked(p)))) {
                    this.openBags(p);
                } else if (slot == WARDROBE_SLOT && this.wardrobe != null && this.wardrobe.unlocked(p)) {
                    this.views.remove(p.getUniqueId());
                    this.wardrobe.open(p);
                } else if (slot == PERSONAL_STORAGE_SLOT && this.storage != null && this.storage.unlocked(p)) {
                    this.views.remove(p.getUniqueId());
                    this.storage.open(p);
                } else if (slot == PASSIVE_ABILITIES_SLOT && this.passiveAbilities != null) {
                    this.views.remove(p.getUniqueId());
                    this.passiveAbilities.open(p);
                } else if (slot == TRADE_SLOT && this.trade != null) {
                    this.views.remove(p.getUniqueId());
                    this.trade.open(p);
                }
            }
            case SKILLS_LIST -> {
                if (slot == 49) {
                    this.openMain(p);
                } else if (slot == 20) {
                    this.openCombat(p, 0);
                } else if (S.containsKey(slot)) {
                    this.openGeneral(p, S.get(slot), 0);
                }
            }
            case BAGS -> {
                if (slot == 49) {
                    this.openMain(p);
                } else if (slot == QUIVER_SLOT && this.quiver != null && this.quiver.unlocked(p)) {
                    this.views.remove(p.getUniqueId());
                    this.quiver.open(p);
                } else if (slot == POTION_BAG_SLOT && this.potionBag != null && this.potionBag.unlocked(p)) {
                    this.views.remove(p.getUniqueId());
                    this.potionBag.open(p);
                } else if (slot == ACCESSORY_BAG_SLOT && this.accessoryBag != null && this.accessoryBag.unlocked(p)) {
                    this.views.remove(p.getUniqueId());
                    this.accessoryBag.open(p);
                }
            }
            case GLOBAL -> {
                if (slot == 45) {
                    this.openMain(p);
                } else if (slot == 48 && v.page() > 0) {
                    this.openGlobal(p, v.page() - 1);
                } else if (slot == 50) {
                    this.openGlobal(p, v.page() + 1);
                }
            }
            case STATS -> {
                if (slot == 49) {
                    this.openMain(p);
                } else if (slot == STATS_COMBAT_SLOT) {
                    Player target = this.resolveTarget(p, v);
                    if (target != null) {
                        this.openStatList(p, target, null);
                    }
                } else if (STATS_SKILL_SLOTS.containsKey(slot)) {
                    Player target = this.resolveTarget(p, v);
                    if (target != null) {
                        this.openStatList(p, target, STATS_SKILL_SLOTS.get(slot));
                    }
                }
            }
            case STAT_LIST -> {
                if (slot == STAT_LIST_BACK_SLOT) {
                    Player target = this.resolveTarget(p, v);
                    if (target != null) {
                        this.openStats(p, target);
                    } else {
                        this.openMain(p);
                    }
                }
            }
            case COMBAT -> {
                if (slot == 45) {
                    this.openMain(p);
                } else if (slot == 39) {
                    p.performCommand("bestiary");
                } else if (slot == 41 && this.tree != null) {
                    this.views.remove(p.getUniqueId());
                    this.tree.open(p);
                } else if (slot == 48 && v.page() > 0) {
                    this.openCombat(p, v.page() - 1);
                } else if (slot == 50) {
                    this.openCombat(p, v.page() + 1);
                }
            }
            case GENERAL -> {
                if (slot == 45) {
                    this.openMain(p);
                } else if (slot == 40 && v.skill() == SkillType.MINING) {
                    this.views.remove(p.getUniqueId());
                    this.mining.open(p);
                } else if (slot == 41 && v.skill() == SkillType.ENCHANTING && this.enchantMenu != null) {
                    this.views.remove(p.getUniqueId());
                    this.enchantMenu.openGuide(p, 0);
                } else if (slot == 39 && v.skill() == SkillType.ENCHANTING && this.enchantMenu != null) {
                    this.views.remove(p.getUniqueId());
                    this.enchantMenu.openMilestoneCategories(p);
                } else if (slot == 41 && v.skill() == SkillType.ALCHEMY && this.potionGuide != null) {
                    this.views.remove(p.getUniqueId());
                    this.potionGuide.openGuide(p, 0);
                } else if (slot == 39 && v.skill() == SkillType.ALCHEMY && this.potionGuide != null) {
                    this.views.remove(p.getUniqueId());
                    this.potionGuide.openMilestones(p, 0);
                } else if (slot == 48 && v.page() > 0) {
                    this.openGeneral(p, v.skill(), v.page() - 1);
                } else if (slot == 50) {
                    this.openGeneral(p, v.skill(), v.page() + 1);
                }
            }
        }
        return true;
    }

    public boolean viewing(Player p) {
        return this.views.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        this.views.remove(p.getUniqueId());
    }

    private void open(Player p, Inventory v, View view) {
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.views.put(p.getUniqueId(), view);
    }

    private Inventory inv(String title) {
        Inventory v = Bukkit.createInventory(null, 54, title);
        ItemStack f = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        f.setData(DataComponentTypes.ITEM_MODEL, MENU_BACKGROUND_MODEL);
        for (int i = 0; i < 54; ++i) {
            v.setItem(i, f);
        }
        return v;
    }

    private void nav(Inventory v, Language l, int page, int max) {
        v.setItem(45, this.customHead(HeadTexture.BACK, "Back to skills", List.of()));
        if (page > 0) {
            v.setItem(48, this.customHead(HeadTexture.ARROW_LEFT, "Previous page", List.of()));
        }
        if ((page + 1) * 25 < max) {
            v.setItem(50, this.customHead(HeadTexture.ARROW_RIGHT, "Next page", List.of()));
        }
    }

    private int clamp(int p, int max) {
        return Math.max(0, Math.min((max - 1) / 25, p));
    }

    private ItemStack node(int n, int current, Language l, List<Component> lore) {
        ItemStack i = this.item(n <= current ? Material.LIME_STAINED_GLASS_PANE : (n == current + 1 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE), "Level " + n, lore);
        i.setAmount(Math.min(64, n));
        return i;
    }

    private Component combatLine(Player p, Language l) {
        CombatProgress x = this.combat.progress(p);
        return this.text("Level " + x.level() + (x.level() >= this.combat.maxLevel() ? " (MAX)" : " • " + Math.round(x.xp()) + "/" + Math.round(x.requiredXp()) + " XP"), NamedTextColor.GREEN);
    }

    private Component globalLine(Player p, Language l) {
        GlobalLevelSnapshot g = this.global.snapshot(p);
        return this.text("Level " + g.level() + " • " + g.progress() + "/" + g.required() + " XP", NamedTextColor.GOLD);
    }

    /** Global Level's button on the main skills grid — a custom head if one is configured, otherwise an XP bottle. */
    private ItemStack globalLevelIcon(Player p, Language l) {
        List<Component> lore = List.of(this.globalLine(p, l), this.click(l));
        String texture = this.global.iconTexture();
        if (texture != null && !texture.isBlank()) {
            return this.customHead(texture, "Global Level", lore);
        }
        return this.item(Material.EXPERIENCE_BOTTLE, "Global Level", lore);
    }

    /** A player head wearing a custom skin (base64 "Value" texture), falling back to a plain head if it's bad. */
    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the whole menu.
        }
        m.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    /** {@link WardrobeService#menuIcon} (a dyed leather chestplate, per the player's own spec - not a custom head like every other button here) plus this button's own lore. */
    private ItemStack wardrobeIcon(List<Component> lore) {
        ItemStack i = WardrobeService.menuIcon();
        ItemMeta m = i.getItemMeta();
        m.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        i.setItemMeta(m);
        return i;
    }

    private Component skillLine(Player p, SkillType t, Language l) {
        SkillProgress x = this.general.progress(p, t);
        return this.text("Level " + x.level() + (x.level() >= this.general.maxLevel() ? " (MAX)" : " • " + Math.round(x.xp()) + "/" + Math.round(x.requiredXp()) + " XP"), NamedTextColor.GREEN);
    }

    private Component xp(double a, double b) {
        return this.text(Math.round(a) + "/" + Math.round(b) + " XP", NamedTextColor.GREEN);
    }

    private Component click(Language l) {
        return this.text("Click to view!", NamedTextColor.YELLOW);
    }

    /** Condensed hover preview (the head icon in the main menu) — click it to open {@link #openStats}. */
    private ItemStack head(Player p, Language l) {
        PlayerStats s = this.stats.stats(p);
        CombatProgress c = this.combat.progress(p);
        int defense = this.armor.defense(p);
        long speedPercent = Math.round(this.value(p, Attribute.MOVEMENT_SPEED, 0.1) / 0.1 * 100.0);
        ItemStack mainHand = p.getInventory().getItemInMainHand();
        double reforgeCritChance = this.reforge == null ? 0.0
                : this.reforge.statsOf(mainHand).critChance() + this.reforge.bowStatsOf(mainHand).critChance() + this.reforge.totalArmorStats(p).critChance();
        double reforgeCritDamage = this.reforge == null ? 0.0
                : this.reforge.statsOf(mainHand).critDamage() + this.reforge.bowStatsOf(mainHand).critDamage() + this.reforge.totalArmorStats(p).critDamage();
        double reforgeStrength = this.reforge == null ? 0.0
                : this.reforge.statsOf(mainHand).strength() + this.reforge.bowStatsOf(mainHand).strength() + this.reforge.totalArmorStats(p).strength();
        double critDamage = (this.abilities.criticalDamageMultiplier(p) - 1.0) * 100.0 + reforgeCritDamage + this.powerCritDamage(p);
        double critChance = Math.min(100.0, this.combat.critChance(c.level()) + this.abilities.critChanceBonus(p) + reforgeCritChance + this.powerCritChance(p));
        List<Component> lore = List.of(
                this.text("View your equipment, stats, and more!", NamedTextColor.GRAY),
                Component.empty(),
                this.text("🏃 " + "Speed: " + speedPercent, NamedTextColor.WHITE),
                this.text("🐇 " + "Agility: " + Math.round(this.stats.effectiveAgility(p)), NamedTextColor.WHITE),
                this.text("✹ Strength: " + Math.round(s.strength() + reforgeStrength), NamedTextColor.RED),
                this.text("✦ " + "Defense: " + defense, NamedTextColor.GREEN),
                this.text("☠ " + "Crit Damage: " + String.format(Locale.US, "%.1f", critDamage) + "%", NamedTextColor.BLUE),
                this.text("☣ " + "Crit Chance: " + String.format(Locale.US, "%.1f", critChance) + "%", NamedTextColor.BLUE),
                this.text("❤ " + "Health: " + Math.round(s.health()) + "/" + Math.round(s.maxHealth()), NamedTextColor.RED),
                this.text("✎ " + "Intelligence: " + Math.round(s.intelligence()), NamedTextColor.AQUA),
                Component.empty(),
                this.text("Click to see more!", NamedTextColor.YELLOW));
        ItemStack i = this.item(Material.PLAYER_HEAD, "Stats & Equipment", lore);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        m.setOwningPlayer((OfflinePlayer) p);
        i.setItemMeta(m);
        return i;
    }

    /**
     * Full breakdown (grouped by icon) plus equipped armor - {@code target}'s (whose
     * stats/gear every line reads), shown to {@code viewer} (whose language it's built
     * in, and who the screen actually opens for). Same screen either way: your own
     * (opened by clicking {@link #head} in the main menu, {@code viewer == target}) or
     * another online player's (opened by right-clicking them - see {@code
     * PlayerStatsViewListener} - read-only, there's no way to reach it for an offline
     * player since every stat here is read live off their actual Player object).
     */
    public void openStats(Player viewer, Player target) {
        Language l = Language.of(viewer);
        Inventory v = this.inv(target.getName() + " - " + "Stats & Equipment");
        v.setItem(4, this.statsOverviewHead(target, l));
        v.setItem(2, this.armorSlot(target.getInventory().getItemInMainHand(), "Held Item", l));
        v.setItem(11, this.armorSlot(target.getInventory().getHelmet(), "Helmet", l));
        v.setItem(20, this.armorSlot(target.getInventory().getChestplate(), "Chestplate", l));
        v.setItem(29, this.armorSlot(target.getInventory().getLeggings(), "Leggings", l));
        v.setItem(38, this.armorSlot(target.getInventory().getBoots(), "Boots", l));
        v.setItem(STATS_COMBAT_SLOT, this.combatStatsItem(target, l));
        for (Map.Entry<Integer, SkillType> e : STATS_SKILL_SLOTS.entrySet()) {
            v.setItem(e.getKey(), this.skillBonusItem(target, e.getValue(), l));
        }
        v.setItem(49, this.customHead(HeadTexture.BACK, "Back to skills", List.of()));
        this.open(viewer, v, new View(Type.STATS, 0, null, target.getUniqueId()));
    }

    /** {@code v.target()} resolved back to an online {@link Player}, or {@code viewer} itself if that field is absent - never null unless the target logged off since the screen opened. */
    private Player resolveTarget(Player viewer, View v) {
        return v.target() == null ? viewer : Bukkit.getPlayer(v.target());
    }

    private ItemStack statsOverviewHead(Player p, Language l) {
        GlobalLevelSnapshot g = this.global.snapshot(p);
        List<Component> lore = List.of(this.text("✦ " + "Global Level: " + g.level(), NamedTextColor.GOLD));
        ItemStack i = this.item(Material.PLAYER_HEAD, p.getName(), lore);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        m.setOwningPlayer((OfflinePlayer) p);
        i.setItemMeta(m);
        return i;
    }

    /** The player's actual equipped piece (real item, with its own name/enchants/lore), or an empty placeholder. */
    private ItemStack armorSlot(ItemStack equipped, String slotName, Language l) {
        if (equipped == null || equipped.getType().isAir()) {
            return this.item(Material.GRAY_STAINED_GLASS_PANE, slotName, List.of(this.text("Nothing equipped.", NamedTextColor.DARK_GRAY)));
        }
        return equipped.clone();
    }

    /** One "what it does / how to get more" pair for a stat's detail item (see {@link #statItem}) - the current value and its breakdown are computed live per-player by the caller, only this descriptive text is fixed per stat. */
    private record StatInfo(String whatPt, String whatEn, String howPt, String howEn) {
        String what(boolean pt) {
            return this.whatEn;
        }

        String how(boolean pt) {
            return this.howEn;
        }
    }

    // ---- Combat Stats' own StatInfo, one per line of the old combatStatsItem lore ----
    private static final StatInfo HEALTH_INFO = new StatInfo(
            "Quanto dano você aguenta antes de morrer. Regenera sozinha com o tempo (veja Regen. de Vida) e ao ser curada.",
            "How much damage you can take before dying. Regenerates over time (see Health Regen) and when healed.",
            "Sobe com o Nível Global, milestones do Bestiário, e as skills de Agricultura e Pesca.",
            "Increases with Global Level, Bestiary milestones, and the Farming and Fishing skills.");
    private static final StatInfo DEFENSE_INFO = new StatInfo(
            "Reduz o dano que você recebe: mitigação de defesa/(defesa+100). O vanilla nativo (armadura crua) é zerado - só esse stat conta.",
            "Reduces damage you take: defense/(defense+100) mitigation. Vanilla's own armor value is zeroed out - only this stat matters.",
            "Vem só do equipamento: peças de armadura (Couro a Netherite) e o encantamento Protection (+4/nível, até nível V). A skill de Mineração soma um bônus fixo por nível também.",
            "Comes only from gear: armor pieces (Leather to Netherite) and the Protection enchantment (+4/level, up to level V). The Mining skill also adds a flat bonus per level.");
    private static final StatInfo TRUE_DEFENSE_INFO = new StatInfo(
            "Redução de dano fixa (não percentual), aplicada depois da Defesa normal - não é afetada por nenhum efeito.",
            "Flat (not percentage-based) damage reduction, applied after normal Defense - unaffected by any other effect.",
            "Hoje é só um valor base fixo (config do servidor) - nenhuma fonte em jogo aumenta ainda.",
            "Currently just a fixed base value (server config) - nothing in-game raises it yet.");
    private static final StatInfo STRENGTH_INFO = new StatInfo(
            "% de dano extra em todo golpe (PvE e PvP), multiplicativo sobre o dano final.",
            "% extra damage on every hit (PvE and PvP alike), multiplicative on top of your final damage.",
            "Sobe com o Nível Global (a cada alguns níveis) e com a skill de Coleta.",
            "Increases with Global Level (every few levels) and the Foraging skill.");
    private static final StatInfo CRIT_CHANCE_INFO = new StatInfo(
            "Chance por golpe de acertar um Crítico (multiplica o dano por Dano Crítico) em vez de dano normal.",
            "Chance per hit to land a Critical (multiplies damage by Crit Damage) instead of a normal hit.",
            "Base fixa (config) + escala com o Nível de Combate + a habilidade Golpes Implacáveis (árvore de Combate).",
            "Fixed base (config) + scales with Combat Level + the Ruthless Strikes ability (combat tree).");
    private static final StatInfo CRIT_DAMAGE_INFO = new StatInfo(
            "Multiplicador de dano de um golpe Crítico, em cima do dano normal.",
            "Damage multiplier a Critical hit gets, on top of a normal hit.",
            "Base fixa (config); a habilidade Maestria Crítica (árvore de Combate) aumenta de acordo com o rank.",
            "Fixed base (config); the Critical Mastery ability (combat tree) raises it based on its rank.");
    private static final StatInfo FEROCITY_INFO = new StatInfo(
            "% de chance por ponto de desferir um golpe extra automático a cada ataque; a cada 100 pontos, 1 golpe extra garantido.",
            "% chance per point of an automatic extra hit on every attack; every 100 points guarantees one extra hit.",
            "Hoje é só um valor base fixo (config) - nenhuma fonte em jogo aumenta ainda.",
            "Currently just a fixed base value (config) - nothing in-game raises it yet.");
    private static final StatInfo ATTACK_SPEED_INFO = new StatInfo(
            "Quantos golpes por segundo sua arma consegue dar - já mostrado assim na tooltip da própria arma.",
            "How many hits per second your weapon can land - already shown this way on the weapon's own tooltip.",
            "Escala com o Nível de Combate (sobe até o nível 50, depois estabiliza).",
            "Scales with Combat Level (climbs up to level 50, then levels off).");
    private static final StatInfo SWING_RANGE_INFO = new StatInfo(
            "Alcance (em blocos) pra acertar golpes corpo a corpo.",
            "Reach (in blocks) for landing melee hits.",
            "Base fixa (config); desbloquear a habilidade Arremesso de Espada (árvore de Combate) soma um bônus. Espadas Longas Lendárias têm um +2 fixo próprio.",
            "Fixed base (config); unlocking the Sword Throw ability (combat tree) adds a bonus. Legendary Longswords carry their own fixed +2 bonus.");
    private static final StatInfo INTELLIGENCE_INFO = new StatInfo(
            "Soma direto na sua Mana máxima e escala o dano de habilidades mágicas.",
            "Adds directly to your max Mana and scales magic-damage abilities.",
            "Sobe com as skills de Alquimia e Encantamento (bônus fixo por nível).",
            "Increases with the Alchemy and Enchanting skills (flat bonus per level).");
    private static final StatInfo AGILITY_INFO = new StatInfo(
            "Alimenta a Velocidade de Movimento - 1 de Agilidade = +1% de Speed.",
            "Feeds your Movement Speed - 1 Agility = +1% Speed.",
            "Hoje só vem de segurar a Adaga de Baruka (Arma Lendária, +50 Agilidade).",
            "Currently only comes from wielding Baruka's Dagger (Legendary Weapon, +50 Agility).");
    private static final StatInfo SPEED_INFO = new StatInfo(
            "Velocidade de movimento real, em % sobre o padrão do vanilla (100% = normal).",
            "Real movement speed, as a % of vanilla's own default (100% = normal).",
            "100% base + Agilidade (veja o stat Agilidade).",
            "100% base + Agility (see the Agility stat).");
    private static final StatInfo ABILITY_DAMAGE_INFO = new StatInfo(
            "% de dano extra em habilidades ativas (Arremesso de Espada, Storm of White Flames...) - não afeta golpes normais.",
            "% extra damage on active abilities (Sword Throw, Storm of White Flames...) - doesn't affect normal hits.",
            "Hoje é só um valor base fixo (config) - nenhuma fonte em jogo aumenta ainda.",
            "Currently just a fixed base value (config) - nothing in-game raises it yet.");
    private static final StatInfo HEALTH_REGEN_INFO = new StatInfo(
            "% multiplicador sobre a regeneração natural de Vida por segundo.",
            "% multiplier on your natural Health regeneration per second.",
            "Base 100% + a habilidade Colheita de Almas (árvore de Combate, ramo Sangue).",
            "100% base + the Soul Harvest ability (combat tree, Blood branch).");
    private static final StatInfo VITALITY_INFO = new StatInfo(
            "Recurso gasto por habilidades de cura (ex.: Vital Touch) - sem Vitalidade suficiente, a habilidade não pode ser usada.",
            "Resource spent by healing abilities (e.g. Vital Touch) - without enough Vitality, the ability can't be used.",
            "Regenera sozinha com o tempo. O máximo é só um valor base fixo (config) - nenhuma fonte em jogo aumenta ainda.",
            "Regenerates on its own over time. The maximum is just a fixed base value (config) - nothing in-game raises it yet.");
    private static final StatInfo MENDING_INFO = new StatInfo(
            "% multiplicador sobre a cura que uma habilidade aplica em OUTRA pessoa (não em você mesmo).",
            "% multiplier on healing an ability lands on someone ELSE (not on yourself).",
            "Base 100% + a habilidade Segundo Fôlego (árvore de Combate, ramo Sangue).",
            "100% base + the Second Wind ability (combat tree, Blood branch).");

    // ---- General skills' own StatInfo ----
    private static final StatInfo FORTUNE_INFO = new StatInfo(
            "Cada ponto é 1% de chance de dropar o dobro do item coletado (minério, colheita ou madeira/recursos, dependendo da skill). A cada 100 pontos completos essa cópia extra vira garantida e o excedente passa a ser a chance da PRÓXIMA cópia (ex.: 120 de Fortune = dobro garantido + 20% de chance de sair o triplo).",
            "Each point is a 1% chance to drop double the gathered item (ore, crops, or wood/resources, depending on the skill). Every full 100 points makes that extra copy guaranteed and the remainder becomes the chance of the NEXT copy (e.g. 120 Fortune = guaranteed double + a 20% chance of tripling it).",
            "Sobe com o nível dessa skill, com peças de armadura que dão Fortune, e com o encantamento Fortune (e, na Agricultura, Harvesting) da ferramenta na mão.",
            "Increases with that skill's level, armor pieces that grant Fortune, and the held tool's own Fortune enchant (and, for Farming, Harvesting).");
    private static final StatInfo SKILL_DEFENSE_INFO = new StatInfo(
            "Soma direto na sua Defesa total (veja o stat Defesa em Status de Combate).",
            "Adds directly to your total Defense (see the Defense stat under Combat Stats).",
            "Sobe automaticamente com o nível da skill de Mineração.",
            "Increases automatically with the Mining skill's level.");
    private static final StatInfo SKILL_HEALTH_INFO = new StatInfo(
            "Soma direto na sua Vida Máxima (veja o stat Vida em Status de Combate).",
            "Adds directly to your Max Health (see the Health stat under Combat Stats).",
            "Sobe automaticamente com o nível dessa skill.",
            "Increases automatically with that skill's level.");
    private static final StatInfo SKILL_STRENGTH_INFO = new StatInfo(
            "Soma direto na sua Strength (veja o stat Strength em Status de Combate).",
            "Adds directly to your Strength (see the Strength stat under Combat Stats).",
            "Sobe automaticamente com o nível da skill de Coleta.",
            "Increases automatically with the Foraging skill's level.");
    private static final StatInfo SWEEP_INFO = new StatInfo(
            "Quantos troncos você derruba de uma vez ao quebrar um de verdade (madeira que cresceu sozinha na natureza).",
            "How many logs you fell at once when breaking a real one (wood that grew naturally in the wild).",
            "Base 1 (nenhum extra) + o acessório da linha Sweep do Mangue (Talismã/Anel/Artefato) guardado na Bolsa de Acessórios. Não conta troncos que você mesmo plantou, a menos que quebre agachado.",
            "Base 1 (no extra) + the Mangrove Sweep line accessory (Talisman/Ring/Artifact) stored in the Accessory Bag. Doesn't count a log you planted yourself, unless you break it while sneaking.");
    private static final StatInfo SKILL_INTELLIGENCE_INFO = new StatInfo(
            "Soma direto na sua Inteligência (veja o stat Inteligência em Status de Combate).",
            "Adds directly to your Intelligence (see the Intelligence stat under Combat Stats).",
            "Sobe automaticamente com o nível dessa skill.",
            "Increases automatically with that skill's level.");
    private static final StatInfo XP_ORBS_INFO = new StatInfo(
            "Aumenta a quantidade de XP vanilla (orbs verdes) que você recebe.",
            "Increases the amount of vanilla XP (green orbs) you receive.",
            "Sobe automaticamente com o nível da skill de Encantamento.",
            "Increases automatically with the Enchanting skill's level.");
    private static final StatInfo POTION_DURATION_INFO = new StatInfo(
            "Aumenta a duração de qualquer poção que você beber.",
            "Increases the duration of any potion you drink.",
            "Sobe automaticamente com o nível da skill de Alquimia.",
            "Increases automatically with the Alchemy skill's level.");

    /**
     * Combat's own summary button on the STATS screen - click to open the full
     * per-stat breakdown ({@link #openStatList}). No live numbers here anymore - every
     * stat's actual value/source lives one click away, on its own item.
     */
    private ItemStack combatStatsItem(Player p, Language l) {
        List<Component> lore = new ArrayList<>();
        for (String part : LoreWrap.wrapText("Stats that influence how much damage you take and deal in combat.", LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(this.click(l));
        return this.item(Material.IRON_SWORD, "Combat Stats", lore);
    }

    /**
     * One item per combat stat (see {@link #STAT_SLOTS}) - Health/Defense/True
     * Defense/Strength/Crit Chance/Crit Damage/Ferocity/Attack Speed/Swing
     * Range/Intelligence/Agility/Speed/Ability Damage/Health Regen/Vitality/Mending,
     * each showing its current value, exactly where that number comes from (the same
     * breakdown {@link #combatStatsItem} used to cram into one item's lore), what the
     * stat actually does, and how to raise it (see {@link #statItem}). Everything here
     * except True Defense is upgradeable through the combat tree and/or a general skill
     * (see {@link CombatAbilityService}'s class doc for which ability grants which
     * bonus, and {@link GeneralSkillService} for Mining/Farming/Fishing/Foraging/
     * Alchemy/Enchanting). Agility/Speed is the odd one out - its only source today is a
     * legendary weapon (Baruka's Dagger), not the tree or a general skill.
     */
    private List<ItemStack> combatStatItems(Player p, Language l) {
        PlayerStats s = this.stats.stats(p);
        CombatProgress c = this.combat.progress(p);
        GlobalLevelSnapshot g = this.global.snapshot(p);
        int defense = this.armor.defense(p);
        ItemStack mainHand = p.getInventory().getItemInMainHand();
        ArmorReforgeStats armorReforge = this.reforge == null ? null : this.reforge.totalArmorStats(p);
        double reforgeStrength = this.reforge == null ? 0.0
                : this.reforge.statsOf(mainHand).strength() + this.reforge.bowStatsOf(mainHand).strength() + armorReforge.strength();
        double reforgeCritChance = this.reforge == null ? 0.0
                : this.reforge.statsOf(mainHand).critChance() + this.reforge.bowStatsOf(mainHand).critChance() + armorReforge.critChance();
        double reforgeCritDamage = this.reforge == null ? 0.0
                : this.reforge.statsOf(mainHand).critDamage() + this.reforge.bowStatsOf(mainHand).critDamage() + armorReforge.critDamage();
        double reforgeHealth = armorReforge == null ? 0.0 : armorReforge.health();
        double reforgeDefense = armorReforge == null ? 0.0 : armorReforge.defense();
        double combatCritChance = this.combat.critChance(c.level());
        double critChanceBonus = this.abilities.critChanceBonus(p);
        double powerCritChance = this.powerCritChance(p);
        double powerCritDamage = this.powerCritDamage(p);
        double critChance = Math.min(100.0, combatCritChance + critChanceBonus + reforgeCritChance + powerCritChance);
        boolean criticalMastery = this.abilities.enabled(p, CombatAbility.CRITICAL_MASTERY);
        double critDamage = (this.abilities.criticalDamageMultiplier(p) - 1.0) * 100.0 + reforgeCritDamage + powerCritDamage;
        double accessoryHealth = this.accessoryBag == null ? 0.0 : this.accessoryBag.totalHealthBonus(p);
        double powerHealth = this.magicalPower == null ? 0.0 : this.magicalPower.healthBonus(p);
        int powerDefense = this.magicalPower == null ? 0 : this.magicalPower.defensePoints(p);
        long powerStrength = this.magicalPower == null ? 0L : this.magicalPower.strengthPoints(p);
        double powerIntelligence = this.magicalPower == null ? 0.0 : this.magicalPower.intelligenceBonus(p);
        // A Power's Speed points become Movement Speed at 0.001 each (see
        // MagicalPowerService#applySpeedAttribute) against vanilla's 0.1 base - i.e. exactly
        // +1% per point on the Speed line below.
        double powerSpeedPercent = this.magicalPower == null ? 0.0 : this.magicalPower.speedBonus(p);
        long globalStrength = g.level() / (long) this.global.levelsPerStrength() * (long) this.global.strengthPerGroup();
        long foragingStrength = this.general.bonusStrength(p);
        double baseHealth = this.stats.baseHealth();
        double bestiaryHealth = this.bestiaryProgress.bonusHealth(p);
        double globalHealth = g.bonusHealth();
        double skillHealth = this.general.bonusHealth(p);
        int helmetDef = ArmorDefenseService.pieceDefense(p.getInventory().getHelmet());
        int chestDef = ArmorDefenseService.pieceDefense(p.getInventory().getChestplate());
        int legsDef = ArmorDefenseService.pieceDefense(p.getInventory().getLeggings());
        int bootsDef = ArmorDefenseService.pieceDefense(p.getInventory().getBoots());
        int miningDef = this.general.bonusDefense(p);

        List<ItemStack> items = new ArrayList<>();

        items.add(this.statItem(Material.GOLDEN_APPLE, "❤ " + "Health: " + Math.round(s.health()) + "/" + Math.round(s.maxHealth()),
                this.join("Base " + Math.round(baseHealth),
                        bestiaryHealth > 0 ? "Bestiary +" + Math.round(bestiaryHealth) : null,
                        globalHealth > 0 ? "Global Level +" + Math.round(globalHealth) : null,
                        skillHealth > 0 ? "Farming/Fishing +" + Math.round(skillHealth) : null,
                        reforgeHealth != 0 ? "Reforge +" + Math.round(reforgeHealth) : null,
                        accessoryHealth > 0 ? "Accessories +" + Math.round(accessoryHealth) : null,
                        this.powerLine(p, powerHealth, String.format(Locale.US, "%.1f", powerHealth))),
                HEALTH_INFO, l));

        double damageReduction = this.armor.damageReduction(p) * 100.0;
        items.add(this.statItem(Material.SHIELD, "✦ " + "Defense: " + defense,
                this.join(reforgeDefense != 0 ? "Reforge +" + Math.round(reforgeDefense) : null,
                        helmetDef > 0 ? "Helmet +" + helmetDef : null,
                        chestDef > 0 ? "Chestplate +" + chestDef : null,
                        legsDef > 0 ? "Leggings +" + legsDef : null,
                        bootsDef > 0 ? "Boots +" + bootsDef : null,
                        miningDef > 0 ? "Mining +" + miningDef : null,
                        this.powerLine(p, powerDefense, String.valueOf(powerDefense)),
                        defense == 0 ? "No source" : null),
                ("= " + defense + "/(" + defense + "+100) = " + String.format(Locale.US, "%.1f", damageReduction) + "% damage reduction"),
                DEFENSE_INFO, l));

        items.add(this.statItem(Material.NETHERITE_INGOT, "🛡 " + "True Defense: " + String.format(Locale.US, "%.0f", s.trueDefense()),
                "Base (config)", TRUE_DEFENSE_INFO, l));

        items.add(this.statItem(Material.DIAMOND_SWORD, "✹ Strength: " + Math.round(s.strength() + reforgeStrength),
                this.join("Global Level +" + globalStrength,
                        foragingStrength > 0 ? "Foraging +" + foragingStrength : null,
                        reforgeStrength != 0 ? "Reforge +" + Math.round(reforgeStrength) : null,
                        this.powerLine(p, powerStrength, String.valueOf(powerStrength))),
                STRENGTH_INFO, l));

        items.add(this.statItem(Material.ARROW, "☣ " + "Crit Chance: " + String.format(Locale.US, "%.1f", critChance) + "%",
                this.join("Combat Level +" + String.format(Locale.US, "%.1f", combatCritChance) + "%",
                        critChanceBonus > 0 ? "Ruthless Strikes +" + String.format(Locale.US, "%.1f", critChanceBonus) + "%" : null,
                        reforgeCritChance != 0 ? "Reforge +" + String.format(Locale.US, "%.1f", reforgeCritChance) + "%" : null,
                        this.powerLine(p, powerCritChance, String.format(Locale.US, "%.1f", powerCritChance) + "%")),
                CRIT_CHANCE_INFO, l));

        items.add(this.statItem(Material.NETHERITE_SWORD, "☠ " + "Crit Damage: " + String.format(Locale.US, "%.1f", critDamage) + "%",
                this.join(criticalMastery ? "Critical Mastery (rank " + this.abilities.rank(p, CombatAbility.CRITICAL_MASTERY) + ")"
                                : "Base (config) - Critical Mastery not unlocked",
                        reforgeCritDamage != 0 ? "Reforge +" + String.format(Locale.US, "%.1f", reforgeCritDamage) + "%" : null,
                        this.powerLine(p, powerCritDamage, String.format(Locale.US, "%.1f", powerCritDamage) + "%")),
                CRIT_DAMAGE_INFO, l));

        items.add(this.statItem(Material.GOLDEN_AXE, "Ⓕ Ferocity: " + Math.round(s.ferocity()),
                "Base (config)", FEROCITY_INFO, l));

        items.add(this.statItem(Material.CLOCK, "⚔ " + "Attack Speed: " + String.format(Locale.US, "%.1f", this.value(p, Attribute.ATTACK_SPEED, 4.0)),
                ("Combat Level " + c.level()), ATTACK_SPEED_INFO, l));

        double swingRangeBonus = this.abilities.swingRangeBonus(p);
        items.add(this.statItem(Material.FISHING_ROD, "↔ " + "Swing Range: " + String.format(Locale.US, "%.1f", s.swingRange()),
                this.join("Base " + String.format(Locale.US, "%.1f", this.stats.baseSwingRange()),
                        swingRangeBonus > 0 ? "Sword Throw +" + String.format(Locale.US, "%.1f", swingRangeBonus) : null),
                SWING_RANGE_INFO, l));

        long alchemyEnchantingIntelligence = this.general.bonusIntelligence(p);
        double reforgeIntelligence = this.reforge == null ? 0.0
                : this.reforge.statsOf(mainHand).intelligence() + this.reforge.bowStatsOf(mainHand).intelligence() + this.reforge.totalArmorStats(p).intelligence();
        items.add(this.statItem(Material.LAPIS_LAZULI, "✎ " + "Intelligence: " + Math.round(s.intelligence()),
                this.join("Base " + Math.round(this.stats.baseIntelligence()),
                        alchemyEnchantingIntelligence > 0 ? "Alchemy/Enchanting +" + alchemyEnchantingIntelligence : null,
                        reforgeIntelligence != 0 ? "Reforge +" + Math.round(reforgeIntelligence) : null,
                        this.powerLine(p, powerIntelligence, String.format(Locale.US, "%.1f", powerIntelligence))),
                INTELLIGENCE_INFO, l));

        // Agility/Speed is the same pairing as Intelligence/Mana above - a plain stat
        // (base plus whatever's currently wielded/worn) that feeds a resource one-for-one,
        // just expressed as a percentage since Speed is a real vanilla attribute rather
        // than a fully custom one - see PlayerStatsService#effectiveAgility.
        double agility = this.stats.effectiveAgility(p);
        double reforgeAgility = this.reforge == null ? 0.0 : this.reforge.totalArmorStats(p).agility();
        double heldAgility = agility - this.stats.baseAgility() - reforgeAgility;
        items.add(this.statItem(Material.RABBIT_FOOT, "🐇 " + "Agility: " + Math.round(agility),
                this.join("Base " + Math.round(this.stats.baseAgility()),
                        heldAgility > 0 ? "Held weapon +" + Math.round(heldAgility) : null,
                        reforgeAgility != 0 ? "Reforge +" + Math.round(reforgeAgility) : null),
                AGILITY_INFO, l));

        long speedPercent = Math.round(this.value(p, Attribute.MOVEMENT_SPEED, 0.1) / 0.1 * 100.0);
        items.add(this.statItem(Material.SUGAR, "🏃 " + "Speed: " + speedPercent + "%",
                this.join("Base 100%",
                        agility > 0 ? "Agility +" + Math.round(agility) + "%" : null,
                        this.powerLine(p, powerSpeedPercent, String.format(Locale.US, "%.1f", powerSpeedPercent) + "%")),
                SPEED_INFO, l));

        items.add(this.statItem(Material.BLAZE_POWDER, "❉ " + "Ability Damage: " + Math.round(s.abilityDamage()) + "%",
                "Base (config)", ABILITY_DAMAGE_INFO, l));

        double healthRegenBonus = this.abilities.healthRegenBonus(p);
        items.add(this.statItem(Material.HONEY_BOTTLE, "❣ " + "Health Regen: " + Math.round(s.healthRegen()) + "%",
                this.join("Base " + Math.round(this.stats.baseHealthRegen()) + "%",
                        healthRegenBonus > 0 ? "Soul Harvest +" + Math.round(healthRegenBonus) + "%" : null),
                HEALTH_REGEN_INFO, l));

        items.add(this.statItem(Material.GHAST_TEAR, "✿ Vitality: " + Math.round(s.vitality()) + "/" + Math.round(s.maxVitality()),
                "Base (config)", VITALITY_INFO, l));

        double mendingBonus = this.abilities.mendingBonus(p);
        items.add(this.statItem(Material.TOTEM_OF_UNDYING, "❋ " + "Mending: " + Math.round(s.mending()) + "%",
                this.join("Base " + Math.round(this.stats.baseMending()) + "%",
                        mendingBonus > 0 ? "Second Wind +" + Math.round(mendingBonus) + "%" : null),
                MENDING_INFO, l));

        ItemStack weapon = p.getInventory().getItemInMainHand();
        double weaponDamage = this.value(p, Attribute.ATTACK_DAMAGE, 1.0);
        double combatLevelBonus = this.combat.damageMultiplier(c.level()) - 1.0;
        double abilityTreeBonus = this.abilities.outgoingMultiplier(p) - 1.0;
        items.add(this.damageItem(l, weapon, weaponDamage, s.strength(), combatLevelBonus, abilityTreeBonus));

        return items;
    }

    /**
     * The actual outgoing-damage equation (see {@code CombatListener#damage}'s own doc
     * for the full breakdown this mirrors) with {@code p}'s own real numbers
     * substituted - still a simplification, not the full per-hit formula: no critical
     * roll, mob-type bonus, or legendary weapon's own situational Backstab/Armored/
     * Undead multiplier, since those genuinely depend on the actual target being hit.
     * Enchants, though, used to be shown outright as "(depends on target)" regardless -
     * wrong for Sharpness specifically, which (unlike Smite/Bane of Arthropods, or the
     * five custom melee-damage enchants - Cubism/Ender Slayer/Impaling/Execute/Giant
     * Killer, all genuinely target-type/health-dependent, see {@code
     * CombatListener#customMeleeDamagePercent}) applies to every target the same way,
     * so it's resolvable right here from the weapon alone (mirrors {@code
     * CombatListener#vanillaDamageEnchantPercent}'s own Sharpness branch and {@code
     * #linearCapped}'s shape). First Strike gets the same treatment as Sharpness here,
     * not the target-dependent list: its own condition (target still at full health)
     * is exactly what this screen's "baseline"/first-hit framing already assumes, so
     * its 25%/level (see {@code CombatListener#customMeleeDamagePercent}) is resolvable
     * from the weapon alone too, same as Sharpness. Now folded into the real computed
     * Multiplier instead, with "(+ depends on target)" appended only when the weapon
     * actually carries one of the genuinely target-dependent enchants above (so a
     * weapon with only Sharpness and/or First Strike, like the common case, shows its
     * true baseline with no caveat at all). {@code
     * weaponDamage} is read straight from {@link Attribute#ATTACK_DAMAGE} (the real,
     * currently-held total - already includes whatever {@code SwordDamageService}/
     * {@code ToolDamageService}/{@code PolearmDamageService}/{@code
     * LegendaryWeaponService} granted the equipped weapon, so this works correctly for
     * any of them without needing its own reference to those classes).
     */
    private ItemStack damageItem(Language l, ItemStack weapon, double weaponDamage, long strength, double combatLevelBonus, double abilityTreeBonus) {
        double initialDamage = (BASE_UNARMED_DAMAGE + weaponDamage) * (1.0 + (double) strength / 100.0);
        int sharpness = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        double vanillaEnchantPercent = sharpness > 0 ? linearCapped(sharpness) : 0.0;
        int firstStrikeLevel = this.enchants == null ? 0 : this.enchants.customLevel(weapon, IcarusEnchant.FIRST_STRIKE);
        // Matches CombatListener#customMeleeDamagePercent's own First Strike formula
        // (25%/level, no isAtFullHealth check here - see this method's own doc for why
        // that condition is exactly this screen's baseline assumption already).
        double enchantPercent = vanillaEnchantPercent + 25.0 * firstStrikeLevel;
        // Sharpness overrides Smite/Bane in CombatListener#vanillaDamageEnchantPercent
        // (real vanilla enchant-table rules never let a weapon carry more than one of
        // the three anyway), so Smite/Bane only matter here when Sharpness is absent.
        boolean targetDependent = sharpness <= 0
                && (weapon.getEnchantmentLevel(Enchantment.SMITE) > 0 || weapon.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS) > 0);
        if (this.enchants != null) {
            for (IcarusEnchant e : TARGET_DEPENDENT_ENCHANTS) {
                if (this.enchants.customLevel(weapon, e) > 0) {
                    targetDependent = true;
                    break;
                }
            }
        }
        double damageMultiplier = 1.0 + combatLevelBonus + enchantPercent / 100.0 + abilityTreeBonus;
        double baseline = initialDamage * damageMultiplier;
        List<Component> lore = new ArrayList<>();
        for (String part : LoreWrap.wrapText("Initial Damage = (5 + Weapon DMG) × (1 + Strength/100)", LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GOLD));
        }
        lore.add(this.text("= (5 + " + String.format(Locale.US, "%.1f", weaponDamage) + ") × (1 + " + strength + "/100) = "
                + String.format(Locale.US, "%.2f", initialDamage), NamedTextColor.GREEN));
        lore.add(Component.empty());
        for (String part : LoreWrap.wrapText("Multiplier = 1 + Level Bonus + Enchants + Ability Bonus", LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GOLD));
        }
        lore.add(this.text("= 1 + " + String.format(Locale.US, "%.2f", combatLevelBonus) + " + "
                + String.format(Locale.US, "%.2f", enchantPercent / 100.0) + (targetDependent ? " " + "(+ depends on target)" : "")
                + " + " + String.format(Locale.US, "%.2f", abilityTreeBonus)
                + " = " + String.format(Locale.US, "%.2f", damageMultiplier), NamedTextColor.GREEN));
        lore.add(Component.empty());
        for (String part : LoreWrap.wrapText("Final Damage (baseline) = Initial × Multiplier", LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GOLD));
        }
        lore.add(this.text("= " + String.format(Locale.US, "%.2f", initialDamage) + " × " + String.format(Locale.US, "%.2f", damageMultiplier)
                + " = " + String.format(Locale.US, "%.1f", baseline), NamedTextColor.GREEN));
        lore.add(Component.empty());
        for (String part : LoreWrap.wrapText(("Critical hits, mob-type/target-health bonuses" + (targetDependent ? ", and the damage enchants above" : "") + " stack on top of this depending on the target."), LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.DARK_GRAY));
        }
        return this.item(Material.NETHERITE_AXE, "⚔ " + "Damage: " + String.format(Locale.US, "%.1f", baseline), lore);
    }

    /** {@code CombatListener#linearCapped}'s exact shape (5%/level, level 5 jumps to 30%) - duplicated here for the same reason as {@link #BASE_UNARMED_DAMAGE}: this is a display-facing approximation, not the real combat calculation, so it doesn't share code with the private method it mirrors. */
    private static int linearCapped(int level) {
        return level == 5 ? 30 : level * 5;
    }

    /**
     * One stat's detail item on a STAT_LIST screen ({@link #openStatList}) - the
     * stat's own name/current value as the item's display name, then three labeled
     * lore blocks: where the current number comes from ({@code source}), what the stat
     * actually does, and how to get more of it (the last two from {@code info}).
     */
    private ItemStack statItem(Material icon, String name, String source, StatInfo info, Language l) {
        return this.statItem(icon, name, source, null, info, l);
    }

    /**
     * Same as {@link #statItem(Material, String, String, StatInfo, Language)}, plus one extra
     * highlighted line right under "What it does" for a stat whose effect is worth spelling out
     * live (e.g. Defense's actual damage-reduction %) instead of leaving it to {@code info}'s
     * fixed, per-language description. {@code extra} may be {@code null} to skip it entirely.
     */
    private ItemStack statItem(Material icon, String name, String source, String extra, StatInfo info, Language l) {
        boolean pt = l == Language.PT;
        List<Component> lore = new ArrayList<>();
        lore.add(this.text("Where it comes from:", NamedTextColor.GOLD));
        for (String part : LoreWrap.wrapText(source, LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text("  " + part, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(this.text("What it does:", NamedTextColor.GOLD));
        for (String part : LoreWrap.wrapText(info.what(pt), LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GRAY));
        }
        if (extra != null) {
            for (String part : LoreWrap.wrapText(extra, LoreWrap.DEFAULT_WIDTH)) {
                lore.add(this.text("  " + part, NamedTextColor.YELLOW));
            }
        }
        lore.add(Component.empty());
        lore.add(this.text("How to get more:", NamedTextColor.GOLD));
        for (String part : LoreWrap.wrapText(info.how(pt), LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GRAY));
        }
        return this.item(icon, name, lore);
    }

    /** Joins non-null parts with " + " - null entries (a bonus that's currently zero) are simply skipped. */
    private String join(String first, String... rest) {
        List<String> parts = new ArrayList<>();
        if (first != null) {
            parts.add(first);
        }
        for (String part : rest) {
            if (part != null) {
                parts.add(part);
            }
        }
        return String.join(" + ", parts);
    }

    /**
     * One tile per general skill (Mining/Farming/Fishing/Foraging/Alchemy/Enchanting) on
     * the STATS screen - click to open the full per-stat breakdown ({@link
     * #openStatList}). No live numbers here anymore, same as {@link #combatStatsItem}.
     */
    private ItemStack skillBonusItem(Player p, SkillType t, Language l) {
        List<Component> lore = new ArrayList<>();
        for (String part : LoreWrap.wrapText("Attribute bonuses this skill grants.", LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(this.click(l));
        return this.item(t.icon(), t.name(l == Language.PT), lore);
    }

    /**
     * Every attribute bonus {@code t} grants (see {@link GeneralSkillService}), one
     * item each - so every skill's contribution is visible somewhere in Stats &
     * Equipment, not just the three that happen to grant Fortune.
     */
    private List<ItemStack> skillStatItems(Player p, SkillType t, Language l) {
        int level = this.general.progress(p, t).level();
        List<ItemStack> items = new ArrayList<>();
        if (t == SkillType.MINING || t == SkillType.FARMING || t == SkillType.FORAGING) {
            Material fortuneIcon = switch (t) {
                case MINING -> Material.RAW_GOLD;
                case FARMING -> Material.WHEAT;
                default -> Material.OAK_LOG;
            };
            // The headline number and its breakdown must both reflect every real source
            // GeneralSkillListener actually rolls with - level, armor (see
            // GeneralSkillService#armorFortuneBonus) and the held tool's own Fortune (and,
            // for Farming, Harvesting) enchant (see GeneralSkillService#toolFortune) -
            // not just the level, which used to be the only thing shown here even though
            // armor/tool Fortune were already silently folded into the total.
            ItemStack tool = p.getInventory().getItemInMainHand();
            int harvestingLevel = t == SkillType.FARMING && this.enchants != null ? this.enchants.customLevel(tool, IcarusEnchant.HARVESTING) : 0;
            int armorBonus = this.general.armorFortuneBonus(p, t);
            int accessoryBonus = this.general.accessoryFortuneBonus(p, t);
            int toolBonus = this.general.toolFortune(tool, t, harvestingLevel);
            int totalFortune = this.general.fortune(p, t) + toolBonus;
            String source = this.rate(l, level, this.general.fortunePerLevel());
            if (armorBonus != 0) {
                source += " + " + armorBonus + " (" + "armor" + ")";
            }
            if (accessoryBonus != 0) {
                source += " + " + accessoryBonus + " (" + "accessory" + ")";
            }
            if (toolBonus != 0) {
                source += " + " + toolBonus + " (" + "held tool" + ")";
            }
            items.add(this.statItem(fortuneIcon, t.name(l == Language.PT) + " Fortune: " + totalFortune, source, FORTUNE_INFO, l));
        }
        switch (t) {
            case MINING -> items.add(this.statItem(Material.SHIELD, "+" + (level * this.general.defensePerLevel()) + " " + "Defense",
                    this.rate(l, level, this.general.defensePerLevel()), SKILL_DEFENSE_INFO, l));
            case FARMING, FISHING -> items.add(this.statItem(Material.GOLDEN_APPLE, "+" + (level * this.general.healthPerLevel()) + " " + "Max Health",
                    this.rate(l, level, this.general.healthPerLevel()), SKILL_HEALTH_INFO, l));
            case FORAGING -> {
                items.add(this.statItem(Material.DIAMOND_SWORD, "+" + (level * this.general.strengthPerLevel()) + " " + "Strength",
                        this.rate(l, level, this.general.strengthPerLevel()), SKILL_STRENGTH_INFO, l));
                int sweepBonus = this.general.sweepBonus(p);
                String sweepSource = "1 (base)";
                if (sweepBonus != 0) {
                    sweepSource += " + " + sweepBonus + " (" + "accessory" + ")";
                }
                items.add(this.statItem(Material.IRON_AXE, "Sweep" + ": " + this.general.sweep(p),
                        sweepSource, SWEEP_INFO, l));
            }
            case ALCHEMY, ENCHANTING -> items.add(this.statItem(Material.LAPIS_LAZULI, "+" + (level * this.general.intelligencePerLevel()) + " " + "Intelligence",
                    this.rate(l, level, this.general.intelligencePerLevel()), SKILL_INTELLIGENCE_INFO, l));
            default -> {}
        }
        if (t == SkillType.ENCHANTING) {
            items.add(this.statItem(Material.EXPERIENCE_BOTTLE, "+" + (level * this.general.xpOrbPercentPerLevel()) + "% " + "XP Orbs",
                    this.rate(l, level, this.general.xpOrbPercentPerLevel()), XP_ORBS_INFO, l));
        }
        if (t == SkillType.ALCHEMY) {
            items.add(this.statItem(Material.POTION, "+" + (level * this.general.potionDurationPercentPerLevel()) + "% " + "Potion Duration",
                    this.rate(l, level, this.general.potionDurationPercentPerLevel()), POTION_DURATION_INFO, l));
        }
        return items;
    }

    /**
     * Full per-stat breakdown for either Combat ({@code skill == null}) or one general
     * skill - one item per stat (see {@link #STAT_SLOTS}), each built by {@link
     * #statItem}. Reached by clicking {@link #combatStatsItem}/{@link #skillBonusItem}
     * on the STATS screen; Voltar returns there for the same (viewer, target) pair.
     */
    private void openStatList(Player viewer, Player target, SkillType skill) {
        Language l = Language.of(viewer);
        boolean pt = l == Language.PT;
        String title = skill == null ? "Combat Stats"
                : (skill.name(false) + " Stats");
        Inventory v = this.inv(title);
        List<ItemStack> items = skill == null ? this.combatStatItems(target, l) : this.skillStatItems(target, skill, l);
        for (int i = 0; i < items.size() && i < STAT_SLOTS.length; i++) {
            v.setItem(STAT_SLOTS[i], items.get(i));
        }
        v.setItem(STAT_LIST_TITLE_SLOT, this.item(skill == null ? Material.IRON_SWORD : skill.icon(), title, List.of()));
        v.setItem(STAT_LIST_BACK_SLOT, this.customHead(HeadTexture.BACK, "Back", List.of()));
        this.open(viewer, v, new View(Type.STAT_LIST, 0, skill, target.getUniqueId()));
    }

    /** "Level N × rate/level" - the source line every {@link #skillStatItems} entry shares. */
    private String rate(Language l, int level, int perLevel) {
        return ("Level " + level + " × " + perLevel + "/level");
    }

    private double value(Player p, Attribute a, double f) {
        AttributeInstance x = p.getAttribute(a);
        return x == null ? f : x.getValue();
    }

    private Component text(String s, NamedTextColor c) {
        return Component.text(s, c);
    }

    private ItemStack item(Material mat, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(cmp -> cmp.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    /** {@code target} is only ever non-null for {@link Type#STATS}/{@link Type#STAT_LIST} - who the viewer (this View's own owner, the key in {@link #views}) is looking at, self included (see {@link #openStats}). */
    private record View(Type type, int page, SkillType skill, UUID target) {
        private View(Type type, int page, SkillType skill) {
            this(type, page, skill, null);
        }
    }

    private enum Type {
        MAIN, SKILLS_LIST, BAGS, COMBAT, GENERAL, GLOBAL, STATS, STAT_LIST
    }
}
