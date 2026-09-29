package dev.icaro.foodtooltips.stats;

import dev.icaro.foodtooltips.item.StatIcons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class StatsHudService {
    private final String gap;

    public StatsHudService(String gap) {
        this.gap = gap;
    }

    public void show(Player p, PlayerStats s, int defense) {
        Component line = Component.text(StatIcons.HEALTH + " ", NamedTextColor.WHITE)
                .append(Component.text(Math.round(s.health()) + "/" + Math.round(s.maxHealth()), NamedTextColor.RED))
                .append(Component.text(this.gap))
                .append(Component.text(StatIcons.DEFENSE + " ", NamedTextColor.WHITE))
                .append(Component.text(String.valueOf(defense), NamedTextColor.GREEN))
                .append(Component.text(this.gap))
                .append(Component.text(StatIcons.MANA + " ", NamedTextColor.WHITE))
                .append(Component.text(Math.round(s.mana()) + "/" + Math.round(s.maxMana()), NamedTextColor.AQUA));
        p.sendActionBar(line);
    }
}
