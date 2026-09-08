package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalLevelSnapshot;
import dev.icaro.foodtooltips.global.LevelColorMenuService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.mining.MiningMenuService;
import dev.icaro.foodtooltips.stats.PlayerStats;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
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
    private static final Map<Integer, SkillType> S = Map.of(22, SkillType.FARMING, 24, SkillType.FISHING, 30, SkillType.MINING, 31, SkillType.FORAGING, 32, SkillType.ENCHANTING, 33, SkillType.ALCHEMY);

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
            v.setItem(47, this.item(Material.NAME_TAG, l.choose("Cores do Nível", "Level Colors"), List.of(this.click(l))));
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
            if (t == SkillType.MINING && level == 3) {
                lore.add(this.text("✦ " + l.choose("Desbloqueia: Vein Miner", "Unlocks: Vein Miner"), NamedTextColor.LIGHT_PURPLE));
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
                    this.openStats(p);
                } else if (slot == 13) {
                    this.openGlobal(p, 0);
                } else if (slot == 20) {
                    this.openCombat(p, 0);
                } else if (S.containsKey(slot)) {
                    this.openGeneral(p, S.get(slot), 0);
                } else if (slot == 47 && this.levelColors != null) {
                    this.views.remove(p.getUniqueId());
                    this.levelColors.open(p);
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
        v.setItem(45, this.item(Material.BARRIER, l.choose("Voltar às skills", "Back to skills"), List.of()));
        if (page > 0) {
            v.setItem(48, this.item(Material.ARROW, l.choose("Página anterior", "Previous page"), List.of()));
        }
        if ((page + 1) * 25 < max) {
            v.setItem(50, this.item(Material.ARROW, l.choose("Próxima página", "Next page"), List.of()));
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

    /** Full breakdown (grouped by icon) plus the player's equipped armor. Opened by clicking {@link #head}. */
    public void openStats(Player p) {
        Language l = Language.of(p);
        Inventory v = this.inv(l.choose("Status & Equipamento", "Stats & Equipment"));
        v.setItem(4, this.statsOverviewHead(p, l));
        v.setItem(20, this.armorSlot(p.getInventory().getHelmet(), l.choose("Capacete", "Helmet"), l));
        v.setItem(29, this.armorSlot(p.getInventory().getChestplate(), l.choose("Peitoral", "Chestplate"), l));
        v.setItem(38, this.armorSlot(p.getInventory().getLeggings(), l.choose("Calças", "Leggings"), l));
        v.setItem(47, this.armorSlot(p.getInventory().getBoots(), l.choose("Botas", "Boots"), l));
        v.setItem(24, this.combatStatsItem(p, l));
        v.setItem(39, this.skillBonusItem(p, SkillType.MINING, l));
        v.setItem(40, this.skillBonusItem(p, SkillType.FARMING, l));
        v.setItem(41, this.skillBonusItem(p, SkillType.FISHING, l));
        v.setItem(42, this.skillBonusItem(p, SkillType.FORAGING, l));
        v.setItem(43, this.skillBonusItem(p, SkillType.ALCHEMY, l));
        v.setItem(44, this.skillBonusItem(p, SkillType.ENCHANTING, l));
        v.setItem(45, this.item(Material.BARRIER, l.choose("Voltar às skills", "Back to skills"), List.of()));
        this.open(p, v, new View(Type.STATS, 0, null));
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

    /**
     * The full Combat Stats list in one place — every combat stat the player has, in the
     * order Health/Defense/True Defense/Strength/Crit Chance/Crit Damage/Ferocity/Swing
     * Range/Intelligence/Agility/Speed/Ability Damage/Health Regen/Vitality/Mending, each
     * followed by a gray sub-line naming exactly where its number comes from. Everything
     * here except True Defense is upgradeable through the combat tree and/or a general
     * skill (see {@link CombatAbilityService}'s class doc for which ability grants which
     * bonus, and {@link GeneralSkillService} for Mining/Farming/Fishing/Foraging/Alchemy/
     * Enchanting). Agility/Speed is the odd one out - its only source today is a
     * legendary weapon (Baruka's Dagger), not the tree or a general skill.
     */
    private ItemStack combatStatsItem(Player p, Language l) {
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

        List<Component> lore = new ArrayList<>(List.of(
                this.text(l.choose("Status que influenciam quanto dano você recebe e causa em combate.", "Stats that influence how much damage you take and deal in combat."), NamedTextColor.GRAY),
                Component.empty()));

        this.stat(lore, "❤ " + l.choose("Vida: ", "Health: ") + Math.round(s.health()) + "/" + Math.round(s.maxHealth()), NamedTextColor.RED,
                this.join(l.choose("Base ", "Base ") + Math.round(baseHealth),
                        bestiaryHealth > 0 ? l.choose("Bestiário +", "Bestiary +") + Math.round(bestiaryHealth) : null,
                        globalHealth > 0 ? l.choose("Nível Global +", "Global Level +") + Math.round(globalHealth) : null,
                        skillHealth > 0 ? l.choose("Agricultura/Pesca +", "Farming/Fishing +") + Math.round(skillHealth) : null));

        this.stat(lore, "✦ " + l.choose("Defesa: ", "Defense: ") + defense, NamedTextColor.GREEN,
                this.join(null,
                        helmetDef > 0 ? l.choose("Elmo +", "Helmet +") + helmetDef : null,
                        chestDef > 0 ? l.choose("Peitoral +", "Chestplate +") + chestDef : null,
                        legsDef > 0 ? l.choose("Calças +", "Leggings +") + legsDef : null,
                        bootsDef > 0 ? l.choose("Botas +", "Boots +") + bootsDef : null,
                        miningDef > 0 ? l.choose("Mineração +", "Mining +") + miningDef : null,
                        defense == 0 ? l.choose("Nenhuma fonte", "No source") : null));

        this.stat(lore, "🛡 " + l.choose("Defesa Verdadeira: ", "True Defense: ") + String.format(Locale.US, "%.0f", s.trueDefense()), NamedTextColor.GRAY,
                l.choose("Base (config)", "Base (config)"));

        this.stat(lore, "✹ Strength: " + s.strength(), NamedTextColor.RED,
                this.join(l.choose("Nível Global +", "Global Level +") + globalStrength,
                        foragingStrength > 0 ? l.choose("Coleta +", "Foraging +") + foragingStrength : null));

        this.stat(lore, "☣ " + l.choose("Chance Crítica: ", "Crit Chance: ") + String.format(Locale.US, "%.1f", critChance) + "%", NamedTextColor.AQUA,
                this.join(l.choose("Nível de Combate +", "Combat Level +") + String.format(Locale.US, "%.1f", combatCritChance) + "%",
                        critChanceBonus > 0 ? l.choose("Golpes Implacáveis +", "Ruthless Strikes +") + String.format(Locale.US, "%.1f", critChanceBonus) + "%" : null));

        this.stat(lore, "☠ " + l.choose("Dano Crítico: ", "Crit Damage: ") + String.format(Locale.US, "%.1f", critDamage) + "%", NamedTextColor.AQUA,
                criticalMastery ? l.choose("Maestria Crítica (rank ", "Critical Mastery (rank ") + this.abilities.rank(p, CombatAbility.CRITICAL_MASTERY) + ")"
                        : l.choose("Base (config) - Maestria Crítica não desbloqueada", "Base (config) - Critical Mastery not unlocked"));

        this.stat(lore, "Ⓕ Ferocity: " + Math.round(s.ferocity()), NamedTextColor.RED,
                l.choose("Base (config)", "Base (config)"));

        this.stat(lore, "⚔ " + l.choose("Velocidade de Ataque: ", "Attack Speed: ") + String.format(Locale.US, "%.1f", this.value(p, Attribute.ATTACK_SPEED, 4.0)), NamedTextColor.GOLD,
                l.choose("Nível de Combate " + c.level(), "Combat Level " + c.level()));

        double swingRangeBonus = this.abilities.swingRangeBonus(p);
        this.stat(lore, "↔ " + l.choose("Alcance de Ataque: ", "Swing Range: ") + String.format(Locale.US, "%.1f", s.swingRange()), NamedTextColor.YELLOW,
                this.join(l.choose("Base ", "Base ") + String.format(Locale.US, "%.1f", this.stats.baseSwingRange()),
                        swingRangeBonus > 0 ? l.choose("Arremesso de Espada +", "Sword Throw +") + String.format(Locale.US, "%.1f", swingRangeBonus) : null));

        long alchemyEnchantingIntelligence = this.general.bonusIntelligence(p);
        this.stat(lore, "✎ " + l.choose("Inteligência: ", "Intelligence: ") + Math.round(s.intelligence()), NamedTextColor.AQUA,
                this.join(l.choose("Base ", "Base ") + Math.round(this.stats.baseIntelligence()),
                        alchemyEnchantingIntelligence > 0 ? l.choose("Alquimia/Encantamento +", "Alchemy/Enchanting +") + alchemyEnchantingIntelligence : null));

        // Agility/Speed is the same pairing as Intelligence/Mana above - a plain stat
        // (base plus whatever's currently wielded) that feeds a resource one-for-one,
        // just expressed as a percentage since Speed is a real vanilla attribute rather
        // than a fully custom one - see PlayerStatsService#effectiveAgility.
        double agility = this.stats.effectiveAgility(p);
        double heldAgility = agility - this.stats.baseAgility();
        this.stat(lore, "🐇 " + l.choose("Agilidade: ", "Agility: ") + Math.round(agility), NamedTextColor.WHITE,
                this.join(l.choose("Base ", "Base ") + Math.round(this.stats.baseAgility()),
                        heldAgility > 0 ? l.choose("Arma equipada +", "Held weapon +") + Math.round(heldAgility) : null));

        long speedPercent = Math.round(this.value(p, Attribute.MOVEMENT_SPEED, 0.1) / 0.1 * 100.0);
        this.stat(lore, "🏃 " + l.choose("Velocidade: ", "Speed: ") + speedPercent + "%", NamedTextColor.WHITE,
                this.join(l.choose("Base 100%", "Base 100%"),
                        agility > 0 ? l.choose("Agilidade +", "Agility +") + Math.round(agility) + "%" : null));

        this.stat(lore, "❉ " + l.choose("Dano de Habilidade: ", "Ability Damage: ") + Math.round(s.abilityDamage()) + "%", NamedTextColor.LIGHT_PURPLE,
                l.choose("Base (config)", "Base (config)"));

        double healthRegenBonus = this.abilities.healthRegenBonus(p);
        this.stat(lore, "❣ " + l.choose("Regen. de Vida: ", "Health Regen: ") + Math.round(s.healthRegen()) + "%", NamedTextColor.RED,
                this.join(l.choose("Base ", "Base ") + Math.round(this.stats.baseHealthRegen()) + "%",
                        healthRegenBonus > 0 ? l.choose("Colheita de Almas +", "Soul Harvest +") + Math.round(healthRegenBonus) + "%" : null));

        this.stat(lore, "✿ Vitality: " + Math.round(s.vitality()) + "/" + Math.round(s.maxVitality()), NamedTextColor.LIGHT_PURPLE,
                l.choose("Base (config)", "Base (config)"));

        double mendingBonus = this.abilities.mendingBonus(p);
        this.stat(lore, "❋ " + l.choose("Cura (Mending): ", "Mending: ") + Math.round(s.mending()) + "%", NamedTextColor.GREEN,
                this.join(l.choose("Base ", "Base ") + Math.round(this.stats.baseMending()) + "%",
                        mendingBonus > 0 ? l.choose("Segundo Fôlego +", "Second Wind +") + Math.round(mendingBonus) + "%" : null));

        return this.item(Material.IRON_SWORD, l.choose("Status de Combate", "Combat Stats"), lore);
    }

    /** Adds a stat line plus its gray "where this comes from" sub-line right under it. */
    private void stat(List<Component> lore, String line, NamedTextColor color, String source) {
        lore.add(this.text(line, color));
        lore.add(this.text("  " + source, NamedTextColor.DARK_GRAY));
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
     * One tile per general skill (Mining/Farming/Fishing/Foraging/Alchemy/Enchanting) —
     * every attribute bonus that skill grants (see {@link GeneralSkillService}), each
     * with the same "stat line + gray source line" treatment {@link #combatStatsItem}
     * uses, so every skill's contribution is visible somewhere in Stats & Equipment,
     * not just the three that happen to grant Fortune.
     */
    private ItemStack skillBonusItem(Player p, SkillType t, Language l) {
        int level = this.general.progress(p, t).level();
        List<Component> lore = new ArrayList<>();
        if (t == SkillType.MINING || t == SkillType.FARMING || t == SkillType.FORAGING) {
            this.stat(lore, t.name(l == Language.PT) + " Fortune: " + this.general.fortune(p, t), NamedTextColor.AQUA,
                    this.rate(l, level, this.general.fortunePerLevel()));
        }
        switch (t) {
            case MINING -> this.stat(lore, "+" + (level * this.general.defensePerLevel()) + " " + l.choose("Defesa", "Defense"), NamedTextColor.GREEN,
                    this.rate(l, level, this.general.defensePerLevel()));
            case FARMING, FISHING -> this.stat(lore, "+" + (level * this.general.healthPerLevel()) + " " + l.choose("Vida Máxima", "Max Health"), NamedTextColor.RED,
                    this.rate(l, level, this.general.healthPerLevel()));
            case FORAGING -> this.stat(lore, "+" + (level * this.general.strengthPerLevel()) + " " + l.choose("Força", "Strength"), NamedTextColor.YELLOW,
                    this.rate(l, level, this.general.strengthPerLevel()));
            case ALCHEMY, ENCHANTING -> this.stat(lore, "+" + (level * this.general.intelligencePerLevel()) + " " + l.choose("Inteligência", "Intelligence"), NamedTextColor.LIGHT_PURPLE,
                    this.rate(l, level, this.general.intelligencePerLevel()));
            default -> {}
        }
        return this.item(t.icon(), t.name(l == Language.PT), lore);
    }

    /** "Level N × rate/level" - the source line every {@link #skillBonusItem} entry shares. */
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

    private record View(Type type, int page, SkillType skill) {
    }

    private enum Type {
        MAIN, COMBAT, GENERAL, GLOBAL, STATS
    }
}
