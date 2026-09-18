package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.CombatProgress;
import dev.icaro.foodtooltips.skills.SkillProgress;
import dev.icaro.foodtooltips.skills.SkillType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SkillProgressBarService {
    private final Plugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<UUID, BossBar>();
    private final Map<UUID, Integer> generations = new HashMap<UUID, Integer>();

    public SkillProgressBarService(Plugin p) {
        this.plugin = p;
    }

    public void showCombat(Player p, double gained, CombatProgress progress, int maxLevel) {
        Language l = Language.of(p);
        BossBar bar = this.bars.computeIfAbsent(p.getUniqueId(), id -> {
            BossBar b = Bukkit.createBossBar((String)"", (BarColor)BarColor.RED, (BarStyle)BarStyle.SEGMENTED_10, (BarFlag[])new BarFlag[0]);
            b.addPlayer(p);
            return b;
        });
        if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
        }
        String state = progress.level() >= maxLevel ? "MAX" : Math.round(progress.xp()) + "/" + Math.round(progress.requiredXp()) + " XP";
        bar.setTitle(String.valueOf(ChatColor.RED) + l.choose("Combate ", "Combat ") + progress.level() + String.valueOf(ChatColor.GRAY) + " \u2022 " + String.valueOf(ChatColor.GREEN) + "+" + Math.round(gained) + " XP " + String.valueOf(ChatColor.GRAY) + "\u2022 " + state);
        bar.setProgress(progress.level() >= maxLevel ? 1.0 : Math.max(0.0, Math.min(1.0, progress.xp() / Math.max(1.0, progress.requiredXp()))));
        bar.setVisible(true);
        int generation = this.generations.merge(p.getUniqueId(), 1, Integer::sum);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (this.generations.getOrDefault(p.getUniqueId(), 0) == generation) {
                bar.setVisible(false);
            }
        }, 60L);
    }

    public void show(Player p, SkillType type, double gained, SkillProgress progress, int maxLevel) {
        Language l = Language.of(p);
        BossBar bar = this.bars.computeIfAbsent(p.getUniqueId(), id -> {
            BossBar b = Bukkit.createBossBar((String)"", (BarColor)BarColor.GREEN, (BarStyle)BarStyle.SEGMENTED_10, (BarFlag[])new BarFlag[0]);
            b.addPlayer(p);
            return b;
        });
        if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
        }
        bar.setColor(this.barColor(type));
        String state = progress.level() >= maxLevel ? "MAX" : Math.round(progress.xp()) + "/" + Math.round(progress.requiredXp()) + " XP";
        bar.setTitle(String.valueOf(ChatColor.GOLD) + type.name(l == Language.PT) + " " + progress.level() + String.valueOf(ChatColor.GRAY) + " \u2022 " + String.valueOf(ChatColor.GREEN) + "+" + Math.round(gained) + " XP " + String.valueOf(ChatColor.GRAY) + "\u2022 " + state);
        bar.setProgress(progress.level() >= maxLevel ? 1.0 : Math.max(0.0, Math.min(1.0, progress.xp() / Math.max(1.0, progress.requiredXp()))));
        this.showTemporarily(p, bar);
    }

    /** Each {@link SkillType} gets its own distinct boss bar color - Combat's own bar (see {@link #showCombat}) keeps RED for itself, so these six are every other {@link BarColor} Bukkit's boss bar API offers. */
    private BarColor barColor(SkillType type) {
        return switch (type) {
            case FARMING -> BarColor.GREEN;
            case FISHING -> BarColor.BLUE;
            case MINING -> BarColor.WHITE;
            case FORAGING -> BarColor.YELLOW;
            case ENCHANTING -> BarColor.PINK;
            case ALCHEMY -> BarColor.PURPLE;
        };
    }

    private void showTemporarily(Player p, BossBar bar) {
        bar.setVisible(true);
        int generation = this.generations.merge(p.getUniqueId(), 1, Integer::sum);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (this.generations.getOrDefault(p.getUniqueId(), 0) == generation) {
                bar.setVisible(false);
            }
        }, 60L);
    }

    public void remove(Player p) {
        BossBar b = this.bars.remove(p.getUniqueId());
        if (b != null) {
            b.removeAll();
        }
        this.generations.remove(p.getUniqueId());
    }

    public void shutdown() {
        this.bars.values().forEach(BossBar::removeAll);
        this.bars.clear();
        this.generations.clear();
    }
}

