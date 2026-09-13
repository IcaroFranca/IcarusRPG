package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import dev.icaro.foodtooltips.crafting.CraftingMenuService;
import dev.icaro.foodtooltips.enchant.EnchantMenuService;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalLevelSnapshot;
import dev.icaro.foodtooltips.global.LevelColorMenuService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.mining.MiningMenuService;
import dev.icaro.foodtooltips.stats.PlayerStats;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import dev.icaro.foodtooltips.travel.TravelMenuService;
import dev.icaro.foodtooltips.trash.TrashMenuService;
import dev.icaro.foodtooltips.util.LoreWrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class SkillsMenuService {
    private static final int[] N = new int[]{9, 18, 27, 28, 29, 20, 11, 2, 3, 4, 13, 22, 31, 32, 33, 24, 15, 6, 7, 8, 17, 26, 35, 44, 53};
    private static final Map<Integer, SkillType> S = Map.of(21, SkillType.FARMING, 22, SkillType.MINING, 23, SkillType.FISHING, 24, SkillType.FORAGING, 30, SkillType.ALCHEMY, 32, SkillType.ENCHANTING);
    /** Bottom-right corner of the MAIN screen only (unused there - {@link #N} only places level nodes on this slot in the other screens). */
    private static final int TRASH_BUTTON_SLOT = 53;
    /** The MAIN screen's Quiver button - see {@link QuiverService}. */
    private static final int QUIVER_SLOT = 33;
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

    private final CombatSkillService combat;
    private final GeneralSkillService general;
    private final PlayerStatsService stats;
    private final CombatAbilityService abilities;
    private final MiningMenuService mining;
    private final GlobalLevelService global;
    private final ArmorDefenseService armor;
    private final BestiaryProgressService bestiaryProgress;
    private LevelColorMenuService levelColors;
    private CombatTreeMenuService tree;
    private TravelMenuService travel;
    private CraftingMenuService crafting;
    private TrashMenuService trash;
    private EnchantMenuService enchantMenu;
    private QuiverService quiver;
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

    public void quiver(QuiverService quiver) {
        this.quiver = quiver;
    }

    /**
     * Bestiário and Árvore de Combate are deliberately NOT buttons here — they live only
     * on the Combat skill screen ({@link #openCombat}), reachable from the Combat icon
     * below (Bestiário is also reachable via {@code /bestiary}).
     */
    public void openMain(Player p) {
        Language l = Language.of(p);
        Inventory v = this.inv(l.choose("Habilidades", "Skills"));
        v.setItem(4, this.head(p, l));
        v.setItem(20, this.item(Material.IRON_SWORD, l.choose("Combate", "Combat"), List.of(this.combatLine(p, l), this.click(l))));
        for (Map.Entry<Integer, SkillType> e : S.entrySet()) {
            SkillType t = e.getValue();
            v.setItem(e.getKey(), this.item(t.icon(), t.name(l == Language.PT), List.of(this.skillLine(p, t, l), this.click(l))));
        }
        v.setItem(13, this.globalLevelIcon(p, l));
        if (this.levelColors != null) {
            v.setItem(45, this.item(Material.NAME_TAG, l.choose("Cores do Nível", "Level Colors"), List.of(this.click(l))));
        }
        if (this.travel != null) {
            v.setItem(49, this.customHead(HeadTexture.PLANET, l.choose("Locais", "Locations"), List.of(this.click(l))));
        }
        if (this.crafting != null) {
            v.setItem(31, this.item(Material.CRAFTING_TABLE, l.choose("Mesa de Trabalho", "Crafting Table"), List.of(this.click(l))));
        }
        if (this.trash != null) {
            v.setItem(TRASH_BUTTON_SLOT, this.customHead(HeadTexture.TRASH_CAN, l.choose("Lixeira", "Trash Can"), List.of(this.click(l))));
        }
        if (this.quiver != null && this.quiver.unlocked(p)) {
            v.setItem(QUIVER_SLOT, this.customHead(HeadTexture.QUIVER, this.quiver.displayName(p, l),
                    List.of(this.text(l.choose("O arco puxa flechas daqui direto, sem precisar deixá-las no inventário.", "The bow pulls arrows from here directly, without needing to keep them in your inventory."), NamedTextColor.GRAY), this.click(l))));
        }
        this.open(p, v, new View(Type.MAIN, 0, null));
    }

    public void openCombat(Player p, int page) {
        page = this.clamp(page, this.combat.maxLevel());
        Language l = Language.of(p);
        Inventory v = this.inv(l.choose("Skill de Combate", "Combat Skill"));
        CombatProgress x = this.combat.progress(p);
        for (int i = 0; i < 25; ++i) {
            int level = page * 25 + i + 1;
            ArrayList<Component> lore = new ArrayList<>(List.of(this.text("+0.5% " + l.choose("Chance crítica", "Crit Chance"), NamedTextColor.AQUA), this.text("+4% " + l.choose("de dano", "Damage"), NamedTextColor.RED)));
            double speedGain = this.combat.attackSpeed(level) - this.combat.attackSpeed(level - 1);
            if (speedGain > 1.0E-4) {
                lore.add(this.text("+" + String.format(Locale.US, "%.2f", speedGain) + " " + l.choose("Velocidade de Ataque", "Attack Speed"), NamedTextColor.YELLOW));
            }
            if (level == x.level() + 1) {
                lore.add(this.xp(x.xp(), x.requiredXp()));
            }
            v.setItem(N[i], this.node(level, x.level(), l, lore));
        }
        v.setItem(0, this.item(Material.IRON_SWORD, l.choose("Progressão de Combate", "Combat Progression"), List.of(this.combatLine(p, l))));
        v.setItem(39, this.item(Material.KNOWLEDGE_BOOK, l.choose("Bestiário", "Bestiary"), List.of(this.click(l))));
        v.setItem(41, this.item(Material.CHEST, l.choose("Árvore de Combate", "Combat Tree"), List.of(this.click(l))));
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
            ArrayList<Component> lore = new ArrayList<>(List.of(this.text(l.choose("Continue usando esta skill para evoluir.", "Keep using this skill to level up."), NamedTextColor.GRAY)));
            lore.addAll(this.attributeRewardLines(t, l));
            List<String> enchantUnlocks = t == SkillType.ENCHANTING && this.enchantMenu != null ? this.enchantMenu.enchantingUnlocksAtLevel(level, l == Language.PT) : List.of();
            if (t == SkillType.MINING && level == 3) {
                lore.add(this.text("✦ " + l.choose("Desbloqueia: Vein Miner", "Unlocks: Vein Miner"), NamedTextColor.LIGHT_PURPLE));
            } else if (!enchantUnlocks.isEmpty()) {
                for (String name : enchantUnlocks) {
                    lore.add(this.text("✦ " + l.choose("Desbloqueia encantamento: ", "Unlocks enchantment: ") + name, NamedTextColor.LIGHT_PURPLE));
                }
            } else {
                lore.add(this.text(l.choose("Nenhuma habilidade neste nível.", "No ability at this level."), NamedTextColor.DARK_GRAY));
            }
            if (level == x.level() + 1) {
                lore.add(this.xp(x.xp(), x.requiredXp()));
            }
            v.setItem(N[i], this.node(level, x.level(), l, lore));
        }
        v.setItem(0, this.item(t.icon(), l.choose("Progressão de ", "Progression: ") + t.name(l == Language.PT), List.of(this.skillLine(p, t, l))));
        if (t == SkillType.MINING) {
            v.setItem(40, this.item(Material.BOOK, l.choose("Compêndio de Mineração", "Mining Compendium"), List.of(this.text(l.choose("Contadores, milestones, XP, drops e camadas.", "Counters, milestones, XP, drops and layers."), NamedTextColor.YELLOW))));
        } else if (t == SkillType.ENCHANTING && this.enchantMenu != null) {
            v.setItem(39, this.item(Material.ENCHANTED_BOOK, l.choose("Milestones de Encantamento", "Enchantment Milestones"),
                    List.of(this.text(l.choose("Progresso por encantamento, filtrado por Armas/Ferramentas/Armadura.", "Progress per enchantment, filtered by Weapons/Tools/Armor."), NamedTextColor.YELLOW),
                            this.text(l.choose("Só conta o que foi aplicado na Mesa de Encantamento.", "Only counts what was applied at the Enchanting Table."), NamedTextColor.GRAY))));
            v.setItem(41, this.item(Material.BOOK, l.choose("Guia de Encantamentos", "Enchantment Guide"), List.of(this.text(l.choose("Veja todos os encantamentos disponíveis.", "See every enchantment available."), NamedTextColor.YELLOW))));
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
            case MINING -> lines.add(this.text("+" + this.general.defensePerLevel() + " " + l.choose("Defesa", "Defense"), NamedTextColor.GREEN));
            case FARMING, FISHING -> lines.add(this.text("+" + this.general.healthPerLevel() + " " + l.choose("Vida Máxima", "Max Health"), NamedTextColor.RED));
            case FORAGING -> lines.add(this.text("+" + this.general.strengthPerLevel() + " " + l.choose("Força", "Strength"), NamedTextColor.YELLOW));
            case ALCHEMY, ENCHANTING -> lines.add(this.text("+" + this.general.intelligencePerLevel() + " " + l.choose("Inteligência", "Intelligence"), NamedTextColor.LIGHT_PURPLE));
            default -> {}
        }
        if (t == SkillType.ENCHANTING) {
            lines.add(this.text("+" + this.general.xpOrbPercentPerLevel() + "% " + l.choose("Orbs de XP", "XP Orbs"), NamedTextColor.AQUA));
        }
        if (t == SkillType.ALCHEMY) {
            lines.add(this.text("+" + this.general.potionDurationPercentPerLevel() + "% " + l.choose("Duração de Poções", "Potion Duration"), NamedTextColor.DARK_PURPLE));
        }
        if (lines.isEmpty()) {
            lines.add(this.text(l.choose("Nenhuma recompensa de atributo neste nível.", "No attribute reward at this level."), NamedTextColor.AQUA));
        }
        return lines;
    }

    public void openGlobal(Player p, int page) {
        int maxLevel = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, this.global.maxAchievableLevel()));
        page = this.clamp(page, maxLevel);
        Language l = Language.of(p);
        Inventory v = this.inv(l.choose("Nível Global", "Global Level"));
        GlobalLevelSnapshot g = this.global.snapshot(p);
        int currentLevel = (int) Math.min(Integer.MAX_VALUE, g.level());
        int levelsPerStrength = Math.max(1, this.global.levelsPerStrength());
        for (int i = 0; i < 25; ++i) {
            int level = page * 25 + i + 1;
            ArrayList<Component> lore = new ArrayList<>(List.of(
                    this.text("❤ +" + Math.round(this.global.hpPerLevel()) + " " + l.choose("HP máximo", "max HP"), NamedTextColor.RED)));
            if (level % levelsPerStrength == 0) {
                lore.add(this.text("✹ +" + this.global.strengthPerGroup() + " Strength", NamedTextColor.GOLD));
            }
            if (level == this.global.telekinesisRequiredLevel()) {
                lore.add(this.text("🧲 " + l.choose("Desbloqueia: Telecinese", "Unlocks: Telekinesis"), NamedTextColor.LIGHT_PURPLE));
            }
            if (lore.size() == 1) {
                lore.add(this.text(l.choose("Nenhuma outra recompensa neste nível.", "No other reward at this level."), NamedTextColor.DARK_GRAY));
            }
            if (level == currentLevel + 1) {
                lore.add(this.text(g.progress() + "/" + g.required() + " XP", NamedTextColor.GREEN));
            }
            v.setItem(N[i], this.node(level, currentLevel, l, lore));
        }
        v.setItem(0, this.item(Material.NETHER_STAR, l.choose("Progressão de Nível Global", "Global Level Progression"), List.of(this.globalLine(p, l))));
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
                if (slot == 4) {
                    this.openStats(p, p);
                } else if (slot == 13) {
                    this.openGlobal(p, 0);
                } else if (slot == 20) {
                    this.openCombat(p, 0);
                } else if (S.containsKey(slot)) {
                    this.openGeneral(p, S.get(slot), 0);
                } else if (slot == 45 && this.levelColors != null) {
                    this.views.remove(p.getUniqueId());
                    this.levelColors.open(p);
                } else if (slot == 49 && this.travel != null) {
                    this.views.remove(p.getUniqueId());
                    this.travel.open(p);
                } else if (slot == 31 && this.crafting != null) {
                    this.views.remove(p.getUniqueId());
                    this.crafting.open(p);
                } else if (slot == TRASH_BUTTON_SLOT && this.trash != null) {
                    this.views.remove(p.getUniqueId());
                    this.trash.open(p);
                } else if (slot == QUIVER_SLOT && this.quiver != null && this.quiver.unlocked(p)) {
                    this.views.remove(p.getUniqueId());
                    this.quiver.open(p);
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
                if (slot == 45) {
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
        this.views.put(p.getUniqueId(), view);
    }

    private Inventory inv(String title) {
        Inventory v = Bukkit.createInventory(null, 54, title);
        ItemStack f = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; ++i) {
            v.setItem(i, f);
        }
        return v;
    }

    private void nav(Inventory v, Language l, int page, int max) {
        v.setItem(45, this.customHead(HeadTexture.BACK, l.choose("Voltar às skills", "Back to skills"), List.of()));
        if (page > 0) {
            v.setItem(48, this.customHead(HeadTexture.ARROW_LEFT, l.choose("Página anterior", "Previous page"), List.of()));
        }
        if ((page + 1) * 25 < max) {
            v.setItem(50, this.customHead(HeadTexture.ARROW_RIGHT, l.choose("Próxima página", "Next page"), List.of()));
        }
    }

    private int clamp(int p, int max) {
        return Math.max(0, Math.min((max - 1) / 25, p));
    }

    private ItemStack node(int n, int current, Language l, List<Component> lore) {
        ItemStack i = this.item(n <= current ? Material.LIME_STAINED_GLASS_PANE : (n == current + 1 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE), l.choose("Nível ", "Level ") + n, lore);
        i.setAmount(Math.min(64, n));
        return i;
    }

    private Component combatLine(Player p, Language l) {
        CombatProgress x = this.combat.progress(p);
        return this.text(l.choose("Nível ", "Level ") + x.level() + (x.level() >= this.combat.maxLevel() ? " (MAX)" : " • " + Math.round(x.xp()) + "/" + Math.round(x.requiredXp()) + " XP"), NamedTextColor.GREEN);
    }

    private Component globalLine(Player p, Language l) {
        GlobalLevelSnapshot g = this.global.snapshot(p);
        return this.text(l.choose("Nível ", "Level ") + g.level() + " • " + g.progress() + "/" + g.required() + " XP", NamedTextColor.GOLD);
    }

    /** Global Level's button on the main skills grid — a custom head if one is configured, otherwise an XP bottle. */
    private ItemStack globalLevelIcon(Player p, Language l) {
        List<Component> lore = List.of(this.globalLine(p, l), this.click(l));
        String texture = this.global.iconTexture();
        if (texture != null && !texture.isBlank()) {
            return this.customHead(texture, l.choose("Nível Global", "Global Level"), lore);
        }
        return this.item(Material.EXPERIENCE_BOTTLE, l.choose("Nível Global", "Global Level"), lore);
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

    private Component skillLine(Player p, SkillType t, Language l) {
        SkillProgress x = this.general.progress(p, t);
        return this.text(l.choose("Nível ", "Level ") + x.level() + (x.level() >= this.general.maxLevel() ? " (MAX)" : " • " + Math.round(x.xp()) + "/" + Math.round(x.requiredXp()) + " XP"), NamedTextColor.GREEN);
    }

    private Component xp(double a, double b) {
        return this.text(Math.round(a) + "/" + Math.round(b) + " XP", NamedTextColor.GREEN);
    }

    private Component click(Language l) {
        return this.text(l.choose("Clique para ver!", "Click to view!"), NamedTextColor.YELLOW);
    }

    /** Condensed hover preview (the head icon in the main menu) — click it to open {@link #openStats}. */
    private ItemStack head(Player p, Language l) {
        PlayerStats s = this.stats.stats(p);
        CombatProgress c = this.combat.progress(p);
        int defense = this.armor.defense(p);
        long speedPercent = Math.round(this.value(p, Attribute.MOVEMENT_SPEED, 0.1) / 0.1 * 100.0);
        double critDamage = (this.abilities.criticalDamageMultiplier(p) - 1.0) * 100.0;
        double critChance = Math.min(100.0, this.combat.critChance(c.level()) + this.abilities.critChanceBonus(p));
        List<Component> lore = List.of(
                this.text(l.choose("Veja seu equipamento, status e mais!", "View your equipment, stats, and more!"), NamedTextColor.GRAY),
                Component.empty(),
                this.text("🏃 " + l.choose("Velocidade: ", "Speed: ") + speedPercent, NamedTextColor.WHITE),
                this.text("🐇 " + l.choose("Agilidade: ", "Agility: ") + Math.round(this.stats.effectiveAgility(p)), NamedTextColor.WHITE),
                this.text("✹ Strength: " + s.strength(), NamedTextColor.RED),
                this.text("✦ " + l.choose("Defesa: ", "Defense: ") + defense, NamedTextColor.GREEN),
                this.text("☠ " + l.choose("Dano Crítico: ", "Crit Damage: ") + String.format(Locale.US, "%.1f", critDamage) + "%", NamedTextColor.BLUE),
                this.text("☣ " + l.choose("Chance Crítica: ", "Crit Chance: ") + String.format(Locale.US, "%.1f", critChance) + "%", NamedTextColor.BLUE),
                this.text("❤ " + l.choose("Vida: ", "Health: ") + Math.round(s.health()) + "/" + Math.round(s.maxHealth()), NamedTextColor.RED),
                this.text("✎ " + l.choose("Inteligência: ", "Intelligence: ") + Math.round(s.intelligence()), NamedTextColor.AQUA),
                Component.empty(),
                this.text(l.choose("Clique para ver mais!", "Click to see more!"), NamedTextColor.YELLOW));
        ItemStack i = this.item(Material.PLAYER_HEAD, l.choose("Status & Equipamento", "Stats & Equipment"), lore);
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
        Inventory v = this.inv(target.getName() + " - " + l.choose("Status & Equipamento", "Stats & Equipment"));
        v.setItem(4, this.statsOverviewHead(target, l));
        v.setItem(11, this.armorSlot(target.getInventory().getHelmet(), l.choose("Capacete", "Helmet"), l));
        v.setItem(20, this.armorSlot(target.getInventory().getChestplate(), l.choose("Peitoral", "Chestplate"), l));
        v.setItem(29, this.armorSlot(target.getInventory().getLeggings(), l.choose("Calças", "Leggings"), l));
        v.setItem(38, this.armorSlot(target.getInventory().getBoots(), l.choose("Botas", "Boots"), l));
        v.setItem(STATS_COMBAT_SLOT, this.combatStatsItem(target, l));
        for (Map.Entry<Integer, SkillType> e : STATS_SKILL_SLOTS.entrySet()) {
            v.setItem(e.getKey(), this.skillBonusItem(target, e.getValue(), l));
        }
        v.setItem(45, this.customHead(HeadTexture.BACK, l.choose("Voltar às skills", "Back to skills"), List.of()));
        this.open(viewer, v, new View(Type.STATS, 0, null, target.getUniqueId()));
    }

    /** {@code v.target()} resolved back to an online {@link Player}, or {@code viewer} itself if that field is absent - never null unless the target logged off since the screen opened. */
    private Player resolveTarget(Player viewer, View v) {
        return v.target() == null ? viewer : Bukkit.getPlayer(v.target());
    }

    private ItemStack statsOverviewHead(Player p, Language l) {
        GlobalLevelSnapshot g = this.global.snapshot(p);
        List<Component> lore = List.of(this.text("✦ " + l.choose("Nível Global: ", "Global Level: ") + g.level(), NamedTextColor.GOLD));
        ItemStack i = this.item(Material.PLAYER_HEAD, p.getName(), lore);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        m.setOwningPlayer((OfflinePlayer) p);
        i.setItemMeta(m);
        return i;
    }

    /** The player's actual equipped piece (real item, with its own name/enchants/lore), or an empty placeholder. */
    private ItemStack armorSlot(ItemStack equipped, String slotName, Language l) {
        if (equipped == null || equipped.getType().isAir()) {
            return this.item(Material.GRAY_STAINED_GLASS_PANE, slotName, List.of(this.text(l.choose("Nada equipado.", "Nothing equipped."), NamedTextColor.DARK_GRAY)));
        }
        return equipped.clone();
    }

    /** One "what it does / how to get more" pair for a stat's detail item (see {@link #statItem}) - the current value and its breakdown are computed live per-player by the caller, only this descriptive text is fixed per stat. */
    private record StatInfo(String whatPt, String whatEn, String howPt, String howEn) {
        String what(boolean pt) {
            return pt ? this.whatPt : this.whatEn;
        }

        String how(boolean pt) {
            return pt ? this.howPt : this.howEn;
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
            "Base fixa (config); desbloquear a habilidade Arremesso de Espada (árvore de Combate) soma um bônus. Algumas Adagas Lendárias têm um -1 fixo próprio.",
            "Fixed base (config); unlocking the Sword Throw ability (combat tree) adds a bonus. Some Legendary Daggers carry their own fixed -1 penalty.");
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
            "Aumenta a quantidade de itens que você recebe ao coletar (minério, colheita ou madeira/recursos, dependendo da skill).",
            "Increases the amount of items you get when gathering (ore, crops, or wood/resources, depending on the skill).",
            "Sobe automaticamente com o nível dessa skill.",
            "Increases automatically with that skill's level.");
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
        List<Component> lore = List.of(
                this.text(l.choose("Status que influenciam quanto dano você recebe e causa em combate.", "Stats that influence how much damage you take and deal in combat."), NamedTextColor.GRAY),
                Component.empty(),
                this.click(l));
        return this.item(Material.IRON_SWORD, l.choose("Status de Combate", "Combat Stats"), lore);
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
        double combatCritChance = this.combat.critChance(c.level());
        double critChanceBonus = this.abilities.critChanceBonus(p);
        double critChance = Math.min(100.0, combatCritChance + critChanceBonus);
        boolean criticalMastery = this.abilities.enabled(p, CombatAbility.CRITICAL_MASTERY);
        double critDamage = (this.abilities.criticalDamageMultiplier(p) - 1.0) * 100.0;
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

        items.add(this.statItem(Material.GOLDEN_APPLE, "❤ " + l.choose("Vida: ", "Health: ") + Math.round(s.health()) + "/" + Math.round(s.maxHealth()),
                this.join(l.choose("Base ", "Base ") + Math.round(baseHealth),
                        bestiaryHealth > 0 ? l.choose("Bestiário +", "Bestiary +") + Math.round(bestiaryHealth) : null,
                        globalHealth > 0 ? l.choose("Nível Global +", "Global Level +") + Math.round(globalHealth) : null,
                        skillHealth > 0 ? l.choose("Agricultura/Pesca +", "Farming/Fishing +") + Math.round(skillHealth) : null),
                HEALTH_INFO, l));

        items.add(this.statItem(Material.SHIELD, "✦ " + l.choose("Defesa: ", "Defense: ") + defense,
                this.join(null,
                        helmetDef > 0 ? l.choose("Elmo +", "Helmet +") + helmetDef : null,
                        chestDef > 0 ? l.choose("Peitoral +", "Chestplate +") + chestDef : null,
                        legsDef > 0 ? l.choose("Calças +", "Leggings +") + legsDef : null,
                        bootsDef > 0 ? l.choose("Botas +", "Boots +") + bootsDef : null,
                        miningDef > 0 ? l.choose("Mineração +", "Mining +") + miningDef : null,
                        defense == 0 ? l.choose("Nenhuma fonte", "No source") : null),
                DEFENSE_INFO, l));

        items.add(this.statItem(Material.NETHERITE_INGOT, "🛡 " + l.choose("Defesa Verdadeira: ", "True Defense: ") + String.format(Locale.US, "%.0f", s.trueDefense()),
                l.choose("Base (config)", "Base (config)"), TRUE_DEFENSE_INFO, l));

        items.add(this.statItem(Material.DIAMOND_SWORD, "✹ Strength: " + s.strength(),
                this.join(l.choose("Nível Global +", "Global Level +") + globalStrength,
                        foragingStrength > 0 ? l.choose("Coleta +", "Foraging +") + foragingStrength : null),
                STRENGTH_INFO, l));

        items.add(this.statItem(Material.ARROW, "☣ " + l.choose("Chance Crítica: ", "Crit Chance: ") + String.format(Locale.US, "%.1f", critChance) + "%",
                this.join(l.choose("Nível de Combate +", "Combat Level +") + String.format(Locale.US, "%.1f", combatCritChance) + "%",
                        critChanceBonus > 0 ? l.choose("Golpes Implacáveis +", "Ruthless Strikes +") + String.format(Locale.US, "%.1f", critChanceBonus) + "%" : null),
                CRIT_CHANCE_INFO, l));

        items.add(this.statItem(Material.NETHERITE_SWORD, "☠ " + l.choose("Dano Crítico: ", "Crit Damage: ") + String.format(Locale.US, "%.1f", critDamage) + "%",
                criticalMastery ? l.choose("Maestria Crítica (rank ", "Critical Mastery (rank ") + this.abilities.rank(p, CombatAbility.CRITICAL_MASTERY) + ")"
                        : l.choose("Base (config) - Maestria Crítica não desbloqueada", "Base (config) - Critical Mastery not unlocked"),
                CRIT_DAMAGE_INFO, l));

        items.add(this.statItem(Material.GOLDEN_AXE, "Ⓕ Ferocity: " + Math.round(s.ferocity()),
                l.choose("Base (config)", "Base (config)"), FEROCITY_INFO, l));

        items.add(this.statItem(Material.CLOCK, "⚔ " + l.choose("Velocidade de Ataque: ", "Attack Speed: ") + String.format(Locale.US, "%.1f", this.value(p, Attribute.ATTACK_SPEED, 4.0)),
                l.choose("Nível de Combate " + c.level(), "Combat Level " + c.level()), ATTACK_SPEED_INFO, l));

        double swingRangeBonus = this.abilities.swingRangeBonus(p);
        items.add(this.statItem(Material.FISHING_ROD, "↔ " + l.choose("Alcance de Ataque: ", "Swing Range: ") + String.format(Locale.US, "%.1f", s.swingRange()),
                this.join(l.choose("Base ", "Base ") + String.format(Locale.US, "%.1f", this.stats.baseSwingRange()),
                        swingRangeBonus > 0 ? l.choose("Arremesso de Espada +", "Sword Throw +") + String.format(Locale.US, "%.1f", swingRangeBonus) : null),
                SWING_RANGE_INFO, l));

        long alchemyEnchantingIntelligence = this.general.bonusIntelligence(p);
        items.add(this.statItem(Material.LAPIS_LAZULI, "✎ " + l.choose("Inteligência: ", "Intelligence: ") + Math.round(s.intelligence()),
                this.join(l.choose("Base ", "Base ") + Math.round(this.stats.baseIntelligence()),
                        alchemyEnchantingIntelligence > 0 ? l.choose("Alquimia/Encantamento +", "Alchemy/Enchanting +") + alchemyEnchantingIntelligence : null),
                INTELLIGENCE_INFO, l));

        // Agility/Speed is the same pairing as Intelligence/Mana above - a plain stat
        // (base plus whatever's currently wielded) that feeds a resource one-for-one,
        // just expressed as a percentage since Speed is a real vanilla attribute rather
        // than a fully custom one - see PlayerStatsService#effectiveAgility.
        double agility = this.stats.effectiveAgility(p);
        double heldAgility = agility - this.stats.baseAgility();
        items.add(this.statItem(Material.RABBIT_FOOT, "🐇 " + l.choose("Agilidade: ", "Agility: ") + Math.round(agility),
                this.join(l.choose("Base ", "Base ") + Math.round(this.stats.baseAgility()),
                        heldAgility > 0 ? l.choose("Arma equipada +", "Held weapon +") + Math.round(heldAgility) : null),
                AGILITY_INFO, l));

        long speedPercent = Math.round(this.value(p, Attribute.MOVEMENT_SPEED, 0.1) / 0.1 * 100.0);
        items.add(this.statItem(Material.SUGAR, "🏃 " + l.choose("Velocidade: ", "Speed: ") + speedPercent + "%",
                this.join(l.choose("Base 100%", "Base 100%"),
                        agility > 0 ? l.choose("Agilidade +", "Agility +") + Math.round(agility) + "%" : null),
                SPEED_INFO, l));

        items.add(this.statItem(Material.BLAZE_POWDER, "❉ " + l.choose("Dano de Habilidade: ", "Ability Damage: ") + Math.round(s.abilityDamage()) + "%",
                l.choose("Base (config)", "Base (config)"), ABILITY_DAMAGE_INFO, l));

        double healthRegenBonus = this.abilities.healthRegenBonus(p);
        items.add(this.statItem(Material.HONEY_BOTTLE, "❣ " + l.choose("Regen. de Vida: ", "Health Regen: ") + Math.round(s.healthRegen()) + "%",
                this.join(l.choose("Base ", "Base ") + Math.round(this.stats.baseHealthRegen()) + "%",
                        healthRegenBonus > 0 ? l.choose("Colheita de Almas +", "Soul Harvest +") + Math.round(healthRegenBonus) + "%" : null),
                HEALTH_REGEN_INFO, l));

        items.add(this.statItem(Material.GHAST_TEAR, "✿ Vitality: " + Math.round(s.vitality()) + "/" + Math.round(s.maxVitality()),
                l.choose("Base (config)", "Base (config)"), VITALITY_INFO, l));

        double mendingBonus = this.abilities.mendingBonus(p);
        items.add(this.statItem(Material.TOTEM_OF_UNDYING, "❋ " + l.choose("Cura (Mending): ", "Mending: ") + Math.round(s.mending()) + "%",
                this.join(l.choose("Base ", "Base ") + Math.round(this.stats.baseMending()) + "%",
                        mendingBonus > 0 ? l.choose("Segundo Fôlego +", "Second Wind +") + Math.round(mendingBonus) + "%" : null),
                MENDING_INFO, l));

        double weaponDamage = this.value(p, Attribute.ATTACK_DAMAGE, 1.0);
        double combatLevelBonus = this.combat.damageMultiplier(c.level()) - 1.0;
        double abilityTreeBonus = this.abilities.outgoingMultiplier(p) - 1.0;
        items.add(this.damageItem(l, weaponDamage, s.strength(), combatLevelBonus, abilityTreeBonus));

        return items;
    }

    /**
     * The actual outgoing-damage equation (see {@code CombatListener#damage}'s own doc
     * for the full breakdown this mirrors) with {@code p}'s own real numbers
     * substituted - a deliberate simplification, not the full per-hit formula: Enchants
     * is shown as "depends on target" rather than computed, since most of the enchants
     * that would feed it (Cubism/Ender Slayer/Impaling/Execute/Giant Killer) need an
     * actual target's type or health to resolve, which this screen doesn't have; no
     * critical roll, mob-type bonus, or legendary weapon's own situational Backstab/
     * Armored/Undead multiplier either, for the same reason. {@code weaponDamage} is
     * read straight from {@link Attribute#ATTACK_DAMAGE} (the real, currently-held
     * total - already includes whatever {@code SwordDamageService}/{@code
     * ToolDamageService}/{@code PolearmDamageService}/{@code LegendaryWeaponService}
     * granted the equipped weapon, so this works correctly for any of them without
     * needing its own reference to those classes).
     */
    private ItemStack damageItem(Language l, double weaponDamage, long strength, double combatLevelBonus, double abilityTreeBonus) {
        double initialDamage = (BASE_UNARMED_DAMAGE + weaponDamage) * (1.0 + (double) strength / 100.0);
        double damageMultiplier = 1.0 + combatLevelBonus + abilityTreeBonus;
        double baseline = initialDamage * damageMultiplier;
        List<Component> lore = new ArrayList<>();
        lore.add(this.text(l.choose("Dano Inicial = (5 + Dano da Arma) × (1 + Força/100)", "Initial Damage = (5 + Weapon DMG) × (1 + Strength/100)"), NamedTextColor.GOLD));
        lore.add(this.text("= (5 + " + String.format(Locale.US, "%.1f", weaponDamage) + ") × (1 + " + strength + "/100) = "
                + String.format(Locale.US, "%.2f", initialDamage), NamedTextColor.GREEN));
        lore.add(Component.empty());
        lore.add(this.text(l.choose("Multiplicador = 1 + Bônus de Nível + Encantamentos + Bônus de Habilidade", "Multiplier = 1 + Level Bonus + Enchants + Ability Bonus"), NamedTextColor.GOLD));
        lore.add(this.text("= 1 + " + String.format(Locale.US, "%.2f", combatLevelBonus) + " + "
                + l.choose("(depende do alvo)", "(depends on target)") + " + " + String.format(Locale.US, "%.2f", abilityTreeBonus)
                + " = " + String.format(Locale.US, "%.2f", damageMultiplier), NamedTextColor.GREEN));
        lore.add(Component.empty());
        lore.add(this.text(l.choose("Dano Final (base) = Dano Inicial × Multiplicador", "Final Damage (baseline) = Initial × Multiplier"), NamedTextColor.GOLD));
        lore.add(this.text("= " + String.format(Locale.US, "%.2f", initialDamage) + " × " + String.format(Locale.US, "%.2f", damageMultiplier)
                + " = " + String.format(Locale.US, "%.1f", baseline), NamedTextColor.GREEN));
        lore.add(Component.empty());
        for (String part : LoreWrap.wrapText(l.choose(
                "Crítico, bônus contra tipos de mob, vida do alvo e encantamentos de dano se somam por cima disso, dependendo do alvo.",
                "Critical hits, mob-type/target-health bonuses, and damage enchants stack on top of this depending on the target."), LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.DARK_GRAY));
        }
        return this.item(Material.NETHERITE_AXE, "⚔ " + l.choose("Dano: ", "Damage: ") + String.format(Locale.US, "%.1f", baseline), lore);
    }

    /**
     * One stat's detail item on a STAT_LIST screen ({@link #openStatList}) - the
     * stat's own name/current value as the item's display name, then three labeled
     * lore blocks: where the current number comes from ({@code source}), what the stat
     * actually does, and how to get more of it (the last two from {@code info}).
     */
    private ItemStack statItem(Material icon, String name, String source, StatInfo info, Language l) {
        boolean pt = l == Language.PT;
        List<Component> lore = new ArrayList<>();
        lore.add(this.text(l.choose("De onde vem:", "Where it comes from:"), NamedTextColor.GOLD));
        for (String part : LoreWrap.wrapText(source, LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text("  " + part, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(this.text(l.choose("O que faz:", "What it does:"), NamedTextColor.GOLD));
        for (String part : LoreWrap.wrapText(info.what(pt), LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(this.text(l.choose("Como conseguir mais:", "How to get more:"), NamedTextColor.GOLD));
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
        List<Component> lore = List.of(
                this.text(l.choose("Bônus de atributo que essa skill concede.", "Attribute bonuses this skill grants."), NamedTextColor.GRAY),
                Component.empty(),
                this.click(l));
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
            items.add(this.statItem(fortuneIcon, t.name(l == Language.PT) + " Fortune: " + this.general.fortune(p, t),
                    this.rate(l, level, this.general.fortunePerLevel()), FORTUNE_INFO, l));
        }
        switch (t) {
            case MINING -> items.add(this.statItem(Material.SHIELD, "+" + (level * this.general.defensePerLevel()) + " " + l.choose("Defesa", "Defense"),
                    this.rate(l, level, this.general.defensePerLevel()), SKILL_DEFENSE_INFO, l));
            case FARMING, FISHING -> items.add(this.statItem(Material.GOLDEN_APPLE, "+" + (level * this.general.healthPerLevel()) + " " + l.choose("Vida Máxima", "Max Health"),
                    this.rate(l, level, this.general.healthPerLevel()), SKILL_HEALTH_INFO, l));
            case FORAGING -> items.add(this.statItem(Material.DIAMOND_SWORD, "+" + (level * this.general.strengthPerLevel()) + " " + l.choose("Força", "Strength"),
                    this.rate(l, level, this.general.strengthPerLevel()), SKILL_STRENGTH_INFO, l));
            case ALCHEMY, ENCHANTING -> items.add(this.statItem(Material.LAPIS_LAZULI, "+" + (level * this.general.intelligencePerLevel()) + " " + l.choose("Inteligência", "Intelligence"),
                    this.rate(l, level, this.general.intelligencePerLevel()), SKILL_INTELLIGENCE_INFO, l));
            default -> {}
        }
        if (t == SkillType.ENCHANTING) {
            items.add(this.statItem(Material.EXPERIENCE_BOTTLE, "+" + (level * this.general.xpOrbPercentPerLevel()) + "% " + l.choose("Orbs de XP", "XP Orbs"),
                    this.rate(l, level, this.general.xpOrbPercentPerLevel()), XP_ORBS_INFO, l));
        }
        if (t == SkillType.ALCHEMY) {
            items.add(this.statItem(Material.POTION, "+" + (level * this.general.potionDurationPercentPerLevel()) + "% " + l.choose("Duração de Poções", "Potion Duration"),
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
        String title = skill == null ? l.choose("Status de Combate", "Combat Stats")
                : l.choose(skill.name(true) + " - Status", skill.name(false) + " Stats");
        Inventory v = this.inv(title);
        List<ItemStack> items = skill == null ? this.combatStatItems(target, l) : this.skillStatItems(target, skill, l);
        for (int i = 0; i < items.size() && i < STAT_SLOTS.length; i++) {
            v.setItem(STAT_SLOTS[i], items.get(i));
        }
        v.setItem(STAT_LIST_TITLE_SLOT, this.item(skill == null ? Material.IRON_SWORD : skill.icon(), title, List.of()));
        v.setItem(STAT_LIST_BACK_SLOT, this.customHead(HeadTexture.BACK, l.choose("Voltar", "Back"), List.of()));
        this.open(viewer, v, new View(Type.STAT_LIST, 0, skill, target.getUniqueId()));
    }

    /** "Level N × rate/level" - the source line every {@link #skillStatItems} entry shares. */
    private String rate(Language l, int level, int perLevel) {
        return l.choose("Nível " + level + " × " + perLevel + "/nível", "Level " + level + " × " + perLevel + "/level");
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
        MAIN, COMBAT, GENERAL, GLOBAL, STATS, STAT_LIST
    }
}
