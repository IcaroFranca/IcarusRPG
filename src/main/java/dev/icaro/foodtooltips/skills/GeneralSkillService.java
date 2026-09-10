package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.mining.MiningCatalog;
import dev.icaro.foodtooltips.mining.MiningEntry;
import dev.icaro.foodtooltips.skills.SkillProgress;
import dev.icaro.foodtooltips.skills.SkillType;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class GeneralSkillService {
    private static final int MAX_LEVEL = 200;
    private static final int[] MINING_MILESTONE_THRESHOLDS = {25, 100, 250, 500, 1000};
    private static final int FORTUNE_PER_LEVEL = 4;
    private static final int HEALTH_PER_LEVEL = 2;
    private static final int STRENGTH_PER_LEVEL = 1;
    private static final int INTELLIGENCE_PER_LEVEL = 1;
    private static final int DEFENSE_PER_LEVEL = 1;
    private static final int XP_ORB_PERCENT_PER_LEVEL = 5;
    private static final int POTION_DURATION_PERCENT_PER_LEVEL = 1;
    private final NamespacedKey healthKey = new NamespacedKey("foodtooltips", "general_skill_health");

    public SkillProgress progress(Player p, SkillType type) {
        int level = (Integer)p.getPersistentDataContainer().getOrDefault(this.key(type, "level"), PersistentDataType.INTEGER, 0);
        double xp = (Double)p.getPersistentDataContainer().getOrDefault(this.key(type, "xp"), PersistentDataType.DOUBLE, 0.0);
        return new SkillProgress(level, xp, level >= 200 ? 0.0 : this.required(level + 1));
    }

    public int addXp(Player p, SkillType type, double amount) {
        double xp;
        double needed;
        SkillProgress before = this.progress(p, type);
        if (before.level() >= 200 || amount <= 0.0) {
            return 0;
        }
        int level = before.level();
        for (xp = before.xp() + amount; level < 200 && !(xp < (needed = this.required(level + 1))); xp -= needed, ++level) {
        }
        if (level >= 200) {
            xp = 0.0;
        }
        p.getPersistentDataContainer().set(this.key(type, "level"), PersistentDataType.INTEGER, level);
        p.getPersistentDataContainer().set(this.key(type, "xp"), PersistentDataType.DOUBLE, xp);
        if (level != before.level() && (type == SkillType.FARMING || type == SkillType.FISHING)) {
            this.applyBonusHealth(p);
        }
        return level - before.level();
    }

    public void setLevel(Player p, SkillType type, int level) {
        p.getPersistentDataContainer().set(this.key(type, "level"), PersistentDataType.INTEGER, Math.max(0, Math.min(200, level)));
        p.getPersistentDataContainer().set(this.key(type, "xp"), PersistentDataType.DOUBLE, 0.0);
        if (type == SkillType.FARMING || type == SkillType.FISHING) {
            this.applyBonusHealth(p);
        }
    }

    /** The shared per-level XP curve every skill uses - see {@link SkillXpCurve}. */
    public double required(int level) {
        return SkillXpCurve.required(level);
    }

    public int maxLevel() {
        return 200;
    }

    public int fortune(Player player, SkillType type) {
        return switch (type) {
            case SkillType.MINING, SkillType.FARMING, SkillType.FORAGING -> this.progress(player, type).level() * FORTUNE_PER_LEVEL;
            default -> 0;
        };
    }

    /** Farming and Fishing each grant {@value #HEALTH_PER_LEVEL} Max Health per level, on top of Farming's Fortune. */
    public int bonusHealth(Player player) {
        return (this.progress(player, SkillType.FARMING).level() + this.progress(player, SkillType.FISHING).level()) * HEALTH_PER_LEVEL;
    }

    /** Foraging grants {@value #STRENGTH_PER_LEVEL} Strength per level, on top of its own Fortune. */
    public int bonusStrength(Player player) {
        return this.progress(player, SkillType.FORAGING).level() * STRENGTH_PER_LEVEL;
    }

    /** Alchemy and Enchanting each grant {@value #INTELLIGENCE_PER_LEVEL} Intelligence per level (which in turn raises Max Mana - see {@code PlayerStatsService#effectiveMaxMana}). */
    public int bonusIntelligence(Player player) {
        return (this.progress(player, SkillType.ALCHEMY).level() + this.progress(player, SkillType.ENCHANTING).level()) * INTELLIGENCE_PER_LEVEL;
    }

    /** Mining grants {@value #DEFENSE_PER_LEVEL} Defense per level, on top of its own Fortune - see {@code ArmorDefenseService#defense}. */
    public int bonusDefense(Player player) {
        return this.progress(player, SkillType.MINING).level() * DEFENSE_PER_LEVEL;
    }

    /** Enchanting grants {@value #XP_ORB_PERCENT_PER_LEVEL}% more vanilla XP orbs (any source) per level, on top of its own Intelligence - see {@code GeneralSkillListener#xpOrb}. */
    public double xpOrbMultiplier(Player player) {
        return 1.0 + 0.01 * XP_ORB_PERCENT_PER_LEVEL * this.progress(player, SkillType.ENCHANTING).level();
    }

    /** Alchemy grants {@value #POTION_DURATION_PERCENT_PER_LEVEL}% longer potion effects per level, on top of its own Intelligence - see {@code GeneralSkillListener#potionDuration}. */
    public double potionDurationMultiplier(Player player) {
        return 1.0 + 0.01 * POTION_DURATION_PERCENT_PER_LEVEL * this.progress(player, SkillType.ALCHEMY).level();
    }

    /** How much {@link #fortune} grows per level (Mining/Farming/Foraging). Exposed so menu/level-up messages don't hardcode the number separately. */
    public int fortunePerLevel() {
        return FORTUNE_PER_LEVEL;
    }

    /** How much {@link #bonusDefense} grows per Mining level. */
    public int defensePerLevel() {
        return DEFENSE_PER_LEVEL;
    }

    /** How much {@link #bonusHealth} grows per level (Farming/Fishing), per contributing skill. */
    public int healthPerLevel() {
        return HEALTH_PER_LEVEL;
    }

    /** How much {@link #bonusStrength} grows per Foraging level. */
    public int strengthPerLevel() {
        return STRENGTH_PER_LEVEL;
    }

    /** How much {@link #bonusIntelligence} grows per level (Alchemy/Enchanting), per contributing skill. */
    public int intelligencePerLevel() {
        return INTELLIGENCE_PER_LEVEL;
    }

    /** How much {@link #xpOrbMultiplier} grows per Enchanting level. */
    public int xpOrbPercentPerLevel() {
        return XP_ORB_PERCENT_PER_LEVEL;
    }

    /** How much {@link #potionDurationMultiplier} grows per Alchemy level. */
    public int potionDurationPercentPerLevel() {
        return POTION_DURATION_PERCENT_PER_LEVEL;
    }

    /**
     * Applies {@link #bonusHealth} as a Max Health attribute modifier - same pattern as
     * {@code BestiaryProgressService#applyBonusHealth} and {@code GlobalLevelService#applyHealth}.
     * Called alongside those two everywhere Max Health gets re-derived (join, world
     * change, /resetstats).
     */
    public void applyBonusHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        AttributeModifier old = attribute.getModifier(Key.key(this.healthKey.getNamespace(), this.healthKey.getKey()));
        if (old != null) {
            attribute.removeModifier(old);
        }
        int bonus = this.bonusHealth(player);
        if (bonus > 0) {
            attribute.addTransientModifier(new AttributeModifier(this.healthKey, bonus, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    public int miningSpeed(Player player, Material tool) {
        return this.baseMiningSpeed(tool) + this.progress(player, SkillType.MINING).level();
    }

    public int baseMiningSpeed(Material tool) {
        return switch (tool) {
            case Material.WOODEN_PICKAXE -> 70;
            case Material.STONE_PICKAXE -> 100;
            case Material.IRON_PICKAXE -> 130;
            case Material.DIAMOND_PICKAXE -> 160;
            case Material.NETHERITE_PICKAXE -> 180;
            case Material.GOLDEN_PICKAXE -> 250;
            default -> tool.name().endsWith("_PICKAXE") ? 100 : 0;
        };
    }

    public MiningRecord recordMined(Player p, Material block) {
        int before = this.miningMilestones(p, block);
        NamespacedKey k = this.minedKey(block);
        int count = (Integer)p.getPersistentDataContainer().getOrDefault(k, PersistentDataType.INTEGER, 0) + 1;
        p.getPersistentDataContainer().set(k, PersistentDataType.INTEGER, count);
        MiningCatalog.find(block).ifPresent(e -> {
            if (ThreadLocalRandom.current().nextInt(100) < 20) {
                this.depositMineralDust(p, Math.max(1L, Math.round(e.skillXp() / 20.0)));
            }
        });
        int after = this.miningMilestones(p, block);
        if (after > before) {
            this.depositMineralDust(p, (long)after * 50L);
        }
        return new MiningRecord(count, this.recordCommission(p, block));
    }

    public long mineralDust(Player p) {
        return (Long)p.getPersistentDataContainer().getOrDefault(new NamespacedKey("foodtooltips", "mineral_dust"), PersistentDataType.LONG, 0L);
    }

    public void depositMineralDust(Player p, long amount) {
        if (amount > 0L) {
            p.getPersistentDataContainer().set(new NamespacedKey("foodtooltips", "mineral_dust"), PersistentDataType.LONG, Math.max(0L, this.mineralDust(p) + amount));
        }
    }

    public int mined(Player p, Material block) {
        return (Integer)p.getPersistentDataContainer().getOrDefault(this.minedKey(block), PersistentDataType.INTEGER, 0);
    }

    public int miningMilestones(Player p, Material block) {
        int count = this.mined(p, block);
        int done = 0;
        for (int n : MINING_MILESTONE_THRESHOLDS) {
            if (count < n) continue;
            ++done;
        }
        return done;
    }

    /** How many milestone tiers a single ore/block type can reach (used to size Global Level's max-achievable-level estimate). */
    public static int maxMiningMilestonesPerBlock() {
        return MINING_MILESTONE_THRESHOLDS.length;
    }

    public double carefulChance(Player p) {
        return Math.min(25.0, (double)this.progress(p, SkillType.MINING).level() * 0.05 + (double)this.totalMiningMilestones(p) * 0.5);
    }

    public int totalMiningMilestones(Player p) {
        int n = 0;
        for (MiningEntry e : MiningCatalog.entries()) {
            n += this.miningMilestones(p, e.block());
        }
        return n;
    }

    public Material commissionTarget(Player p) {
        List<MiningEntry> list = MiningCatalog.entries();
        int index = Math.floorMod(LocalDate.now().toEpochDay() + (long)p.getUniqueId().hashCode(), list.size());
        return list.get(index).block();
    }

    public int commissionProgress(Player p) {
        PersistentDataContainer d;
        String day = LocalDate.now().toString();
        if (!day.equals((d = p.getPersistentDataContainer()).get(this.key(SkillType.MINING, "commission_day"), PersistentDataType.STRING))) {
            return 0;
        }
        return (Integer)d.getOrDefault(this.key(SkillType.MINING, "commission_count"), PersistentDataType.INTEGER, 0);
    }

    public int commissionGoal(Player p) {
        return 100;
    }

    private boolean recordCommission(Player p, Material block) {
        int before;
        if (block != this.commissionTarget(p)) {
            return false;
        }
        PersistentDataContainer d = p.getPersistentDataContainer();
        NamespacedKey dayKey = this.key(SkillType.MINING, "commission_day");
        NamespacedKey countKey = this.key(SkillType.MINING, "commission_count");
        String day = LocalDate.now().toString();
        if (!day.equals(d.get(dayKey, PersistentDataType.STRING))) {
            d.set(dayKey, PersistentDataType.STRING, day);
            d.set(countKey, PersistentDataType.INTEGER, 0);
        }
        if ((before = ((Integer)d.getOrDefault(countKey, PersistentDataType.INTEGER, 0)).intValue()) >= this.commissionGoal(p)) {
            return false;
        }
        int after = before + 1;
        d.set(countKey, PersistentDataType.INTEGER, after);
        if (after == this.commissionGoal(p)) {
            this.depositMineralDust(p, 250L);
            return true;
        }
        return false;
    }

    private NamespacedKey minedKey(Material block) {
        return new NamespacedKey("foodtooltips", "mined_" + block.name().toLowerCase(Locale.ROOT));
    }

    private NamespacedKey key(SkillType type, String field) {
        return new NamespacedKey("foodtooltips", type.name().toLowerCase(Locale.ROOT) + "_" + field);
    }

    public record MiningRecord(int count, boolean commissionCompleted) {
    }
}

